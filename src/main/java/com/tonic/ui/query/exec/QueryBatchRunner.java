package com.tonic.ui.query.exec;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.ast.Predicate;
import com.tonic.ui.query.planner.ClickTarget;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.ResultRow;
import com.tonic.ui.query.planner.filter.StaticFilter;
import com.tonic.ui.vm.testgen.MethodFuzzer;

import com.tonic.analysis.xref.Xref;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class QueryBatchRunner {

    private final ClassPool classPool;
    private final QueryExecutor executor;

    private int maxMethodsToRun = 100;
    private int seedsPerMethod = 5;
    private long timeBudgetMs = 60_000;
    private volatile AtomicBoolean cancelled = new AtomicBoolean(false);
    private Set<String> userClassNames;

    public QueryBatchRunner(ClassPool classPool) {
        this.classPool = classPool;
        this.executor = new QueryExecutor();
    }

    public void setUserClassNames(Set<String> userClassNames) {
        this.userClassNames = userClassNames;
    }

    public void setMaxMethodsToRun(int max) {
        this.maxMethodsToRun = max;
    }

    public void setSeedsPerMethod(int seeds) {
        this.seedsPerMethod = seeds;
    }

    public void setTimeBudgetMs(long ms) {
        this.timeBudgetMs = ms;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public QueryBatchResult run(ProbePlan plan, ProgressListener listener) {
        cancelled.set(false);
        long startTime = System.currentTimeMillis();

        StaticFilter filter = plan.staticFilter();

        Stream<ClassFile> classStream = classPool.getClasses().stream();
        if (userClassNames != null && !userClassNames.isEmpty()) {
            classStream = classStream.filter(cf -> userClassNames.contains(cf.getClassName()));
        }

        Stream<MethodEntry> allMethods = classStream.flatMap(cf -> cf.getMethods().stream());

        Set<MethodEntry> candidateMethods = filter.filterMethods(allMethods);

        if (listener != null) {
            listener.onPhaseStart("Filtering", candidateMethods.size());
        }

        Predicate predicate = plan.originalQuery().predicate();
        boolean predicateStatic = predicate != null && predicate.isStaticallyResolvable();
        boolean hasXref = plan.hasXrefBackedFilter();
        boolean isStaticOnly = predicateStatic && hasXref;

        com.tonic.ui.query.planner.filter.XrefMethodFilter.setStaticMode(isStaticOnly);

        if (isStaticOnly) {
            List<ResultRow> matchingRows = new ArrayList<>();
            Map<String, List<Xref>> xrefsByMethod =
                com.tonic.ui.query.planner.filter.XrefMethodFilter.getLastXrefsByMethod();

            int count = 0;
            int totalCallSites = 0;
            for (MethodEntry method : candidateMethods) {
                String className = method.getOwnerName();
                String methodName = method.getName();
                String desc = method.getDesc();
                String signature = className + "." + methodName + desc;

                List<Xref> xrefs = xrefsByMethod.getOrDefault(signature, Collections.emptyList());
                totalCallSites += xrefs.size();

                List<ResultRow> children = new ArrayList<>();
                for (Xref xref : xrefs) {
                    int pc = xref.getInstructionIndex();
                    int line = xref.getLineNumber();
                    String childLabel = (line > 0 ? "line " + line : "pc " + pc) +
                        " -> " + xref.getTargetDisplay();

                    ClickTarget childTarget = new ClickTarget.PCTarget(className, methodName, desc, pc);
                    Map<String, Object> childColumns = new LinkedHashMap<>();
                    childColumns.put("line", line > 0 ? line : "-");
                    childColumns.put("pc", pc);
                    childColumns.put("target", xref.getTargetDisplay());

                    ResultRow child = ResultRow.builder(childLabel)
                        .target(childTarget)
                        .column("line", line > 0 ? line : "-")
                        .column("pc", pc)
                        .column("target", xref.getTargetDisplay())
                        .asChild()
                        .build();
                    children.add(child);
                }

                ClickTarget target = new ClickTarget.MethodTarget(className, methodName, desc);
                String displayLabel = signature + " (" + xrefs.size() + " call site" +
                    (xrefs.size() != 1 ? "s" : "") + ")";

                ResultRow row = ResultRow.builder(displayLabel)
                    .target(target)
                    .column("class", className)
                    .column("method", methodName)
                    .column("sites", xrefs.size())
                    .children(children)
                    .build();
                matchingRows.add(row);

                count++;
                if (listener != null && count % 10 == 0) {
                    listener.onProgress(count, candidateMethods.size(),
                        "Found " + count + " methods, " + totalCallSites + " call sites (static)");
                }
            }

            if (listener != null) {
                listener.onComplete(matchingRows.size());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            return new QueryBatchResult(
                Collections.emptyList(),
                matchingRows,
                candidateMethods.size(),
                0,
                totalCallSites,
                elapsed,
                false
            );
        }

        List<MethodEntry> methodsToRun = new ArrayList<>();
        for (MethodEntry method : candidateMethods) {
            if (isExecutable(method)) {
                methodsToRun.add(method);
                if (methodsToRun.size() >= maxMethodsToRun) {
                    break;
                }
            }
        }

        List<QueryExecutor.QueryExecutionResult> allResults = new ArrayList<>();
        List<ResultRow> matchingRows = new ArrayList<>();

        AtomicInteger completed = new AtomicInteger(0);
        int totalToRun = methodsToRun.size();

        if (listener != null) {
            listener.onPhaseStart("Executing", totalToRun);
        }

        for (MethodEntry method : methodsToRun) {
            if (cancelled.get()) {
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeBudgetMs) {
                break;
            }

            List<Object[]> inputSets = generateInputs(method);

            for (Object[] inputs : inputSets) {
                if (cancelled.get()) break;

                QueryExecutor.QueryExecutionResult result = executor.execute(plan, method, inputs);
                allResults.add(result);

                if (result.isMatch()) {
                    List<ResultRow> rows = plan.projector().project(result.probeResult());
                    matchingRows.addAll(rows);
                }
            }

            int done = completed.incrementAndGet();
            if (listener != null) {
                listener.onProgress(done, totalToRun,
                    "Executed " + done + "/" + totalToRun + " methods");
            }
        }

        long endTime = System.currentTimeMillis();

        if (listener != null) {
            listener.onComplete(matchingRows.size());
        }

        return new QueryBatchResult(
            allResults,
            matchingRows,
            candidateMethods.size(),
            methodsToRun.size(),
            allResults.size(),
            endTime - startTime,
            cancelled.get()
        );
    }

    private boolean isExecutable(MethodEntry method) {
        int access = method.getAccess();

        if ((access & 0x0400) != 0) return false;
        if ((access & 0x0100) != 0) return false;

        if (method.getCodeAttribute() == null) return false;

        return true;
    }

    private List<Object[]> generateInputs(MethodEntry method) {
        String className = method.getOwnerName();
        String methodName = method.getName();
        String descriptor = method.getDesc();

        MethodFuzzer.FuzzConfig config = new MethodFuzzer.FuzzConfig();
        config.setIterationsPerType(2);
        config.setIncludeEdgeCases(true);
        config.setIncludeNulls(true);
        config.setIncludeRandom(true);

        try {
            MethodFuzzer fuzzer = new MethodFuzzer(className, methodName, descriptor, config);
            List<Object[]> allInputs = fuzzer.generateInputSets();

            if (allInputs.size() > seedsPerMethod) {
                return selectDiverse(allInputs, seedsPerMethod);
            }
            return allInputs;

        } catch (Exception e) {
            List<Object[]> fallback = new ArrayList<>();
            fallback.add(new Object[0]);
            return fallback;
        }
    }

    private List<Object[]> selectDiverse(List<Object[]> inputs, int max) {
        if (inputs.size() <= max) {
            return inputs;
        }

        List<Object[]> selected = new ArrayList<>();
        int step = inputs.size() / max;

        for (int i = 0; i < max && i * step < inputs.size(); i++) {
            selected.add(inputs.get(i * step));
        }

        return selected;
    }

    public static final class QueryBatchResult {
        private final List<QueryExecutor.QueryExecutionResult> allExecutions;
        private final List<ResultRow> matchingRows;
        private final int candidateMethodCount;
        private final int executedMethodCount;
        private final int totalExecutions;
        private final long totalTimeMs;
        private final boolean wasCancelled;

        public QueryBatchResult(List<QueryExecutor.QueryExecutionResult> allExecutions,
                                List<ResultRow> matchingRows, int candidateMethodCount,
                                int executedMethodCount, int totalExecutions,
                                long totalTimeMs, boolean wasCancelled) {
            this.allExecutions = allExecutions;
            this.matchingRows = matchingRows;
            this.candidateMethodCount = candidateMethodCount;
            this.executedMethodCount = executedMethodCount;
            this.totalExecutions = totalExecutions;
            this.totalTimeMs = totalTimeMs;
            this.wasCancelled = wasCancelled;
        }

        public List<QueryExecutor.QueryExecutionResult> allExecutions() { return allExecutions; }
        public List<ResultRow> matchingRows() { return matchingRows; }
        public int candidateMethodCount() { return candidateMethodCount; }
        public int executedMethodCount() { return executedMethodCount; }
        public int totalExecutions() { return totalExecutions; }
        public long totalTimeMs() { return totalTimeMs; }
        public boolean wasCancelled() { return wasCancelled; }

        public int matchCount() {
            return matchingRows.size();
        }

        public double matchRate() {
            if (totalExecutions == 0) return 0.0;
            return (double) matchCount() / totalExecutions;
        }
    }

    public interface ProgressListener {
        void onPhaseStart(String phase, int total);
        void onProgress(int current, int total, String message);
        void onComplete(int matchCount);
    }
}
