package com.tonic.plugin.context;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pattern.PatternSearch;
import com.tonic.analysis.pattern.SearchResult;
import com.tonic.analysis.query.exec.QueryBatchRunner;
import com.tonic.analysis.query.exec.QueryService;
import com.tonic.analysis.query.planner.QueryMatch;
import com.tonic.analysis.query.planner.QueryTarget;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.analysis.xref.Xref;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.plugin.api.AnalysisApi;
import com.tonic.plugin.api.ProjectApi;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.XrefQueryService;
import com.tonic.service.deadcode.DeadCodeAnalyzer;
import com.tonic.service.deadcode.DeadCodeConfig;
import com.tonic.service.deadcode.DeadCodeReport;
import com.tonic.service.deadcode.DeadItem;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AnalysisApiImpl implements AnalysisApi {

    private final ProjectModel projectModel;
    private final CallGraphApiImpl callGraphApi;
    private final DataFlowApiImpl dataFlowApi;
    private final PatternApiImpl patternApi;
    private final TypeApiImpl typeApi;
    private final StringApiImpl stringApi;
    private final DecompileApiImpl decompileApi;
    private final XrefApiImpl xrefApi;
    private final QueryApiImpl queryApi;
    private final DeadCodeApiImpl deadCodeApi;

    public AnalysisApiImpl(ProjectModel projectModel) {
        this.projectModel = projectModel;
        this.callGraphApi = new CallGraphApiImpl();
        this.dataFlowApi = new DataFlowApiImpl();
        this.patternApi = new PatternApiImpl();
        this.typeApi = new TypeApiImpl();
        this.stringApi = new StringApiImpl();
        this.decompileApi = new DecompileApiImpl();
        this.xrefApi = new XrefApiImpl();
        this.queryApi = new QueryApiImpl();
        this.deadCodeApi = new DeadCodeApiImpl();
    }

    @Override
    public CallGraphApi getCallGraph() {
        return callGraphApi;
    }

    @Override
    public DataFlowApi getDataFlow() {
        return dataFlowApi;
    }

    @Override
    public PatternApi getPatterns() {
        return patternApi;
    }

    @Override
    public TypeApi getTypes() {
        return typeApi;
    }

    @Override
    public StringApi getStrings() {
        return stringApi;
    }

    @Override
    public DecompileApi getDecompile() {
        return decompileApi;
    }

    @Override
    public XrefApi getXrefs() {
        return xrefApi;
    }

    @Override
    public QueryApi getQuery() {
        return queryApi;
    }

    @Override
    public DeadCodeApi getDeadCode() {
        return deadCodeApi;
    }

    private class CallGraphApiImpl implements CallGraphApi {
        private CallGraph callGraph;

        @Override
        public void build() {
            ClassPool pool = projectModel.getClassPool();
            if (pool != null) {
                callGraph = CallGraph.build(pool);
            }
        }

        @Override
        public void buildForClass(String className) {
            build();
        }

        @Override
        public List<CallSite> getCallersOf(String className, String methodName) {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptyList();

            List<CallSite> sites = new ArrayList<>();
            String normalizedName = className.replace('.', '/');

            MethodReference target = findMethod(normalizedName, methodName);
            if (target == null) return sites;

            Set<MethodReference> callers = callGraph.getCallers(target);
            for (MethodReference caller : callers) {
                sites.add(new CallSite(
                    caller.getOwner(),
                    caller.getName(),
                    normalizedName,
                    methodName,
                    -1
                ));
            }
            return sites;
        }

        @Override
        public List<CallSite> getCalleesOf(String className, String methodName) {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptyList();

            List<CallSite> sites = new ArrayList<>();
            String normalizedName = className.replace('.', '/');

            MethodReference source = findMethod(normalizedName, methodName);
            if (source == null) return sites;

            Set<MethodReference> callees = callGraph.getCallees(source);
            for (MethodReference callee : callees) {
                sites.add(new CallSite(
                    normalizedName,
                    methodName,
                    callee.getOwner(),
                    callee.getName(),
                    -1
                ));
            }
            return sites;
        }

        @Override
        public List<String> getCallChain(String fromClass, String fromMethod, String toClass, String toMethod) {
            if (callGraph == null) build();

            return Collections.emptyList();
        }

        @Override
        public Set<String> getReachableMethods(String className, String methodName) {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptySet();

            Set<String> reachable = new HashSet<>();
            String normalizedName = className.replace('.', '/');

            MethodReference source = findMethod(normalizedName, methodName);
            if (source == null) return reachable;

            Set<MethodReference> reachableRefs = callGraph.getReachableFrom(Collections.singleton(source));
            for (MethodReference ref : reachableRefs) {
                reachable.add(ref.getOwner() + "." + ref.getName());
            }
            return reachable;
        }

        @Override
        public boolean canReach(String fromClass, String fromMethod, String toClass, String toMethod) {
            if (callGraph == null) build();
            if (callGraph == null) return false;

            String fromNormalized = fromClass.replace('.', '/');
            String toNormalized = toClass.replace('.', '/');

            MethodReference fromRef = findMethod(fromNormalized, fromMethod);
            MethodReference toRef = findMethod(toNormalized, toMethod);

            if (fromRef == null || toRef == null) return false;
            return callGraph.canReach(fromRef, toRef);
        }

        @Override
        public int getCallCount(String className, String methodName) {
            return getCallersOf(className, methodName).size();
        }

        @Override
        public List<CallNode> getCallersToDepth(String className, String methodName, String descriptor,
                                                int depth, int maxNodes) {
            return walk(className, methodName, descriptor, depth, maxNodes, true);
        }

        @Override
        public List<CallNode> getCalleesToDepth(String className, String methodName, String descriptor,
                                                int depth, int maxNodes) {
            return walk(className, methodName, descriptor, depth, maxNodes, false);
        }

        /** Depth-bounded BFS over caller/callee edges from a focus method, capped at {@code maxNodes} results. */
        private List<CallNode> walk(String className, String methodName, String descriptor,
                                    int depth, int maxNodes, boolean callers) {
            if (callGraph == null) {
                build();
            }
            if (callGraph == null) {
                return Collections.emptyList();
            }
            MethodReference focus = new MethodReference(className.replace('.', '/'), methodName, descriptor);
            Map<MethodReference, Integer> seen = new LinkedHashMap<>();
            Set<MethodReference> frontier = new LinkedHashSet<>();
            frontier.add(focus);
            for (int d = 0; d < depth && !frontier.isEmpty(); d++) {
                Set<MethodReference> next = new LinkedHashSet<>();
                for (MethodReference ref : frontier) {
                    Set<MethodReference> edges = callers ? callGraph.getCallers(ref) : callGraph.getCallees(ref);
                    for (MethodReference edge : edges) {
                        if (!seen.containsKey(edge) && seen.size() < maxNodes) {
                            seen.put(edge, d + 1);
                            next.add(edge);
                        }
                    }
                }
                if (seen.size() >= maxNodes) {
                    break;
                }
                frontier = next;
            }
            List<CallNode> result = new ArrayList<>(seen.size());
            for (Map.Entry<MethodReference, Integer> entry : seen.entrySet()) {
                MethodReference ref = entry.getKey();
                result.add(new CallNode(ref.getOwner(), ref.getName(), ref.getDescriptor(), entry.getValue()));
            }
            return result;
        }

        private MethodReference findMethod(String className, String methodName) {
            for (CallGraphNode node : callGraph.getPoolNodes()) {
                MethodReference ref = node.getReference();
                if (ref.getOwner().equals(className) && ref.getName().equals(methodName)) {
                    return ref;
                }
            }
            return null;
        }
    }

    private static class DataFlowApiImpl implements DataFlowApi {

        @Override
        public DataFlowResult analyze(String className, String methodName) {
            return new DataFlowResult(className, methodName, Collections.emptyList(), Collections.emptyList());
        }

        @Override
        public List<TaintFlow> trackTaint(String source, String sink) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getDefinitions(String className, String methodName, int varIndex) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getUses(String className, String methodName, int varIndex) {
            return Collections.emptyList();
        }

        @Override
        public boolean hasUninitializedRead(String className, String methodName) {
            return false;
        }
    }

    private class PatternApiImpl implements PatternApi {
        private final Map<String, PatternMatcher> customPatterns = new HashMap<>();

        @Override
        public List<PatternMatch> findPattern(String patternType) {
            PatternMatcher matcher = customPatterns.get(patternType);
            if (matcher == null) return Collections.emptyList();

            List<PatternMatch> matches = new ArrayList<>();
            for (ClassEntryModel entry : projectModel.getAllClasses()) {
                matches.addAll(matcher.match(new ClassInfoImpl(entry)));
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findMethodCalls(String ownerPattern, String namePattern) {
            ClassPool pool = projectModel.getClassPool();
            if (pool == null) return Collections.emptyList();

            List<PatternMatch> matches = new ArrayList<>();
            try {
                PatternSearch search = new PatternSearch(pool)
                    .inAllClasses()
                    .limit(100);

                String pattern = ownerPattern.replace("*", ".*") + "." + namePattern.replace("*", ".*");
                List<SearchResult> results = search.findMethodCalls(pattern);

                for (SearchResult result : results) {
                    String className = result.getClassFile() != null ? result.getClassFile().getClassName() : "";
                    String methodName = result.getMethod() != null ? result.getMethod().getName() : "";
                    matches.add(new PatternMatch(
                        className,
                        methodName,
                        -1,
                        result.getDescription(),
                        Collections.emptyMap()
                    ));
                }
            } catch (Exception e) {
                // Pattern search failed, return empty
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findFieldAccess(String ownerPattern, String namePattern) {
            ClassPool pool = projectModel.getClassPool();
            if (pool == null) return Collections.emptyList();

            List<PatternMatch> matches = new ArrayList<>();
            try {
                PatternSearch search = new PatternSearch(pool)
                    .inAllClasses()
                    .limit(100);

                String pattern = namePattern.replace("*", ".*");
                List<SearchResult> results = search.findFieldsByName(pattern);

                for (SearchResult result : results) {
                    String className = result.getClassFile() != null ? result.getClassFile().getClassName() : "";
                    String methodName = result.getMethod() != null ? result.getMethod().getName() : "";
                    matches.add(new PatternMatch(
                        className,
                        methodName,
                        -1,
                        result.getDescription(),
                        Collections.emptyMap()
                    ));
                }
            } catch (Exception e) {
                // Pattern search failed, return empty
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findStringLiterals(String pattern) {
            List<PatternMatch> matches = new ArrayList<>();
            Pattern regex = Pattern.compile(pattern);

            for (StringInfo str : stringApi.getAllStrings()) {
                if (regex.matcher(str.getValue()).find()) {
                    matches.add(new PatternMatch(
                        str.getClassName(),
                        str.getMethodName(),
                        str.getLineNumber(),
                        str.getValue(),
                        Collections.emptyMap()
                    ));
                }
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findAnnotations(String annotationType) {
            return Collections.emptyList();
        }

        @Override
        public void registerCustomPattern(String name, PatternMatcher matcher) {
            customPatterns.put(name, matcher);
        }

        private class ClassInfoImpl implements ProjectApi.ClassInfo {
            private final ClassEntryModel entry;

            ClassInfoImpl(ClassEntryModel entry) {
                this.entry = entry;
            }

            @Override
            public String getName() {
                return entry.getClassName();
            }

            @Override
            public String getSimpleName() {
                return entry.getSimpleName();
            }

            @Override
            public String getPackageName() {
                return entry.getPackageName();
            }

            @Override
            public String getSuperclass() {
                return entry.getSuperClassName();
            }

            @Override
            public List<String> getInterfaces() {
                return entry.getInterfaceNames();
            }

            @Override
            public List<ProjectApi.MethodInfo> getMethods() {
                return Collections.emptyList();
            }

            @Override
            public List<ProjectApi.FieldInfo> getFields() {
                return Collections.emptyList();
            }

            @Override
            public int getAccessFlags() {
                return entry.getAccessFlags();
            }

            @Override
            public boolean isInterface() {
                return entry.isInterface();
            }

            @Override
            public boolean isAbstract() {
                return entry.isAbstract();
            }

            @Override
            public boolean isEnum() {
                return entry.isEnum();
            }

            @Override
            public boolean isAnnotation() {
                return entry.isAnnotation();
            }

            @Override
            public byte[] getBytecode() {
                try {
                    return entry.getClassFile().write();
                } catch (Exception e) {
                    return new byte[0];
                }
            }
        }
    }

    private class TypeApiImpl implements TypeApi {

        @Override
        public Optional<String> resolveType(String className, String descriptor) {
            return Optional.of(descriptor);
        }

        @Override
        public boolean isSubtypeOf(String type, String supertype) {
            if (type.equals(supertype)) return true;

            ClassEntryModel entry = projectModel.findClassByName(type);
            if (entry == null) return false;

            String superClass = entry.getSuperClassName();
            if (superClass != null && superClass.equals(supertype)) return true;

            for (String iface : entry.getInterfaceNames()) {
                if (iface.equals(supertype)) return true;
            }

            if (superClass != null && !superClass.equals("java/lang/Object")) {
                return isSubtypeOf(superClass, supertype);
            }

            return false;
        }

        @Override
        public List<String> getSubtypes(String type) {
            List<String> subtypes = new ArrayList<>();
            String normalizedType = type.replace('.', '/');

            for (ClassEntryModel entry : projectModel.getAllClasses()) {
                if (normalizedType.equals(entry.getSuperClassName())) {
                    subtypes.add(entry.getClassName());
                }
                if (entry.getInterfaceNames().contains(normalizedType)) {
                    subtypes.add(entry.getClassName());
                }
            }
            return subtypes;
        }

        @Override
        public List<String> getSupertypes(String type) {
            List<String> supertypes = new ArrayList<>();
            ClassEntryModel entry = projectModel.findClassByName(type);
            if (entry == null) return supertypes;

            String superClass = entry.getSuperClassName();
            if (superClass != null) {
                supertypes.add(superClass);
                supertypes.addAll(getSupertypes(superClass));
            }

            supertypes.addAll(entry.getInterfaceNames());
            return supertypes;
        }

        @Override
        public String getCommonSupertype(String type1, String type2) {
            if (type1.equals(type2)) return type1;

            List<String> supertypes1 = getSupertypes(type1);
            supertypes1.add(0, type1);

            List<String> supertypes2 = getSupertypes(type2);
            supertypes2.add(0, type2);

            for (String st1 : supertypes1) {
                if (supertypes2.contains(st1)) {
                    return st1;
                }
            }

            return "java/lang/Object";
        }
    }

    private class StringApiImpl implements StringApi {

        @Override
        public List<StringInfo> getAllStrings() {
            List<StringInfo> strings = new ArrayList<>();

            for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                try {
                    ClassFile cf = classEntry.getClassFile();
                    ConstPool constPool = cf.getConstPool();
                    List<Item<?>> items = constPool.getItems();

                    for (int i = 1; i < items.size(); i++) {
                        try {
                            Item<?> item = items.get(i);
                            if (item instanceof StringRefItem) {
                                StringRefItem stringRef = (StringRefItem) item;
                                int utf8Index = stringRef.getValue();
                                Item<?> utf8Item = items.get(utf8Index);
                                if (utf8Item instanceof Utf8Item) {
                                    String str = ((Utf8Item) utf8Item).getValue();
                                    if (str != null && !str.isEmpty()) {
                                        strings.add(new StringInfo(
                                            str,
                                            classEntry.getClassName(),
                                            "",
                                            -1
                                        ));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip invalid entries
                        }
                    }
                } catch (Exception e) {
                    // Skip classes with errors
                }
            }
            return strings;
        }

        @Override
        public List<StringInfo> findStrings(String pattern) {
            Pattern regex = Pattern.compile(pattern);
            return getAllStrings().stream()
                .filter(s -> regex.matcher(s.getValue()).find())
                .collect(Collectors.toList());
        }

        @Override
        public Map<String, List<StringInfo>> getStringsByClass() {
            return getAllStrings().stream()
                .collect(Collectors.groupingBy(StringInfo::getClassName));
        }

        @Override
        public List<StringInfo> getPotentialSecrets() {
            return findStrings("(?i)(password|secret|key|token|api[_-]?key|auth)");
        }

        @Override
        public List<StringInfo> getUrls() {
            return findStrings("(?i)https?://[^\\s\"']+");
        }

        @Override
        public List<StringInfo> getSqlQueries() {
            return findStrings("(?i)(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER)\\s+");
        }
    }

    private class DecompileApiImpl implements DecompileApi {

        @Override
        public Optional<String> getSource(String className) {
            ClassEntryModel cls = projectModel.findClassByName(className);
            return cls != null ? Optional.of(decompiledSource(cls)) : Optional.empty();
        }

        @Override
        public List<MethodSourceInfo> getMethodSpans(String className) {
            ClassEntryModel cls = projectModel.findClassByName(className);
            if (cls == null) {
                return Collections.emptyList();
            }
            decompiledSource(cls);
            Map<String, DecompileResult.MethodSpan> spans = cls.getMethodSpans();
            if (spans == null) {
                return Collections.emptyList();
            }
            List<MethodSourceInfo> result = new ArrayList<>(spans.size());
            for (Map.Entry<String, DecompileResult.MethodSpan> entry : spans.entrySet()) {
                String key = entry.getKey();
                int paren = key.indexOf('(');
                String name = paren >= 0 ? key.substring(0, paren) : key;
                String desc = paren >= 0 ? key.substring(paren) : "";
                DecompileResult.MethodSpan span = entry.getValue();
                result.add(new MethodSourceInfo(cls.getClassName(), name, desc,
                        span.getStartLine(), span.getEndLine()));
            }
            return result;
        }

        @Override
        public Optional<MethodSourceInfo> getMethodSpan(String className, String methodName, String descriptor) {
            ClassEntryModel cls = projectModel.findClassByName(className);
            if (cls == null) {
                return Optional.empty();
            }
            decompiledSource(cls);
            Map<String, DecompileResult.MethodSpan> spans = cls.getMethodSpans();
            DecompileResult.MethodSpan span = spans != null ? spans.get(methodName + descriptor) : null;
            if (span == null) {
                return Optional.empty();
            }
            return Optional.of(new MethodSourceInfo(cls.getClassName(), methodName, descriptor,
                    span.getStartLine(), span.getEndLine()));
        }
    }

    private class XrefApiImpl implements XrefApi {

        @Override
        public void ensureBuilt() {
            XrefQueryService.ensureDatabase(projectModel);
        }

        @Override
        public List<UsageInfo> findUsages(TargetKind kind, String className, String memberName, String descriptor) {
            ensureBuilt();
            FindUsagesEvent.TargetType type;
            switch (kind) {
                case METHOD:
                    type = FindUsagesEvent.TargetType.METHOD;
                    break;
                case FIELD:
                    type = FindUsagesEvent.TargetType.FIELD;
                    break;
                default:
                    type = FindUsagesEvent.TargetType.CLASS;
                    memberName = null;
                    descriptor = null;
                    break;
            }
            String internal = internalName(className);
            List<Xref> all = XrefQueryService.getUsages(projectModel, type, internal, memberName, descriptor);
            List<UsageInfo> result = new ArrayList<>(all.size());
            for (Xref xref : all) {
                result.add(new UsageInfo(xref.getSourceClass(), xref.getSourceMethod(),
                        xref.getSourceMethodDesc(), xref.getType().name(), xref.getTargetMember()));
            }
            return result;
        }
    }

    private class QueryApiImpl implements QueryApi {

        @Override
        public QueryResult run(String dsl, long timeBudgetMs, int limit) {
            QueryService service = new QueryService(projectModel.getClassPool());
            service.setUserClassNames(projectModel.getUserClassNames());
            QueryService.QueryConfig config = QueryService.QueryConfig.builder().timeBudgetMs(timeBudgetMs).build();
            try {
                var result = service.executeAsync(dsl, config, NOOP_PROGRESS).get();
                if (result.hasError()) {
                    return new QueryResult(true, result.error(), Collections.emptyList(), 0, 0L, false);
                }
                List<QueryMatch> matches = result.results();
                List<String> labels = new ArrayList<>();
                int count = 0;
                for (QueryMatch match : matches) {
                    if (count++ >= limit) {
                        break;
                    }
                    labels.add(matchLabel(match));
                }
                return new QueryResult(false, null, labels, result.resultCount(),
                        result.executionTimeMs(), matches.size() > limit);
            } catch (Exception e) {
                return new QueryResult(true, e.getMessage(), Collections.emptyList(), 0, 0L, false);
            } finally {
                service.shutdown();
            }
        }
    }

    private class DeadCodeApiImpl implements DeadCodeApi {

        @Override
        public DeadCodeResult analyze(boolean publicAsEntryPoints) {
            DeadCodeConfig config = new DeadCodeConfig(publicAsEntryPoints,
                    Collections.emptySet(), Collections.emptySet());
            DeadCodeReport report = new DeadCodeAnalyzer(projectModel, config).analyze();
            return new DeadCodeResult(mapDead(report.getDeadClasses()), mapDead(report.getDeadMethods()),
                    mapDead(report.getDeadFields()));
        }

        private List<DeadItemInfo> mapDead(List<DeadItem> items) {
            List<DeadItemInfo> out = new ArrayList<>(items.size());
            for (DeadItem item : items) {
                out.add(new DeadItemInfo(item.getOwner(), item.displayLabel()));
            }
            return out;
        }
    }

    private String internalName(String className) {
        ClassEntryModel cls = projectModel.findClassByName(className);
        return cls != null ? cls.getClassName() : className.replace('.', '/');
    }

    /** Decompiled source for a class, reusing/populating the per-class decompilation cache (incl. method spans). */
    private String decompiledSource(ClassEntryModel cls) {
        String cached = cls.getDecompilationCache();
        if (cached != null && cls.getMethodSpans() != null) {
            return cached;
        }
        DecompileResult result = new ClassDecompiler(cls.getClassFile()).decompileWithLineMap();
        cls.setDecompilationCache(result.getSource(), result.getLineMaps(), result.getMethodSpans(),
                result.getFieldSpans(), result.getClassSpan());
        return result.getSource();
    }

    private static String matchLabel(QueryMatch match) {
        QueryTarget target = match.getTarget();
        if (target instanceof QueryTarget.MethodTarget) {
            return ((QueryTarget.MethodTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.PCTarget) {
            return ((QueryTarget.PCTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.ClassTarget) {
            return ((QueryTarget.ClassTarget) target).className();
        }
        Object cls = match.getAttribute("class");
        return cls != null ? cls.toString() : "(match)";
    }

    private static final QueryBatchRunner.ProgressListener NOOP_PROGRESS = new QueryBatchRunner.ProgressListener() {
        @Override
        public void onPhaseStart(String phase, int total) {
        }

        @Override
        public void onProgress(int current, int total, String message) {
        }

        @Override
        public void onComplete(int matchCount) {
        }
    };
}
