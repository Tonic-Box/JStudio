package com.tonic.ui.vm.testgen.objectspec;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ObjectFactory {

    private static final ObjectFactory INSTANCE = new ObjectFactory();

    private ObjectFactory() {
    }

    public static ObjectFactory getInstance() {
        return INSTANCE;
    }

    public List<Object> generateValues(ParamSpec spec, int count) {
        List<Object> values = new ArrayList<>();

        switch (spec.getMode()) {
            case NULL:
                values.add(null);
                break;

            case FIXED:
                values.add(spec.getFixedValue());
                break;

            case FUZZ:
                values.addAll(generateFuzzValues(spec, count));
                break;

            case OBJECT_SPEC:
                ObjectSpec objSpec = spec.getNestedObjectSpec();
                if (objSpec != null) {
                    values.addAll(generateObjectValues(objSpec, count));
                } else {
                    values.add(null);
                }
                break;
        }

        return values;
    }

    public List<Object> generateObjectValues(ObjectSpec spec, int count) {
        ObjectSpec resolved = ObjectTemplateManager.getInstance().resolveSpec(spec);
        if (resolved == null) {
            return Collections.singletonList(null);
        }

        switch (resolved.getMode()) {
            case NULL:
                return Collections.singletonList(null);

            case CONSTRUCTOR:
                return generateConstructorValues(resolved, count);

            case FIELD_INJECTION:
                return generateFieldInjectionValues(resolved, count);

            case EXPRESSION:
                return Collections.singletonList(
                    new PlaceholderObject(resolved.getTypeName(), "EXPR: " + resolved.getExpression())
                );

            case TEMPLATE:
                ObjectTemplate template = ObjectTemplateManager.getInstance()
                    .getTemplate(resolved.getTemplateName());
                if (template != null && template.getSpec() != null) {
                    return generateObjectValues(template.getSpec(), count);
                }
                return Collections.singletonList(null);

            default:
                return Collections.singletonList(null);
        }
    }

    private List<Object> generateConstructorValues(ObjectSpec spec, int count) {
        List<ParamSpec> args = spec.getConstructorArgs();
        if (args.isEmpty()) {
            return Collections.singletonList(
                new ConstructorCall(spec.getTypeName(), spec.getConstructorDescriptor(), new Object[0])
            );
        }

        List<List<Object>> argValueLists = new ArrayList<>();
        for (ParamSpec arg : args) {
            argValueLists.add(generateValues(arg, count));
        }

        List<Object[]> combinations = generateCombinations(argValueLists, count);
        List<Object> results = new ArrayList<>();
        for (Object[] combo : combinations) {
            results.add(new ConstructorCall(spec.getTypeName(), spec.getConstructorDescriptor(), combo));
        }
        return results;
    }

    private List<Object> generateFieldInjectionValues(ObjectSpec spec, int count) {
        Map<String, ParamSpec> fields = spec.getFieldOverrides();
        if (fields.isEmpty()) {
            return Collections.singletonList(
                new FieldInjection(spec.getTypeName(), new LinkedHashMap<>())
            );
        }

        Map<String, List<Object>> fieldValueLists = new LinkedHashMap<>();
        for (Map.Entry<String, ParamSpec> entry : fields.entrySet()) {
            fieldValueLists.put(entry.getKey(), generateValues(entry.getValue(), count));
        }

        List<Map<String, Object>> combinations = generateFieldCombinations(fieldValueLists, count);
        List<Object> results = new ArrayList<>();
        for (Map<String, Object> combo : combinations) {
            results.add(new FieldInjection(spec.getTypeName(), combo));
        }
        return results;
    }

    private List<Object> generateFuzzValues(ParamSpec spec, int count) {
        String typeDesc = spec.getTypeDescriptor();
        FuzzStrategy strategy = spec.getFuzzStrategy();
        if (strategy == null) {
            strategy = FuzzStrategy.defaultStrategy();
        }

        List<Object> values = new ArrayList<>();

        switch (typeDesc) {
            case "I":
                values.addAll(generateIntValues(strategy, count));
                break;
            case "J":
                values.addAll(generateLongValues(strategy, count));
                break;
            case "D":
                values.addAll(generateDoubleValues(strategy, count));
                break;
            case "F":
                values.addAll(generateFloatValues(strategy, count));
                break;
            case "Z":
                values.add(true);
                values.add(false);
                break;
            case "B":
                values.addAll(generateByteValues(strategy, count));
                break;
            case "S":
                values.addAll(generateShortValues(strategy, count));
                break;
            case "C":
                values.addAll(generateCharValues(strategy, count));
                break;
            case "Ljava/lang/String;":
                values.addAll(generateStringValues(strategy, count));
                break;
            default:
                if (strategy.isIncludeNull()) {
                    values.add(null);
                }
        }

        return values;
    }

    private List<Object> generateIntValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();
        long min = strategy.getMinInt();
        long max = strategy.getMaxInt();

        if (strategy.isIncludeEdgeCases()) {
            values.add(0);
            values.add(1);
            values.add(-1);
            if (min <= Integer.MIN_VALUE && max >= Integer.MIN_VALUE) {
                values.add(Integer.MIN_VALUE);
            }
            if (min <= Integer.MAX_VALUE && max >= Integer.MAX_VALUE) {
                values.add(Integer.MAX_VALUE);
            }
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            int rangeMin = (int) Math.max(min, Integer.MIN_VALUE);
            int rangeMax = (int) Math.min(max, Integer.MAX_VALUE);
            values.add(rand.nextInt(rangeMax - rangeMin + 1) + rangeMin);
        }

        return values;
    }

    private List<Object> generateLongValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add(0L);
            values.add(1L);
            values.add(-1L);
            values.add(Long.MIN_VALUE);
            values.add(Long.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add(rand.nextLong());
        }

        return values;
    }

    private List<Object> generateDoubleValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add(0.0);
            values.add(1.0);
            values.add(-1.0);
            values.add(Double.MIN_VALUE);
            values.add(Double.MAX_VALUE);
            values.add(Double.NaN);
            values.add(Double.POSITIVE_INFINITY);
            values.add(Double.NEGATIVE_INFINITY);
        }

        Random rand = ThreadLocalRandom.current();
        double min = strategy.getMinDouble();
        double max = strategy.getMaxDouble();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add(min + rand.nextDouble() * (max - min));
        }

        return values;
    }

    private List<Object> generateFloatValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add(0.0f);
            values.add(1.0f);
            values.add(-1.0f);
            values.add(Float.MIN_VALUE);
            values.add(Float.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add((float) (rand.nextDouble() * 200 - 100));
        }

        return values;
    }

    private List<Object> generateByteValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add((byte) 0);
            values.add((byte) 1);
            values.add((byte) -1);
            values.add(Byte.MIN_VALUE);
            values.add(Byte.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add((byte) rand.nextInt(256));
        }

        return values;
    }

    private List<Object> generateShortValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add((short) 0);
            values.add((short) 1);
            values.add((short) -1);
            values.add(Short.MIN_VALUE);
            values.add(Short.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add((short) rand.nextInt(65536));
        }

        return values;
    }

    private List<Object> generateCharValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases()) {
            values.add('a');
            values.add('Z');
            values.add('0');
            values.add(' ');
            values.add('\n');
            values.add('\0');
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add((char) (rand.nextInt(95) + 32));
        }

        return values;
    }

    private List<Object> generateStringValues(FuzzStrategy strategy, int count) {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeNull()) {
            values.add(null);
        }

        if (strategy.isIncludeEdgeCases()) {
            values.add("");
            values.add("test");
            values.add("Hello World");
            values.add("12345");
            values.add(" ");
            values.add("\n");
        }

        String[] stringSet = strategy.getStringSet();
        if (stringSet != null) {
            for (String s : stringSet) {
                values.add(s);
            }
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++) {
            values.add(generateRandomString(rand.nextInt(15) + 1));
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

    private List<Object[]> generateCombinations(List<List<Object>> valueLists, int maxCombos) {
        List<Object[]> result = new ArrayList<>();

        if (valueLists.isEmpty()) {
            return result;
        }

        int totalCombos = 1;
        for (List<Object> list : valueLists) {
            totalCombos *= list.size();
            if (totalCombos > maxCombos * 10) break;
        }

        if (totalCombos <= maxCombos) {
            int[] indices = new int[valueLists.size()];
            for (int i = 0; i < totalCombos; i++) {
                Object[] combo = new Object[valueLists.size()];
                int idx = i;
                for (int p = valueLists.size() - 1; p >= 0; p--) {
                    int size = valueLists.get(p).size();
                    combo[p] = valueLists.get(p).get(idx % size);
                    idx /= size;
                }
                result.add(combo);
            }
        } else {
            Set<String> seen = new HashSet<>();
            Random rand = ThreadLocalRandom.current();
            int attempts = 0;
            while (result.size() < maxCombos && attempts < maxCombos * 10) {
                Object[] combo = new Object[valueLists.size()];
                for (int p = 0; p < valueLists.size(); p++) {
                    List<Object> vals = valueLists.get(p);
                    combo[p] = vals.get(rand.nextInt(vals.size()));
                }
                String key = Arrays.toString(combo);
                if (seen.add(key)) {
                    result.add(combo);
                }
                attempts++;
            }
        }

        return result;
    }

    private List<Map<String, Object>> generateFieldCombinations(
            Map<String, List<Object>> fieldValueLists, int maxCombos) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<String> fieldNames = new ArrayList<>(fieldValueLists.keySet());
        List<List<Object>> valueLists = new ArrayList<>();
        for (String name : fieldNames) {
            valueLists.add(fieldValueLists.get(name));
        }

        List<Object[]> arrayCombos = generateCombinations(valueLists, maxCombos);
        for (Object[] combo : arrayCombos) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < fieldNames.size(); i++) {
                map.put(fieldNames.get(i), combo[i]);
            }
            result.add(map);
        }

        return result;
    }

    @Getter
    @AllArgsConstructor
    public static class ConstructorCall {
        private final String typeName;
        private final String descriptor;
        private final Object[] args;

        @Override
        public String toString() {
            String simple = typeName;
            int lastSlash = typeName.lastIndexOf('/');
            if (lastSlash >= 0) simple = typeName.substring(lastSlash + 1);

            StringBuilder sb = new StringBuilder("new ");
            sb.append(simple).append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatArg(args[i]));
            }
            sb.append(")");
            return sb.toString();
        }

        private String formatArg(Object arg) {
            if (arg == null) return "null";
            if (arg instanceof String) return "\"" + arg + "\"";
            if (arg instanceof Character) return "'" + arg + "'";
            if (arg instanceof ConstructorCall) return arg.toString();
            return String.valueOf(arg);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class FieldInjection {
        private final String typeName;
        private final Map<String, Object> fieldValues;

        @Override
        public String toString() {
            String simple = typeName;
            int lastSlash = typeName.lastIndexOf('/');
            if (lastSlash >= 0) simple = typeName.substring(lastSlash + 1);

            StringBuilder sb = new StringBuilder(simple);
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : fieldValues.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PlaceholderObject {
        private final String typeName;
        private final String description;

        @Override
        public String toString() {
            return description;
        }
    }
}
