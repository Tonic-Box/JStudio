package com.tonic.ui.vm.testgen.objectspec;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Generates argument values from parameter and object specs; objects come back as descriptions of how to build them, not live instances. */
public class ObjectFactory
{

    private static final ObjectFactory INSTANCE = new ObjectFactory();

    private ObjectFactory()
    {
    }

    /** @return the shared factory */
    public static ObjectFactory getInstance()
    {
        return INSTANCE;
    }

    /**
     * Generates candidate values for a parameter according to its mode.
     *
     * @param spec the parameter's spec
     * @param count how many fuzzed values to aim for, and the cap on object combinations
     * @return one value for fixed or null mode, the fuzzed values, or the built object descriptions, null when no object spec is set
     */
    public List<Object> generateValues(ParamSpec spec, int count)
    {
        List<Object> values = new ArrayList<>();

        switch (spec.getMode())
        {
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
                if (objSpec != null)
                {
                    values.addAll(generateObjectValues(objSpec, count));
                }
                else
                {
                    values.add(null);
                }
                break;
        }

        return values;
    }

    /**
     * Generates object descriptions from a spec, resolving templates first.
     *
     * @param spec the object's spec, or null
     * @param count how many values to aim for per argument or field, and the cap on combinations
     * @return constructor calls, factory calls, field injections, or an expression object; a single null for null specs, missing templates and the null mode
     */
    public List<Object> generateObjectValues(ObjectSpec spec, int count)
    {
        ObjectSpec resolved = ObjectTemplateManager.getInstance().resolveSpec(spec);
        if (resolved == null)
        {
            return Collections.singletonList(null);
        }

        switch (resolved.getMode())
        {

            case CONSTRUCTOR:
                return generateConstructorValues(resolved, count);

            case FACTORY_METHOD:
                return generateFactoryValues(resolved, count);

            case FIELD_INJECTION:
                return generateFieldInjectionValues(resolved, count);

            case EXPRESSION:
                return Collections.singletonList(new ExpressionObject(resolved.getTypeName(), resolved.getExpression()));

            case TEMPLATE:
                ObjectTemplate template = ObjectTemplateManager.getInstance()
                        .getTemplate(resolved.getTemplateName());
                if (template != null && template.getSpec() != null)
                {
                    return generateObjectValues(template.getSpec(), count);
                }
                return Collections.singletonList(null);

            default:
                return Collections.singletonList(null);
        }
    }

    private List<Object> generateConstructorValues(ObjectSpec spec, int count)
    {
        List<Object> results = new ArrayList<>();
        for (Object[] combo : argumentCombinations(spec.getConstructorArgs(), count))
        {
            results.add(new ConstructorCall(spec.getTypeName(), spec.getConstructorDescriptor(), combo));
        }
        return results;
    }

    private List<Object> generateFactoryValues(ObjectSpec spec, int count)
    {
        List<Object> results = new ArrayList<>();
        for (Object[] combo : argumentCombinations(spec.getFactoryArgs(), count))
        {
            results.add(new FactoryCall(spec.getTypeName(), spec.getFactoryMethodName(), spec.getFactoryMethodDescriptor(), combo));
        }
        return results;
    }

    private List<Object[]> argumentCombinations(List<ParamSpec> args, int count)
    {
        if (args.isEmpty())
        {
            return Collections.singletonList(new Object[0]);
        }
        List<List<Object>> argValueLists = new ArrayList<>();
        for (ParamSpec arg : args)
        {
            argValueLists.add(generateValues(arg, count));
        }
        return combinations(argValueLists, count);
    }

    private List<Object> generateFieldInjectionValues(ObjectSpec spec, int count)
    {
        Map<String, ParamSpec> fields = spec.getFieldOverrides();
        if (fields.isEmpty())
        {
            return Collections.singletonList(new FieldInjection(spec.getTypeName(), new LinkedHashMap<>()));
        }

        Map<String, List<Object>> fieldValueLists = new LinkedHashMap<>();
        for (Map.Entry<String, ParamSpec> entry : fields.entrySet())
        {
            fieldValueLists.put(entry.getKey(), generateValues(entry.getValue(), count));
        }

        List<Map<String, Object>> combinations = generateFieldCombinations(fieldValueLists, count);
        List<Object> results = new ArrayList<>();
        for (Map<String, Object> combo : combinations)
        {
            results.add(new FieldInjection(spec.getTypeName(), combo));
        }
        return results;
    }

    private List<Object> generateFuzzValues(ParamSpec spec, int count)
    {
        String typeDesc = spec.getTypeDescriptor();
        FuzzStrategy strategy = spec.getFuzzStrategy();
        if (strategy == null)
        {
            strategy = FuzzStrategy.defaultStrategy();
        }

        List<Object> values = new ArrayList<>();

        switch (typeDesc)
        {
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
                if (strategy.isIncludeNull())
                {
                    values.add(null);
                }
        }

        return values;
    }

    private List<Object> generateIntValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();
        long min = strategy.getMinInt();
        long max = strategy.getMaxInt();

        if (strategy.isIncludeEdgeCases())
        {
            values.add(0);
            values.add(1);
            values.add(-1);
            if (min <= Integer.MIN_VALUE && max >= Integer.MIN_VALUE)
            {
                values.add(Integer.MIN_VALUE);
            }
            if (min <= Integer.MAX_VALUE && max >= Integer.MAX_VALUE)
            {
                values.add(Integer.MAX_VALUE);
            }
        }

        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            int rangeMin = (int) Math.max(min, Integer.MIN_VALUE);
            int rangeMax = (int) Math.min(max, Integer.MAX_VALUE);
            if (rangeMin >= rangeMax)
            {
                values.add(rangeMin);
            }
            else if (rangeMin == Integer.MIN_VALUE && rangeMax == Integer.MAX_VALUE)
            {
                values.add(ThreadLocalRandom.current().nextInt());
            }
            else
            {
                values.add(ThreadLocalRandom.current().nextInt(rangeMin, rangeMax + 1));
            }
        }

        return values;
    }

    private List<Object> generateLongValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
            values.add(0L);
            values.add(1L);
            values.add(-1L);
            values.add(Long.MIN_VALUE);
            values.add(Long.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add(rand.nextLong());
        }

        return values;
    }

    private List<Object> generateDoubleValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
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
        for (int i = 0; i < remaining; i++)
        {
            values.add(min + rand.nextDouble() * (max - min));
        }

        return values;
    }

    private List<Object> generateFloatValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
            values.add(0.0f);
            values.add(1.0f);
            values.add(-1.0f);
            values.add(Float.MIN_VALUE);
            values.add(Float.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add((float) (rand.nextDouble() * 200 - 100));
        }

        return values;
    }

    private List<Object> generateByteValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
            values.add((byte) 0);
            values.add((byte) 1);
            values.add((byte) -1);
            values.add(Byte.MIN_VALUE);
            values.add(Byte.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add((byte) rand.nextInt(256));
        }

        return values;
    }

    private List<Object> generateShortValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
            values.add((short) 0);
            values.add((short) 1);
            values.add((short) -1);
            values.add(Short.MIN_VALUE);
            values.add(Short.MAX_VALUE);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add((short) rand.nextInt(65536));
        }

        return values;
    }

    private List<Object> generateCharValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeEdgeCases())
        {
            values.add('a');
            values.add('Z');
            values.add('0');
            values.add(' ');
            values.add('\n');
            values.add('\0');
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add((char) (rand.nextInt(95) + 32));
        }

        return values;
    }

    private List<Object> generateStringValues(FuzzStrategy strategy, int count)
    {
        List<Object> values = new ArrayList<>();

        if (strategy.isIncludeNull())
        {
            values.add(null);
        }

        if (strategy.isIncludeEdgeCases())
        {
            values.add("");
            values.add("test");
            values.add("Hello World");
            values.add("12345");
            values.add(" ");
            values.add("\n");
        }

        String[] stringSet = strategy.getStringSet();
        if (stringSet != null)
        {
            Collections.addAll(values, stringSet);
        }

        Random rand = ThreadLocalRandom.current();
        int remaining = Math.max(0, count - values.size());
        for (int i = 0; i < remaining; i++)
        {
            values.add(generateRandomString(rand.nextInt(15) + 1));
        }

        return values;
    }

    private String generateRandomString(int length)
    {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++)
        {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Combines one value per list into argument sets: every combination when there are at most the cap, otherwise a random sample of distinct ones; an empty list contributes null.
     *
     * @param valueLists the candidate values for each position
     * @param maxCombos the most combinations to return
     * @return the combinations, empty when there are no positions
     */
    public static List<Object[]> combinations(List<List<Object>> valueLists, int maxCombos)
    {
        List<Object[]> result = new ArrayList<>();
        if (valueLists.isEmpty())
        {
            return result;
        }

        List<List<Object>> lists = new ArrayList<>();
        long totalCombos = 1;
        for (List<Object> list : valueLists)
        {
            List<Object> values = list.isEmpty() ? Collections.singletonList(null) : list;
            lists.add(values);
            totalCombos = Math.min(totalCombos * values.size(), (long) Integer.MAX_VALUE);
        }

        if (totalCombos <= maxCombos)
        {
            for (int i = 0; i < totalCombos; i++)
            {
                Object[] combo = new Object[lists.size()];
                int idx = i;
                for (int p = lists.size() - 1; p >= 0; p--)
                {
                    int size = lists.get(p).size();
                    combo[p] = lists.get(p).get(idx % size);
                    idx /= size;
                }
                result.add(combo);
            }
            return result;
        }

        Set<String> seen = new HashSet<>();
        Random rand = ThreadLocalRandom.current();
        long attempts = 0;
        while (result.size() < maxCombos && attempts < maxCombos * 10L)
        {
            Object[] combo = new Object[lists.size()];
            for (int p = 0; p < lists.size(); p++)
            {
                List<Object> vals = lists.get(p);
                combo[p] = vals.get(rand.nextInt(vals.size()));
            }
            if (seen.add(Arrays.deepToString(combo)))
            {
                result.add(combo);
            }
            attempts++;
        }
        return result;
    }

    private List<Map<String, Object>> generateFieldCombinations(Map<String, List<Object>> fieldValueLists, int maxCombos)
    {

        List<Map<String, Object>> result = new ArrayList<>();

        List<String> fieldNames = new ArrayList<>(fieldValueLists.keySet());
        List<List<Object>> valueLists = new ArrayList<>();
        for (String name : fieldNames)
        {
            valueLists.add(fieldValueLists.get(name));
        }

        List<Object[]> arrayCombos = combinations(valueLists, maxCombos);
        for (Object[] combo : arrayCombos)
        {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < fieldNames.size(); i++)
            {
                map.put(fieldNames.get(i), combo[i]);
            }
            result.add(map);
        }

        return result;
    }

    /** A description of an object built by calling a constructor with given arguments. */
    @Getter
    @AllArgsConstructor
    public static class ConstructorCall
    {
        private final String typeName;
        private final String descriptor;
        private final Object[] args;

        @Override
        public String toString()
        {
            String simple = typeName;
            int lastSlash = typeName.lastIndexOf('/');
            if (lastSlash >= 0) simple = typeName.substring(lastSlash + 1);

            StringBuilder sb = new StringBuilder("new ");
            sb.append(simple).append("(");
            for (int i = 0; i < args.length; i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(formatArg(args[i]));
            }
            sb.append(")");
            return sb.toString();
        }

        private String formatArg(Object arg)
        {
            if (arg == null) return "null";
            if (arg instanceof String) return "\"" + arg + "\"";
            if (arg instanceof Character) return "'" + arg + "'";
            if (arg instanceof ConstructorCall) return arg.toString();
            return String.valueOf(arg);
        }
    }

    /** A description of an object returned by a static factory method on its own class with given arguments. */
    @Getter
    @AllArgsConstructor
    public static class FactoryCall
    {
        private final String typeName;
        private final String methodName;
        private final String descriptor;
        private final Object[] args;

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(typeName.substring(typeName.lastIndexOf('/') + 1));
            sb.append('.').append(methodName).append('(');
            for (int i = 0; i < args.length; i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(args[i] instanceof String ? "\"" + args[i] + "\"" : String.valueOf(args[i]));
            }
            return sb.append(')').toString();
        }
    }

    /** A description of an object built by setting field values directly. */
    @Getter
    @AllArgsConstructor
    public static class FieldInjection
    {
        private final String typeName;
        private final Map<String, Object> fieldValues;

        @Override
        public String toString()
        {
            String simple = typeName;
            int lastSlash = typeName.lastIndexOf('/');
            if (lastSlash >= 0) simple = typeName.substring(lastSlash + 1);

            StringBuilder sb = new StringBuilder(simple);
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : fieldValues.entrySet())
            {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /** An object given by a Java expression; it has a source form but cannot be built in the VM. */
    @Getter
    @AllArgsConstructor
    public static class ExpressionObject
    {
        private final String typeName;
        private final String expression;

        @Override
        public String toString()
        {
            return "EXPR: " + expression;
        }
    }
}
