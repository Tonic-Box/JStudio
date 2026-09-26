package com.tonic.ui.vm.testgen;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;
import com.tonic.ui.vm.testgen.objectspec.ParamSpec;
import com.tonic.ui.vm.testgen.objectspec.ValueMode;
import com.tonic.util.DescriptorParser;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Runs a method in the VM over generated inputs, an instance method on receivers built from a receiver spec, and records each outcome with the branch path it took. */
public class MethodFuzzer
{

    /** One fuzz run: the receiver and inputs, the execution result, and the branch path, keyed by path plus a coarse category of the return value or exception. */
    public static class FuzzResult
    {
        @Getter
        private final Object receiver;
        private final Object[] inputs;
        @Getter
        private final ExecutionResult result;
        @Getter
        private final String outcomeKey;
        @Getter
        private final String branchPathSignature;
        @Getter
        private final String branchSummary;
        @Getter
        private final int uniqueBranchPoints;

        /**
         * Creates a result with no branch tracking.
         *
         * @param receiver the receiver spec the instance was built from, null for a static method
         * @param inputs the arguments passed, copied
         * @param result the execution result
         */
        public FuzzResult(Object receiver, Object[] inputs, ExecutionResult result)
        {
            this(receiver, inputs, result, "NO_BRANCHES", "No branches tracked", 0);
        }

        /**
         * Creates a result with its branch path.
         *
         * @param receiver the receiver spec the instance was built from, null for a static method
         * @param inputs the arguments passed, copied
         * @param result the execution result
         * @param branchPathSignature the signature of the branch path taken
         * @param branchSummary a readable summary of the branch path
         * @param uniqueBranchPoints the number of distinct branch sites visited
         */
        public FuzzResult(Object receiver, Object[] inputs, ExecutionResult result, String branchPathSignature, String branchSummary, int uniqueBranchPoints)
        {
            this.receiver = receiver;
            this.inputs = inputs.clone();
            this.result = result;
            this.branchPathSignature = branchPathSignature;
            this.branchSummary = branchSummary;
            this.uniqueBranchPoints = uniqueBranchPoints;
            this.outcomeKey = computeGroupKey();
        }

        private String computeGroupKey()
        {
            return branchPathSignature + "|" + computeReturnKey(result);
        }

        private String computeReturnKey(ExecutionResult result)
        {
            if (result.getException() != null)
            {
                String excMsg = result.getException().getMessage();
                if (excMsg != null && excMsg.contains(":"))
                {
                    return "EX:" + excMsg.substring(0, Math.min(excMsg.indexOf(':') + 20, excMsg.length()));
                }
                return "EX:" + result.getException().getClass().getSimpleName();
            }
            if (result.getReturnValue() == null)
            {
                return "NULL";
            }
            Object rv = result.getReturnValue();
            if (rv instanceof Number)
            {
                return "RV:" + categorizeNumber((Number) rv);
            }
            if (rv instanceof Boolean)
            {
                return "RV:" + rv;
            }
            if (rv instanceof String)
            {
                String s = (String) rv;
                if (s.isEmpty()) return "RV:EMPTY_STR";
                if (s.length() < 10) return "RV:SHORT_STR";
                return "RV:STR_" + (s.length() / 10) * 10;
            }
            return "RV:" + rv.getClass().getSimpleName();
        }

        private String categorizeNumber(Number n)
        {
            long v = n.longValue();
            if (v == 0) return "ZERO";
            if (v == 1) return "ONE";
            if (v == -1) return "NEG_ONE";
            if (v < 0) return "NEGATIVE";
            if (v > 1000000) return "LARGE";
            return "POSITIVE";
        }

        /**
         * Copies the arguments passed.
         *
         * @return a copy of the arguments passed
         */
        public Object[] getInputs()
        {
            return inputs.clone();
        }

        /**
         * Describes the outcome for display.
         *
         * @return "Throws" with the exception's simple name, or "Returns" with the value, quoting strings and cutting them at 27 characters
         */
        public String getOutcomeDescription()
        {
            if (result.getException() != null)
            {
                return "Throws " + extractExceptionName(result.getException().getMessage());
            }
            if (result.getReturnValue() == null)
            {
                return "Returns null";
            }
            Object rv = result.getReturnValue();
            if (rv instanceof String)
            {
                String s = (String) rv;
                if (s.length() > 30)
                {
                    return "Returns \"" + s.substring(0, 27) + "...\"";
                }
                return "Returns \"" + s + "\"";
            }
            return "Returns " + rv;
        }

        private String extractExceptionName(String msg)
        {
            if (msg == null) return "Exception";
            if (msg.contains("VM Exception:"))
            {
                String part = msg.substring(msg.indexOf(':') + 1).trim();
                int space = part.indexOf(' ');
                if (space > 0)
                {
                    String name = part.substring(0, space);
                    if (name.contains("/"))
                    {
                        name = name.substring(name.lastIndexOf('/') + 1);
                    }
                    return name;
                }
            }
            return "Exception";
        }
    }

    /** The fuzzing options: random values per parameter and whether to include edge cases, nulls and random values. */
    @Getter
    public static class FuzzConfig
    {
        private int iterationsPerType = 5;
        private boolean includeEdgeCases = true;
        private boolean includeNulls = true;
        private boolean includeRandom = true;

        /**
         * Sets how many random values to generate per parameter; combinations of several parameters are capped at three times this.
         *
         * @param n the number of random values per parameter
         */
        public void setIterationsPerType(int n)
        {
            this.iterationsPerType = n;
        }

        /**
         * Sets whether to include edge-case values.
         *
         * @param v true to include them
         */
        public void setIncludeEdgeCases(boolean v)
        {
            this.includeEdgeCases = v;
        }

        /**
         * Sets whether to include null for reference parameters.
         *
         * @param v true to include it
         */
        public void setIncludeNulls(boolean v)
        {
            this.includeNulls = v;
        }

        /**
         * Sets whether to include random values.
         *
         * @param v true to include them
         */
        public void setIncludeRandom(boolean v)
        {
            this.includeRandom = v;
        }
    }

    @Getter
    private final String className;
    @Getter
    private final String methodName;
    @Getter
    private final String descriptor;
    @Getter
    private final boolean staticMethod;
    private final List<String> paramTypes;
    private final FuzzConfig config;
    private List<ParamSpec> paramSpecs;
    @Getter
    private ParamSpec receiverSpec;

    /**
     * Creates a fuzzer for a method.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method's name
     * @param descriptor the method's descriptor, parsed for parameter types
     * @param staticMethod whether the method is static; an instance method needs a receiver spec before it can run
     * @param config the fuzzing options, or null for the defaults
     * @throws IllegalArgumentException if the descriptor is malformed
     */
    public MethodFuzzer(String className, String methodName, String descriptor, boolean staticMethod, FuzzConfig config)
    {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.staticMethod = staticMethod;
        this.paramTypes = DescriptorParser.parameterDescriptors(descriptor);
        this.config = config != null ? config : new FuzzConfig();
    }

    /**
     * Sets how receivers are built for an instance method; each value the spec generates is combined with the argument sets.
     *
     * @param receiverSpec the receiver's spec, typically a constructor object spec, or null for none
     */
    public void setReceiverSpec(ParamSpec receiverSpec)
    {
        this.receiverSpec = receiverSpec;
    }

    /**
     * Builds a receiver spec from a class's constructors: the no-argument one when present, otherwise the first, with fuzzed arguments.
     *
     * @param owner the class whose instances receive the calls
     * @return the spec, or null when the class declares no constructor
     */
    public static ParamSpec defaultReceiverSpec(ClassFile owner)
    {
        MethodEntry chosen = null;
        for (MethodEntry method : owner.getMethods())
        {
            if (method.getName().equals("<init>") && (chosen == null || method.getDesc().equals("()V")))
            {
                chosen = method;
            }
        }
        if (chosen == null)
        {
            return null;
        }
        ObjectSpec spec = ObjectSpec.withConstructor(owner.getClassName(), chosen.getDesc());
        List<String> types = DescriptorParser.parameterDescriptors(chosen.getDesc());
        for (int i = 0; i < types.size(); i++)
        {
            spec.addConstructorArg(ParamSpec.fuzz("arg" + i, types.get(i)));
        }
        return ParamSpec.object("this", "L" + owner.getClassName() + ";", spec);
    }

    /**
     * Sets per-parameter specs; they are used only when there is one for each parameter.
     *
     * @param specs the specs in parameter order, or null to generate by type
     */
    public void setParameterSpecs(List<ParamSpec> specs)
    {
        this.paramSpecs = specs;
    }

    /**
     * Copies the parameter types.
     *
     * @return a copy of the parameter type descriptors, in order
     */
    public List<String> getParamTypes()
    {
        return new ArrayList<>(paramTypes);
    }

    /**
     * Builds a fuzz spec for each parameter, named arg0, arg1 and so on.
     *
     * @return the specs in parameter order
     */
    public List<ParamSpec> getDefaultParamSpecs()
    {
        List<ParamSpec> specs = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++)
        {
            String type = paramTypes.get(i);
            ParamSpec spec = new ParamSpec("arg" + i, type);
            spec.setMode(ValueMode.FUZZ);
            specs.add(spec);
        }
        return specs;
    }

    /**
     * Generates the argument sets to run: every value for a single parameter, and for several all combinations or a random sample of distinct ones when there are more than three times the per-type count.
     *
     * @return the argument sets, or one empty set when the method takes no parameters
     */
    public List<Object[]> generateInputSets()
    {
        List<Object[]> inputSets = new ArrayList<>();

        if (paramTypes.isEmpty())
        {
            inputSets.add(new Object[0]);
            return inputSets;
        }

        List<List<Object>> valuesPerParam = new ArrayList<>();

        if (paramSpecs != null && paramSpecs.size() == paramTypes.size())
        {
            ObjectFactory factory = ObjectFactory.getInstance();
            for (ParamSpec spec : paramSpecs)
            {
                valuesPerParam.add(factory.generateValues(spec, config.iterationsPerType));
            }
        }
        else
        {
            for (String type : paramTypes)
            {
                valuesPerParam.add(generateValuesForType(type));
            }
        }

        int cap = paramTypes.size() == 1 ? Math.max(1, valuesPerParam.get(0).size()) : config.iterationsPerType * 3;
        inputSets.addAll(ObjectFactory.combinations(valuesPerParam, cap));
        return inputSets;
    }

    private List<Object> generateValuesForType(String type)
    {
        List<Object> values = new ArrayList<>();

        switch (type)
        {
            case "I":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 100, -100));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add(ThreadLocalRandom.current().nextInt());
                    }
                }
                break;

            case "J":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 100L));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add(ThreadLocalRandom.current().nextLong());
                    }
                }
                break;

            case "D":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.5, -0.5));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add(ThreadLocalRandom.current().nextDouble() * 1000 - 500);
                    }
                }
                break;

            case "F":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList(0.0f, 1.0f, -1.0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, 0.5f));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add((float) (ThreadLocalRandom.current().nextDouble() * 100 - 50));
                    }
                }
                break;

            case "Z":
                values.addAll(Arrays.asList(true, false));
                break;

            case "B":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList((byte) 0, (byte) 1, (byte) -1, Byte.MAX_VALUE, Byte.MIN_VALUE));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add((byte) ThreadLocalRandom.current().nextInt(-128, 128));
                    }
                }
                break;

            case "S":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList((short) 0, (short) 1, (short) -1, Short.MAX_VALUE, Short.MIN_VALUE));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add((short) ThreadLocalRandom.current().nextInt(-32768, 32768));
                    }
                }
                break;

            case "C":
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList('a', 'Z', '0', ' ', '\n', '\t', '\0', (char) 255));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add((char) ThreadLocalRandom.current().nextInt(32, 127));
                    }
                }
                break;

            case "Ljava/lang/String;":
                if (config.includeNulls)
                {
                    values.add(null);
                }
                if (config.includeEdgeCases)
                {
                    values.addAll(Arrays.asList("", "test", "Hello World", "12345", "a", " ", "\n", "null", "true", "false", "ABCDEFGHIJ", "!@#$%^&*()", "\t\r\n"));
                }
                if (config.includeRandom)
                {
                    for (int i = 0; i < config.iterationsPerType; i++)
                    {
                        values.add(generateRandomString(ThreadLocalRandom.current().nextInt(1, 20)));
                    }
                }
                break;

            default:
                if (type.startsWith("["))
                {
                    values.addAll(generateArrayValues(type));
                }
                else if (type.startsWith("L"))
                {
                    if (config.includeNulls)
                    {
                        values.add(null);
                    }
                }
                else
                {
                    values.add(0);
                }
        }

        if (values.isEmpty())
        {
            values.add(null);
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

    private List<Object> generateArrayValues(String arrayType)
    {
        List<Object> values = new ArrayList<>();
        String componentType = arrayType.substring(1);

        if (config.includeNulls)
        {
            values.add(null);
        }

        if (config.includeEdgeCases)
        {
            values.add(new Object[0]);

            Object singleElem = getSingleEdgeCaseElement(componentType);
            if (singleElem != null)
            {
                values.add(new Object[]{singleElem});
            }

            Object[] edgeCases = getEdgeCaseElements(componentType);
            if (edgeCases.length > 0)
            {
                values.add(edgeCases);
            }
        }

        if (config.includeRandom)
        {
            for (int i = 0; i < config.iterationsPerType; i++)
            {
                int len = ThreadLocalRandom.current().nextInt(1, 6);
                Object[] arr = new Object[len];
                for (int j = 0; j < len; j++)
                {
                    arr[j] = getRandomElement(componentType);
                }
                values.add(arr);
            }
        }

        return values;
    }

    private Object getSingleEdgeCaseElement(String componentType)
    {
        switch (componentType)
        {
            case "I":
                return 0;
            case "J":
                return 0L;
            case "F":
                return 0.0f;
            case "D":
                return 0.0;
            case "B":
                return (byte) 0;
            case "S":
                return (short) 0;
            case "Z":
                return false;
            case "C":
                return 'a';
            case "Ljava/lang/String;":
                return "";
            default:
                return null;
        }
    }

    private Object[] getEdgeCaseElements(String componentType)
    {
        switch (componentType)
        {
            case "I":
                return new Object[]{0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
            case "J":
                return new Object[]{0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE};
            case "F":
                return new Object[]{0.0f, 1.0f, -1.0f, Float.MAX_VALUE, Float.NaN};
            case "D":
                return new Object[]{0.0, 1.0, -1.0, Double.MAX_VALUE, Double.NaN};
            case "B":
                return new Object[]{(byte) 0, Byte.MAX_VALUE, Byte.MIN_VALUE};
            case "S":
                return new Object[]{(short) 0, Short.MAX_VALUE, Short.MIN_VALUE};
            case "Z":
                return new Object[]{true, false};
            case "C":
                return new Object[]{'a', 'Z', '0', ' '};
            case "Ljava/lang/String;":
                return new Object[]{"", "test", "Hello World"};
            default:
                return new Object[0];
        }
    }

    private Object getRandomElement(String componentType)
    {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        switch (componentType)
        {
            case "I":
                return rand.nextInt();
            case "J":
                return rand.nextLong();
            case "F":
                return (float) (rand.nextDouble() * 100 - 50);
            case "D":
                return rand.nextDouble() * 1000 - 500;
            case "B":
                return (byte) rand.nextInt(-128, 128);
            case "S":
                return (short) rand.nextInt(-32768, 32768);
            case "Z":
                return rand.nextBoolean();
            case "C":
                return (char) rand.nextInt(32, 127);
            case "Ljava/lang/String;":
                return generateRandomString(rand.nextInt(1, 10));
            default:
                return null;
        }
    }

    /**
     * Runs the method once per generated argument set, and for an instance method once per receiver and argument set combination, initializing the VM service first if needed; an execution that throws is recorded as a failed result.
     *
     * @param callback receives progress and completion, or null
     * @return one result per run, in order
     * @throws IllegalStateException if the method is an instance method and no receiver spec is set
     */
    public List<FuzzResult> runFuzz(ProgressCallback callback)
    {
        List<Object[]> runs = generateRuns();
        List<FuzzResult> results = new ArrayList<>();

        VMExecutionService service = VMExecutionService.getInstance();
        if (!service.isInitialized())
        {
            service.initialize();
        }

        int total = runs.size();
        int current = 0;

        for (Object[] run : runs)
        {
            Object receiver = run[0];
            Object[] inputs = (Object[]) run[1];
            if (callback != null)
            {
                callback.onProgress(current, total, "Testing input " + (current + 1) + " of " + total);
            }

            try
            {
                BranchTrackingListener branchListener = new BranchTrackingListener();
                ExecutionResult result = service.executeWithListener(className, methodName, descriptor, receiver, inputs, branchListener);

                String pathSig = branchListener.getPathSignature();
                String summary = branchListener.getSummary();
                int uniquePoints = branchListener.getUniqueBranchPoints();

                results.add(new FuzzResult(receiver, inputs, result, pathSig, summary, uniquePoints));
            }
            catch (Exception e)
            {
                ExecutionResult errorResult = ExecutionResult.builder()
                        .success(false)
                        .exception(e)
                        .build();
                results.add(new FuzzResult(receiver, inputs, errorResult));
            }

            current++;
        }

        if (callback != null)
        {
            callback.onComplete(results.size());
        }

        return results;
    }

    private List<Object[]> generateRuns()
    {
        List<Object> inputSets = new ArrayList<>(generateInputSets());
        List<Object> receivers;
        if (staticMethod)
        {
            receivers = Collections.singletonList(null);
        }
        else if (receiverSpec == null)
        {
            throw new IllegalStateException("Configure how to construct the receiver of " + className + "." + methodName);
        }
        else
        {
            receivers = ObjectFactory.getInstance().generateValues(receiverSpec, config.iterationsPerType);
        }
        int cap = Math.max(inputSets.size(), config.iterationsPerType * 3);
        return ObjectFactory.combinations(List.of(receivers, inputSets), cap);
    }

    /**
     * Groups results by outcome key.
     *
     * @param results the results to group
     * @return the groups in first-seen order
     */
    public Map<String, List<FuzzResult>> groupByOutcome(List<FuzzResult> results)
    {
        Map<String, List<FuzzResult>> grouped = new LinkedHashMap<>();
        for (FuzzResult r : results)
        {
            grouped.computeIfAbsent(r.getOutcomeKey(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    /**
     * Groups results by branch path signature.
     *
     * @param results the results to group
     * @return the groups in first-seen order
     */
    public Map<String, List<FuzzResult>> groupByBranchPath(List<FuzzResult> results)
    {
        Map<String, List<FuzzResult>> grouped = new LinkedHashMap<>();
        for (FuzzResult r : results)
        {
            grouped.computeIfAbsent(r.getBranchPathSignature(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    /**
     * Counts the distinct branch paths.
     *
     * @param results the results to count
     * @return the number of distinct branch path signatures
     */
    public int countUniqueBranchPaths(List<FuzzResult> results)
    {
        Set<String> paths = new HashSet<>();
        for (FuzzResult r : results)
        {
            paths.add(r.getBranchPathSignature());
        }
        return paths.size();
    }

    /**
     * Picks, for each branch path, the first result of each distinct return category.
     *
     * @param results the results to pick from
     * @param maxPerCategory the most results to keep per branch path
     * @return the picked results, grouped by path in first-seen order
     */
    public List<FuzzResult> selectDiverseResults(List<FuzzResult> results, int maxPerCategory)
    {
        Map<String, List<FuzzResult>> groupedByPath = groupByBranchPath(results);
        List<FuzzResult> diverse = new ArrayList<>();

        for (Map.Entry<String, List<FuzzResult>> pathEntry : groupedByPath.entrySet())
        {
            List<FuzzResult> pathResults = pathEntry.getValue();

            Map<String, FuzzResult> returnGroups = new LinkedHashMap<>();
            for (FuzzResult r : pathResults)
            {
                String returnKey = getReturnKey(r);
                if (!returnGroups.containsKey(returnKey))
                {
                    returnGroups.put(returnKey, r);
                }
            }

            int count = 0;
            for (FuzzResult r : returnGroups.values())
            {
                if (count >= maxPerCategory) break;
                diverse.add(r);
                count++;
            }
        }

        return diverse;
    }

    private String getReturnKey(FuzzResult r)
    {
        ExecutionResult result = r.getResult();
        if (result.getException() != null)
        {
            return "EX:" + result.getException().getClass().getSimpleName();
        }
        if (result.getReturnValue() == null)
        {
            return "NULL";
        }
        Object rv = result.getReturnValue();
        if (rv instanceof Number)
        {
            long v = ((Number) rv).longValue();
            if (v == 0) return "RV:ZERO";
            if (v == 1) return "RV:ONE";
            if (v < 0) return "RV:NEGATIVE";
            return "RV:POSITIVE";
        }
        if (rv instanceof Boolean)
        {
            return "RV:" + rv;
        }
        return "RV:OTHER";
    }

    /** Receives progress from runFuzz. */
    public interface ProgressCallback
    {
        /**
         * Called before each run.
         *
         * @param current the zero-based index of the run about to start
         * @param total the number of runs
         * @param message a status line for display
         */
        void onProgress(int current, int total, String message);

        /**
         * Called once all runs are done.
         *
         * @param totalResults the number of results produced
         */
        void onComplete(int totalResults);
    }
}
