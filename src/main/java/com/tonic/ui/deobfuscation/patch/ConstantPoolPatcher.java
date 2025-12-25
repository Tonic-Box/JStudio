package com.tonic.ui.deobfuscation.patch;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.deobfuscation.model.DeobfuscationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantPoolPatcher {

    public void patchString(ClassFile classFile, int cpIndex, String newValue) {
        ConstPool cp = classFile.getConstPool();
        List<Item<?>> items = cp.getItems();

        if (cpIndex < 0 || cpIndex >= items.size()) {
            throw new IllegalArgumentException("CP index " + cpIndex + " is out of range");
        }

        Item<?> item = items.get(cpIndex);
        if (!(item instanceof StringRefItem)) {
            throw new IllegalArgumentException("CP index " + cpIndex + " is not a StringRef");
        }

        StringRefItem stringRef = (StringRefItem) item;
        int utf8Index = stringRef.getValue();

        if (utf8Index < 0 || utf8Index >= items.size()) {
            throw new IllegalArgumentException("StringRef points to invalid UTF8 index");
        }

        Item<?> utf8Item = items.get(utf8Index);
        if (!(utf8Item instanceof Utf8Item)) {
            throw new IllegalArgumentException("StringRef points to non-UTF8 item");
        }

        Utf8Item utf8 = (Utf8Item) utf8Item;
        utf8.setValue(newValue);
    }

    public int applyResults(ClassFile classFile, List<DeobfuscationResult> results) {
        int applied = 0;

        for (DeobfuscationResult result : results) {
            if (!result.isSuccess() || result.isApplied()) {
                continue;
            }

            if (!result.getClassName().equals(classFile.getClassName())) {
                continue;
            }

            try {
                patchString(classFile, result.getConstantPoolIndex(), result.getDecryptedValue());
                result.setApplied(true);
                applied++;
            } catch (Exception e) {
                System.out.println("[ConstantPoolPatcher] Failed to apply patch at CP#" +
                    result.getConstantPoolIndex() + ": " + e.getMessage());
            }
        }

        return applied;
    }

    public Map<String, String> getStringMapping(ClassFile classFile) {
        Map<String, String> mapping = new HashMap<>();
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
                        mapping.put(String.valueOf(i), value);
                    }
                }
            }
        }

        return mapping;
    }

    public List<StringLocation> findStringLocations(ClassFile classFile, String value) {
        List<StringLocation> locations = new ArrayList<>();
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
                        String currentValue = ((Utf8Item) utf8Item).getValue();
                        if (value.equals(currentValue)) {
                            locations.add(new StringLocation(classFile, i, utf8Index, currentValue));
                        }
                    }
                }
            }
        }

        return locations;
    }

    public static class StringLocation {
        private final ClassFile classFile;
        private final int stringRefIndex;
        private final int utf8Index;
        private final String value;

        public StringLocation(ClassFile classFile, int stringRefIndex, int utf8Index, String value) {
            this.classFile = classFile;
            this.stringRefIndex = stringRefIndex;
            this.utf8Index = utf8Index;
            this.value = value;
        }

        public ClassFile getClassFile() {
            return classFile;
        }

        public int getStringRefIndex() {
            return stringRefIndex;
        }

        public int getUtf8Index() {
            return utf8Index;
        }

        public String getValue() {
            return value;
        }
    }
}
