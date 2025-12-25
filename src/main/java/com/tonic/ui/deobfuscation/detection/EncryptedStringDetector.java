package com.tonic.ui.deobfuscation.detection;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.deobfuscation.model.SuspiciousString;
import com.tonic.ui.deobfuscation.model.SuspiciousString.SuspicionReason;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EncryptedStringDetector {

    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]{4,}={0,2}$");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-Fa-f]{8,}$");

    private double entropyThreshold = 4.0;
    private int minLength = 4;
    private int maxLength = 1000;
    private boolean detectBase64 = true;
    private boolean detectHighEntropy = true;
    private boolean detectNonPrintable = true;
    private boolean detectHex = true;

    public List<SuspiciousString> scan(ClassFile classFile) {
        List<SuspiciousString> suspicious = new ArrayList<>();
        ConstPool cp = classFile.getConstPool();
        List<Item<?>> items = cp.getItems();

        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);

            if (item instanceof StringRefItem) {
                StringRefItem stringRef = (StringRefItem) item;
                int utf8Index = stringRef.getValue();
                if (utf8Index > 0 && utf8Index < items.size()) {
                    Item<?> utf8Item = items.get(utf8Index);

                    if (utf8Item instanceof Utf8Item) {
                        String value = ((Utf8Item) utf8Item).getValue();
                        SuspiciousString result = analyzeString(classFile, i, value);
                        if (result != null) {
                            suspicious.add(result);
                        }
                    }
                }
            }
        }

        suspicious.sort((a, b) -> Double.compare(b.getSuspicionScore(), a.getSuspicionScore()));
        return suspicious;
    }

    private SuspiciousString analyzeString(ClassFile classFile, int cpIndex, String value) {
        if (value == null || value.length() < minLength || value.length() > maxLength) {
            return null;
        }

        if (isCommonString(value)) {
            return null;
        }

        if (detectBase64 && isBase64Encoded(value)) {
            double score = 0.8 + (value.length() > 20 ? 0.1 : 0);
            return new SuspiciousString(classFile, cpIndex, value, SuspicionReason.BASE64_PATTERN, score);
        }

        if (detectHex && isHexEncoded(value)) {
            double score = 0.7 + (value.length() > 16 ? 0.1 : 0);
            return new SuspiciousString(classFile, cpIndex, value, SuspicionReason.HEX_PATTERN, score);
        }

        if (detectNonPrintable && hasNonPrintable(value)) {
            return new SuspiciousString(classFile, cpIndex, value, SuspicionReason.NON_PRINTABLE, 0.9);
        }

        if (detectHighEntropy) {
            double entropy = calculateEntropy(value);
            if (entropy > entropyThreshold) {
                double score = Math.min(1.0, entropy / 6.0);
                return new SuspiciousString(classFile, cpIndex, value, SuspicionReason.HIGH_ENTROPY, score);
            }
        }

        return null;
    }

    private boolean isBase64Encoded(String value) {
        if (value.length() < 8) return false;
        if (value.length() % 4 != 0 && !value.endsWith("=") && !value.endsWith("==")) {
            return false;
        }
        return BASE64_PATTERN.matcher(value).matches();
    }

    private boolean isHexEncoded(String value) {
        if (value.length() < 8 || value.length() % 2 != 0) return false;
        return HEX_PATTERN.matcher(value).matches();
    }

    private boolean hasNonPrintable(String value) {
        for (char c : value.toCharArray()) {
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
            if (c > 126 && c < 160) {
                return true;
            }
        }
        return false;
    }

    private double calculateEntropy(String value) {
        if (value == null || value.isEmpty()) return 0;

        int[] freq = new int[256];
        for (char c : value.toCharArray()) {
            if (c < 256) {
                freq[c]++;
            }
        }

        double entropy = 0;
        int len = value.length();
        for (int f : freq) {
            if (f > 0) {
                double p = (double) f / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }

        return entropy;
    }

    private boolean isCommonString(String value) {
        if (value.startsWith("java/") || value.startsWith("javax/")) return true;
        if (value.startsWith("sun/") || value.startsWith("com/sun/")) return true;
        if (value.startsWith("org/") && !value.contains("obfusc")) return true;
        if (value.contains(".class") || value.contains(".java")) return true;
        if (value.matches("^[ZBCSIJFDV\\[L;()]+$")) return true;
        if (value.matches("^[a-z]+$") && value.length() < 20) return true;
        if (value.matches("^[A-Z_]+$")) return true;
        if (value.startsWith("<") && value.endsWith(">")) return true;
        if (value.equals("Code") || value.equals("LineNumberTable") ||
            value.equals("LocalVariableTable") || value.equals("StackMapTable") ||
            value.equals("SourceFile") || value.equals("InnerClasses") ||
            value.equals("Exceptions") || value.equals("Signature")) return true;

        return false;
    }

    public void setEntropyThreshold(double threshold) {
        this.entropyThreshold = threshold;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setDetectBase64(boolean detect) {
        this.detectBase64 = detect;
    }

    public void setDetectHighEntropy(boolean detect) {
        this.detectHighEntropy = detect;
    }

    public void setDetectNonPrintable(boolean detect) {
        this.detectNonPrintable = detect;
    }

    public void setDetectHex(boolean detect) {
        this.detectHex = detect;
    }
}
