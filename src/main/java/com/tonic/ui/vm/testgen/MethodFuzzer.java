package com.tonic.ui.vm.testgen;

import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.model.ExecutionResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MethodFuzzer {

    public static class FuzzResult {
        private final Object[] inputs;
        private final ExecutionResult result;
        private final String outcomeKey;

        public FuzzResult(Object[] inputs, ExecutionResult result) {
            this.inputs = inputs.clone();
            this.result = result;
            this.outcomeKey = computeOutcomeKey(result);
        }

        private String computeOutcomeKey(ExecutionResult result) {
            if (result.getException() != null) {
                String excMsg = result.getException().getMessage();
                if (excMsg != null && excMsg.contains(":")) {
                    return "EXCEPTION:" + excMsg.substring(0, Math.min(excMsg.indexOf(':') + 20, excMsg.length()));
                }
                return "EXCEPTION:" + result.getException().getClass().getSimpleName();
            }
            if (result.getReturnValue() == null) {
                return "NULL";
            }
            Object rv = result.getReturnValue();
            if (rv instanceof Number) {
                return "RETURN:" + categorizeNumber((Number) rv);
            }
            if (rv instanceof Boolean) {
                return "RETURN:" + rv;
            }
            if (rv instanceof String) {
                String s = (String) rv;
                if (s.isEmpty()) return "RETURN:EMPTY_STRING";
                if (s.length() < 10) return "RETURN:SHORT_STRING";
                return "RETURN:STRING_LEN_" + (s.length() / 10) * 10;
            }
            return "RETURN:" + rv.getClass().getSimpleName();
        }

        private String categorizeNumber(Number n) {
            long v = n.longValue();
            if (v == 0) return "ZERO";
            if (v == 1) return "ONE";
            if (v == -1) return "NEG_ONE";
            if (v < 0) return "NEGATIVE";
            if (v > 1000000) return "LARGE";
            return "POSITIVE";
        }

        public Object[] getInputs() {
            return inputs.clone();
        }

        public ExecutionResult getResult() {
            return result;
        }

        public String getOutcomeKey() {
            return outcomeKey;
        }

        public String getOutcomeDescription() {
            if (result.getException() != null) {
                return "Throws " + extractExceptionName(result.getException().getMessage());
            }
            if (result.getReturnValue() == null) {
                return "Returns null";
            }
            Object rv = result.getReturnValue();
            if (rv instanceof String) {
                String s = (String) rv;
                if (s.length() > 30) {
                    return "Returns \"" + s.substring(0, 27) + "...\"";
                }
                return "Returns \"" + s + "\"";
            }
            return "Returns " + rv;
        }

        private String extractExceptionName(String msg) {
            if (msg == null) return "Exception";
            if (msg.contains("VM Exception:")) {
                String part = msg.substring(msg.indexOf(':') + 1).trim();
                int space = part.indexOf(' ');
                if (space > 0) {
                    String name = part.substring(0, space);
                    if (name.contains("/")) {
                        name = name.substring(name.lastIndexOf('/') + 1);
                    }
                    return name;
                }
            }
            return "Exception";
        }
    }

    public static class FuzzConfig {
        private int iterationsPerType = 5;
        private boolean includeEdgeCases = true;
        private boolean includeNulls = true;
        private boolean includeRandom = true;

        public int getIterationsPerType() { return iterationsPerType; }
        public void setIterationsPerType(int n) { this.iterationsPerType = n; }
        public boolean isIncludeEdgeCases() { return includeEdgeCases; }
        public void setIncludeEdgeCases(boolean v) { this.includeEdgeCases = v; }
        public boolean isIncludeNulls() { return includeNulls; }
        public void setIncludeNulls(boolean v) { this.includeNulls = v; }
        public boolean isIncludeRandom() { return includeRandom; }
        public void setIncludeRandom(boolean v) { this.includeRandom = v; }
    }

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final List<String> paramTypes;
    private final FuzzConfig config;

    public MethodFuzzer(String className, String methodName, String descriptor, FuzzConfig config) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.paramTypes = parseParameterTypes(descriptor);
        this.config = config != null ? config : new FuzzConfig();
    }

    public List<Object[]> generateInputSets() {
        List<Object[]> inputSets = new ArrayList<>();

        if (paramTypes.isEmpty()) {
            inputSets.add(new Object[0]);
            return inputSets;
        }

        List<List<Object>> valuesPerParam = new ArrayList<>();
        for (String type : paramTypes) {
            valuesPerParam.add(generateValuesForType(type));
        }

        if (paramTypes.size() == 1) {
            for (Object val : valuesPerParam.get(0)) {
                inputSets.add(new Object[]{val});
            }
        } else {
            generateCombinations(valuesPerParam, inputSets, config.iterationsPerType * 3);
        }

        return inputSets;
    }

    private void generateCombinations(List<List<Object>> valuesPerParam, List<Object[]> result, int maxCombos) {
        int[] indices = new int[valuesPerParam.size()];
        int totalCombos = 1;
        for (List<Object> vals : valuesPerParam) {
            totalCombos *= vals.size();
        }

        if (totalCombos <= maxCombos) {
            for (int i = 0; i < totalCombos; i++) {
                Object[] combo = new Object[valuesPerParam.size()];
                int idx = i;
                for (int p = valuesPerParam.size() - 1; p >= 0; p--) {
                    int size = valuesPerParam.get(p).size();
                    combo[p] = valuesPerParam.get(p).get(idx % size);
                    idx /= size;
                }
                result.add(combo);
            }
        } else {
            Set<String> seen = new HashSet<>();
            Random rand = ThreadLocalRandom.current();
            int attempts = 0;
            while (result.size() < maxCombos && attempts < maxCombos * 10) {
                Object[] combo = new Object[valuesPerParam.size()];
                for (int p = 0; p < valuesPerParam.size(); p++) {
                    List<Object> vals = valuesPerParam.get(p);
                    combo[p] = vals.get(rand.nextInt(vals.size()));
                }
                String key = Arrays.toString(combo);
                if (seen.add(key)) {
                    result.add(combo);
                }
                attempts++;
            }
        }
    }

    private List<Object> generateValuesForType(String type) {
        List<Object> values = new ArrayList<>();

        switch (type) {
            case "I":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 100, -100));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add(ThreadLocalRandom.current().nextInt());
                    }
                }
                break;

            case "J":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 100L));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add(ThreadLocalRandom.current().nextLong());
                    }
                }
                break;

            case "D":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE,
                            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.5, -0.5));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add(ThreadLocalRandom.current().nextDouble() * 1000 - 500);
                    }
                }
                break;

            case "F":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList(0.0f, 1.0f, -1.0f, Float.MAX_VALUE, Float.MIN_VALUE,
                            Float.NaN, Float.POSITIVE_INFINITY, 0.5f));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add((float)(ThreadLocalRandom.current().nextDouble() * 100 - 50));
                    }
                }
                break;

            case "Z":
                values.addAll(Arrays.asList(true, false));
                break;

            case "B":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList((byte)0, (byte)1, (byte)-1, Byte.MAX_VALUE, Byte.MIN_VALUE));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add((byte)ThreadLocalRandom.current().nextInt(-128, 128));
                    }
                }
                break;

            case "S":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList((short)0, (short)1, (short)-1, Short.MAX_VALUE, Short.MIN_VALUE));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add((short)ThreadLocalRandom.current().nextInt(-32768, 32768));
                    }
                }
                break;

            case "C":
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList('a', 'Z', '0', ' ', '\n', '\t', '\0', (char)255));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add((char)ThreadLocalRandom.current().nextInt(32, 127));
                    }
                }
                break;

            case "Ljava/lang/String;":
                if (config.includeNulls) {
                    values.add(null);
                }
                if (config.includeEdgeCases) {
                    values.addAll(Arrays.asList("", "test", "Hello World", "12345",
                            "a", " ", "\n", "null", "true", "false",
                            "ABCDEFGHIJ", "!@#$%^&*()", "\t\r\n"));
                }
                if (config.includeRandom) {
                    for (int i = 0; i < config.iterationsPerType; i++) {
                        values.add(generateRandomString(ThreadLocalRandom.current().nextInt(1, 20)));
                    }
                }
                break;

            default:
                if (type.startsWith("L") || type.startsWith("[")) {
                    if (config.includeNulls) {
                        values.add(null);
                    }
                } else {
                    values.add(0);
                }
        }

        if (values.isEmpty()) {
            values.add(null);
        }

        return values;
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public List<FuzzResult> runFuzz(ProgressCallback callback) {
        List<Object[]> inputSets = generateInputSets();
        List<FuzzResult> results = new ArrayList<>();

        VMExecutionService service = VMExecutionService.getInstance();
        if (!service.isInitialized()) {
            service.initialize();
        }

        int total = inputSets.size();
        int current = 0;

        for (Object[] inputs : inputSets) {
            if (callback != null) {
                callback.onProgress(current, total, "Testing input " + (current + 1) + " of " + total);
            }

            try {
                ExecutionResult result = service.executeStaticMethod(className, methodName, descriptor, inputs);
                results.add(new FuzzResult(inputs, result));
            } catch (Exception e) {
                ExecutionResult errorResult = ExecutionResult.builder()
                        .success(false)
                        .exception(e)
                        .build();
                results.add(new FuzzResult(inputs, errorResult));
            }

            current++;
        }

        if (callback != null) {
            callback.onComplete(results.size());
        }

        return results;
    }

    public Map<String, List<FuzzResult>> groupByOutcome(List<FuzzResult> results) {
        Map<String, List<FuzzResult>> grouped = new LinkedHashMap<>();
        for (FuzzResult r : results) {
            grouped.computeIfAbsent(r.getOutcomeKey(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    public List<FuzzResult> selectDiverseResults(List<FuzzResult> results, int maxPerCategory) {
        Map<String, List<FuzzResult>> grouped = groupByOutcome(results);
        List<FuzzResult> diverse = new ArrayList<>();

        for (List<FuzzResult> group : grouped.values()) {
            int count = Math.min(maxPerCategory, group.size());
            for (int i = 0; i < count; i++) {
                diverse.add(group.get(i));
            }
        }

        return diverse;
    }

    private List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        int i = descriptor.indexOf('(');
        if (i < 0) return types;
        i++;

        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) break;
                types.add(descriptor.substring(i, end + 1));
                i = end + 1;
            } else if (c == '[') {
                int start = i;
                i++;
                while (i < descriptor.length() && descriptor.charAt(i) == '[') i++;
                if (i < descriptor.length()) {
                    char elem = descriptor.charAt(i);
                    if (elem == 'L') {
                        int end = descriptor.indexOf(';', i);
                        if (end < 0) break;
                        types.add(descriptor.substring(start, end + 1));
                        i = end + 1;
                    } else {
                        types.add(descriptor.substring(start, i + 1));
                        i++;
                    }
                }
            } else {
                types.add(String.valueOf(c));
                i++;
            }
        }
        return types;
    }

    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
        void onComplete(int totalResults);
    }
}
