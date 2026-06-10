package com.tonic.ui.query.exec;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.ast.Condition;
import com.tonic.ui.query.ast.OrderBy;
import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.ast.Target;
import com.tonic.ui.query.eval.AttributeRegistry;
import com.tonic.ui.query.eval.ConditionEvaluator;
import com.tonic.ui.query.eval.DefaultAttributes;
import com.tonic.ui.query.eval.EvalContext;
import com.tonic.ui.query.eval.EvidenceCollector;
import com.tonic.ui.query.eval.Subject;
import com.tonic.ui.query.planner.ClickTarget;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.ResultRow;
import com.tonic.ui.query.planner.filter.StaticFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a {@link ProbePlan} by evaluating its query condition over the scope-filtered candidates
 * with the registry-driven {@link ConditionEvaluator}. Matches are projected into navigable
 * {@link ResultRow}s whose children point at the bytecode evidence the evaluator collected.
 */
public class QueryBatchRunner {

    private static final AttributeRegistry REGISTRY = DefaultAttributes.create();

    private final ClassPool classPool;

    private long timeBudgetMs = 60_000;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Set<String> userClassNames;

    public QueryBatchRunner(ClassPool classPool) {
        this.classPool = classPool;
    }

    public void setUserClassNames(Set<String> userClassNames) {
        this.userClassNames = userClassNames;
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

        StaticFilter scopeFilter = plan.staticFilter();
        Target target = plan.originalQuery().target();
        Condition condition = plan.originalQuery().condition();
        ConditionEvaluator evaluator = new ConditionEvaluator(REGISTRY);

        List<ClassFile> classes = classPool.getClasses().stream()
                .filter(cf -> userClassNames == null || userClassNames.isEmpty()
                        || userClassNames.contains(cf.getClassName()))
                .collect(Collectors.toList());

        List<ResultRow> rows = new ArrayList<>();
        int candidateCount;

        if (target == Target.CLASSES) {
            Set<ClassFile> candidates = scopeFilter.filterClasses(classes.stream());
            candidateCount = candidates.size();
            if (listener != null) {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            for (ClassFile cf : candidates) {
                if (cancelled.get() || overBudget(startTime)) break;
                EvalContext ctx = new EvalContext(cf, null, new EvidenceCollector());
                if (condition == null || evaluator.eval(condition, new Subject.ClassSubject(cf, ctx))) {
                    rows.add(classRow(cf));
                }
            }
        } else {
            Stream<MethodEntry> allMethods = classes.stream().flatMap(cf -> cf.getMethods().stream());
            Set<MethodEntry> candidates = scopeFilter.filterMethods(allMethods);
            candidateCount = candidates.size();
            if (listener != null) {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            int scanned = 0;
            for (ClassFile cf : classes) {
                if (cancelled.get() || overBudget(startTime)) break;
                for (MethodEntry method : cf.getMethods()) {
                    if (!candidates.contains(method)) continue;
                    EvidenceCollector evidence = new EvidenceCollector();
                    EvalContext ctx = new EvalContext(cf, method, evidence);
                    if (condition == null || evaluator.eval(condition, new Subject.MethodSubject(method, ctx))) {
                        rows.add(methodRow(method, evidence));
                    }
                    if (listener != null && ++scanned % 200 == 0) {
                        listener.onProgress(scanned, candidateCount, "Matched " + rows.size());
                    }
                }
            }
        }

        rows = applyOrderingAndLimit(rows, plan.originalQuery());

        if (listener != null) {
            listener.onComplete(rows.size());
        }

        return new QueryBatchResult(rows, cancelled.get());
    }

    /** Applies the query's {@code ORDER BY} (sort by a result column) then {@code LIMIT} (truncate). */
    private static List<ResultRow> applyOrderingAndLimit(List<ResultRow> rows, Query query) {
        OrderBy orderBy = query.orderBy();
        if (orderBy != null) {
            Comparator<ResultRow> cmp = (a, b) ->
                    compareValues(a.getColumn(orderBy.key()), b.getColumn(orderBy.key()));
            rows.sort(orderBy.ascending() ? cmp : cmp.reversed());
        }
        Integer limit = query.limit();
        if (limit != null && limit >= 0 && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    /** Orders two column values numerically when both look numeric, else case-insensitively by text. */
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Double na = toNumber(a);
        Double nb = toNumber(b);
        if (na != null && nb != null) {
            return Double.compare(na, nb);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static Double toNumber(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean overBudget(long startTime) {
        return System.currentTimeMillis() - startTime > timeBudgetMs;
    }

    private ResultRow methodRow(MethodEntry method, EvidenceCollector evidence) {
        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        List<ResultRow> children = new ArrayList<>();
        for (EvidenceCollector.Hit hit : evidence.hits()) {
            ClickTarget childTarget = new ClickTarget.PCTarget(hit.className(), hit.methodName(), hit.descriptor(), hit.pc());
            children.add(ResultRow.builder(hit.label())
                    .target(childTarget)
                    .column("pc", hit.pc())
                    .asChild()
                    .build());
        }

        String signature = className + "." + methodName + desc;
        String label = children.isEmpty() ? signature
                : signature + " (" + children.size() + " match" + (children.size() != 1 ? "es" : "") + ")";

        return ResultRow.builder(label)
                .target(new ClickTarget.MethodTarget(className, methodName, desc))
                .column("class", className)
                .column("method", methodName)
                .column("matches", children.size())
                .children(children)
                .build();
    }

    private ResultRow classRow(ClassFile cf) {
        return ResultRow.builder(cf.getClassName())
                .target(new ClickTarget.ClassTarget(cf.getClassName()))
                .column("class", cf.getClassName())
                .build();
    }

    public static final class QueryBatchResult {
        private final List<ResultRow> matchingRows;
        private final boolean wasCancelled;

        public QueryBatchResult(List<ResultRow> matchingRows, boolean wasCancelled) {
            this.matchingRows = matchingRows;
            this.wasCancelled = wasCancelled;
        }

        public List<ResultRow> matchingRows() {
            return matchingRows;
        }

        public boolean wasCancelled() {
            return wasCancelled;
        }
    }

    public interface ProgressListener {
        void onPhaseStart(String phase, int total);
        void onProgress(int current, int total, String message);
        void onComplete(int matchCount);
    }
}
