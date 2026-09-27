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
import com.tonic.analysis.ssa.ir.FieldAccessInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.xref.Xref;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.plugin.api.AnalysisApi;
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

/** The AnalysisApi over one fixed project model; the call graph and registered patterns are kept per instance. */
public class AnalysisApiImpl implements AnalysisApi
{

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

    /**
     * Creates the API over a project.
     *
     * @param projectModel the project to analyse
     */
    public AnalysisApiImpl(ProjectModel projectModel)
    {
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
    public CallGraphApi getCallGraph()
    {
        return callGraphApi;
    }

    @Override
    public DataFlowApi getDataFlow()
    {
        return dataFlowApi;
    }

    @Override
    public PatternApi getPatterns()
    {
        return patternApi;
    }

    @Override
    public TypeApi getTypes()
    {
        return typeApi;
    }

    @Override
    public StringApi getStrings()
    {
        return stringApi;
    }

    @Override
    public DecompileApi getDecompile()
    {
        return decompileApi;
    }

    @Override
    public XrefApi getXrefs()
    {
        return xrefApi;
    }

    @Override
    public QueryApi getQuery()
    {
        return queryApi;
    }

    @Override
    public DeadCodeApi getDeadCode()
    {
        return deadCodeApi;
    }

    private class CallGraphApiImpl implements CallGraphApi
    {
        private CallGraph callGraph;

        @Override
        public void build()
        {
            ClassPool pool = projectModel.getClassPool();
            if (pool != null)
            {
                callGraph = CallGraph.build(pool);
            }
        }

        @Override
        public void buildForClass(String className)
        {
            build();
        }

        @Override
        public List<CallSite> getCallersOf(String className, String methodName)
        {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptyList();

            String normalizedName = className.replace('.', '/');
            Set<MethodReference> callers = new LinkedHashSet<>();
            for (MethodReference target : findMethods(normalizedName, methodName))
            {
                callers.addAll(callGraph.getCallers(target));
            }
            List<CallSite> sites = new ArrayList<>();
            for (MethodReference caller : callers)
            {
                sites.add(new CallSite(caller.getOwner(), caller.getName(), normalizedName, methodName, -1));
            }
            return sites;
        }

        @Override
        public List<CallSite> getCalleesOf(String className, String methodName)
        {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptyList();

            String normalizedName = className.replace('.', '/');
            Set<MethodReference> callees = new LinkedHashSet<>();
            for (MethodReference source : findMethods(normalizedName, methodName))
            {
                callees.addAll(callGraph.getCallees(source));
            }
            List<CallSite> sites = new ArrayList<>();
            for (MethodReference callee : callees)
            {
                sites.add(new CallSite(normalizedName, methodName, callee.getOwner(), callee.getName(), -1));
            }
            return sites;
        }

        @Override
        public List<String> getCallChain(String fromClass, String fromMethod, String toClass, String toMethod)
        {
            if (callGraph == null) build();

            return Collections.emptyList();
        }

        @Override
        public Set<String> getReachableMethods(String className, String methodName)
        {
            if (callGraph == null) build();
            if (callGraph == null) return Collections.emptySet();

            List<MethodReference> sources = findMethods(className.replace('.', '/'), methodName);
            Set<String> reachable = new HashSet<>();
            if (sources.isEmpty())
            {
                return reachable;
            }
            for (MethodReference ref : callGraph.getReachableFrom(new LinkedHashSet<>(sources)))
            {
                reachable.add(ref.getOwner() + "." + ref.getName());
            }
            return reachable;
        }

        @Override
        public boolean canReach(String fromClass, String fromMethod, String toClass, String toMethod)
        {
            if (callGraph == null) build();
            if (callGraph == null) return false;

            List<MethodReference> targets = findMethods(toClass.replace('.', '/'), toMethod);
            for (MethodReference from : findMethods(fromClass.replace('.', '/'), fromMethod))
            {
                for (MethodReference to : targets)
                {
                    if (callGraph.canReach(from, to))
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int getCallCount(String className, String methodName)
        {
            return getCallersOf(className, methodName).size();
        }

        @Override
        public List<CallNode> getCallersToDepth(String className, String methodName, String descriptor, int depth, int maxNodes)
        {
            return walk(className, methodName, descriptor, depth, maxNodes, true);
        }

        @Override
        public List<CallNode> getCalleesToDepth(String className, String methodName, String descriptor, int depth, int maxNodes)
        {
            return walk(className, methodName, descriptor, depth, maxNodes, false);
        }

        private List<CallNode> walk(String className, String methodName, String descriptor, int depth, int maxNodes, boolean callers)
        {
            if (callGraph == null)
            {
                build();
            }
            if (callGraph == null)
            {
                return Collections.emptyList();
            }
            MethodReference focus = new MethodReference(className.replace('.', '/'), methodName, descriptor);
            Map<MethodReference, Integer> seen = new LinkedHashMap<>();
            Set<MethodReference> frontier = new LinkedHashSet<>();
            frontier.add(focus);
            for (int d = 0; d < depth && !frontier.isEmpty(); d++)
            {
                Set<MethodReference> next = new LinkedHashSet<>();
                for (MethodReference ref : frontier)
                {
                    Set<MethodReference> edges = callers ? callGraph.getCallers(ref) : callGraph.getCallees(ref);
                    for (MethodReference edge : edges)
                    {
                        if (!seen.containsKey(edge) && seen.size() < maxNodes)
                        {
                            seen.put(edge, d + 1);
                            next.add(edge);
                        }
                    }
                }
                if (seen.size() >= maxNodes)
                {
                    break;
                }
                frontier = next;
            }
            List<CallNode> result = new ArrayList<>(seen.size());
            for (Map.Entry<MethodReference, Integer> entry : seen.entrySet())
            {
                MethodReference ref = entry.getKey();
                result.add(new CallNode(ref.getOwner(), ref.getName(), ref.getDescriptor(), entry.getValue()));
            }
            return result;
        }

        private List<MethodReference> findMethods(String className, String methodName)
        {
            List<MethodReference> refs = new ArrayList<>();
            for (CallGraphNode node : callGraph.getPoolNodes())
            {
                MethodReference ref = node.getReference();
                if (ref.getOwner().equals(className) && ref.getName().equals(methodName))
                {
                    refs.add(ref);
                }
            }
            return refs;
        }
    }

    private static class DataFlowApiImpl implements DataFlowApi
    {

        @Override
        public DataFlowResult analyze(String className, String methodName)
        {
            return new DataFlowResult(className, methodName, Collections.emptyList(), Collections.emptyList());
        }

        @Override
        public List<TaintFlow> trackTaint(String source, String sink)
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getDefinitions(String className, String methodName, int varIndex)
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getUses(String className, String methodName, int varIndex)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean hasUninitializedRead(String className, String methodName)
        {
            return false;
        }
    }

    private class PatternApiImpl implements PatternApi
    {
        private final Map<String, PatternMatcher> customPatterns = new HashMap<>();

        @Override
        public List<PatternMatch> findPattern(String patternType)
        {
            PatternMatcher matcher = customPatterns.get(patternType);
            if (matcher == null) return Collections.emptyList();

            List<PatternMatch> matches = new ArrayList<>();
            for (ClassEntryModel entry : projectModel.getAllClasses())
            {
                matches.addAll(matcher.match(new ProjectApiImpl.ClassInfoImpl(entry)));
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findMethodCalls(String ownerPattern, String namePattern)
        {
            Pattern owner = wildcard(ownerPattern);
            Pattern name = wildcard(namePattern);
            return search((instr, method, source, classFile) -> instr instanceof InvokeInstruction && owner.matcher(((InvokeInstruction) instr).getOwner()).matches() && name.matcher(((InvokeInstruction) instr).getName()).matches());
        }

        @Override
        public List<PatternMatch> findFieldAccess(String ownerPattern, String namePattern)
        {
            Pattern owner = wildcard(ownerPattern);
            Pattern name = wildcard(namePattern);
            return search((instr, method, source, classFile) -> instr instanceof FieldAccessInstruction && owner.matcher(((FieldAccessInstruction) instr).getOwner()).matches() && name.matcher(((FieldAccessInstruction) instr).getName()).matches());
        }

        private Pattern wildcard(String pattern)
        {
            return Pattern.compile(pattern.replace("*", ".*"));
        }

        private List<PatternMatch> search(com.tonic.analysis.pattern.PatternMatcher matcher)
        {
            ClassPool pool = projectModel.getClassPool();
            if (pool == null)
            {
                return Collections.emptyList();
            }
            List<PatternMatch> matches = new ArrayList<>();
            for (SearchResult result : new PatternSearch(pool).inAllClasses().limit(100).findPattern(matcher))
            {
                String className = result.getClassFile() != null ? result.getClassFile().getClassName() : "";
                String methodName = result.getMethod() != null ? result.getMethod().getName() : "";
                matches.add(new PatternMatch(className, methodName, -1, result.getDescription(), Collections.emptyMap()));
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findStringLiterals(String pattern)
        {
            List<PatternMatch> matches = new ArrayList<>();
            Pattern regex = Pattern.compile(pattern);

            for (StringInfo str : stringApi.getAllStrings())
            {
                if (regex.matcher(str.getValue()).find())
                {
                    matches.add(new PatternMatch(str.getClassName(), str.getMethodName(), str.getLineNumber(), str.getValue(), Collections.emptyMap()));
                }
            }
            return matches;
        }

        @Override
        public List<PatternMatch> findAnnotations(String annotationType)
        {
            return Collections.emptyList();
        }

        @Override
        public void registerCustomPattern(String name, PatternMatcher matcher)
        {
            customPatterns.put(name, matcher);
        }
    }

    private class TypeApiImpl implements TypeApi
    {

        @Override
        public Optional<String> resolveType(String className, String descriptor)
        {
            return Optional.of(descriptor);
        }

        @Override
        public boolean isSubtypeOf(String type, String supertype)
        {
            String target = supertype.replace('.', '/');
            return type.replace('.', '/').equals(target) || getSupertypes(type).contains(target);
        }

        @Override
        public List<String> getSubtypes(String type)
        {
            Map<String, List<String>> direct = new HashMap<>();
            for (ClassEntryModel entry : projectModel.getAllClasses())
            {
                if (entry.getSuperClassName() != null)
                {
                    direct.computeIfAbsent(entry.getSuperClassName(), k -> new ArrayList<>()).add(entry.getClassName());
                }
                for (String iface : entry.getInterfaceNames())
                {
                    direct.computeIfAbsent(iface, k -> new ArrayList<>()).add(entry.getClassName());
                }
            }
            Set<String> seen = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(type.replace('.', '/'));
            while (!pending.isEmpty())
            {
                for (String sub : direct.getOrDefault(pending.poll(), Collections.emptyList()))
                {
                    if (seen.add(sub))
                    {
                        pending.add(sub);
                    }
                }
            }
            return new ArrayList<>(seen);
        }

        @Override
        public List<String> getSupertypes(String type)
        {
            Set<String> seen = new LinkedHashSet<>();
            ClassEntryModel start = projectModel.findClassByName(type);
            if (start == null)
            {
                return new ArrayList<>();
            }
            Deque<ClassEntryModel> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty())
            {
                ClassEntryModel entry = pending.poll();
                List<String> direct = new ArrayList<>();
                if (entry.getSuperClassName() != null)
                {
                    direct.add(entry.getSuperClassName());
                }
                direct.addAll(entry.getInterfaceNames());
                for (String parent : direct)
                {
                    if (seen.add(parent))
                    {
                        ClassEntryModel parentEntry = projectModel.findClassByName(parent);
                        if (parentEntry != null)
                        {
                            pending.add(parentEntry);
                        }
                    }
                }
            }
            return new ArrayList<>(seen);
        }

        @Override
        public String getCommonSupertype(String type1, String type2)
        {
            if (type1.equals(type2)) return type1;

            List<String> supertypes1 = getSupertypes(type1);
            supertypes1.add(0, type1);

            List<String> supertypes2 = getSupertypes(type2);
            supertypes2.add(0, type2);

            for (String st1 : supertypes1)
            {
                if (supertypes2.contains(st1))
                {
                    return st1;
                }
            }

            return "java/lang/Object";
        }
    }

    private class StringApiImpl implements StringApi
    {

        @Override
        public List<StringInfo> getAllStrings()
        {
            List<StringInfo> strings = new ArrayList<>();

            for (ClassEntryModel classEntry : projectModel.getAllClasses())
            {
                try
                {
                    ClassFile cf = classEntry.getClassFile();
                    ConstPool constPool = cf.getConstPool();
                    List<Item<?>> items = constPool.getItems();

                    for (int i = 1; i < items.size(); i++)
                    {
                        try
                        {
                            Item<?> item = items.get(i);
                            if (item instanceof StringRefItem)
                            {
                                StringRefItem stringRef = (StringRefItem) item;
                                int utf8Index = stringRef.getValue();
                                Item<?> utf8Item = items.get(utf8Index);
                                if (utf8Item instanceof Utf8Item)
                                {
                                    String str = ((Utf8Item) utf8Item).getValue();
                                    if (str != null && !str.isEmpty())
                                    {
                                        strings.add(new StringInfo(str, classEntry.getClassName(), "", -1));
                                    }
                                }
                            }
                        }
                        catch (Exception e)
                        {
                        }
                    }
                }
                catch (Exception e)
                {
                }
            }
            return strings;
        }

        @Override
        public List<StringInfo> findStrings(String pattern)
        {
            Pattern regex = Pattern.compile(pattern);
            return getAllStrings().stream()
                    .filter(s -> regex.matcher(s.getValue()).find())
                    .collect(Collectors.toList());
        }

        @Override
        public Map<String, List<StringInfo>> getStringsByClass()
        {
            return getAllStrings().stream()
                    .collect(Collectors.groupingBy(StringInfo::getClassName));
        }

        @Override
        public List<StringInfo> getPotentialSecrets()
        {
            return findStrings("(?i)(password|secret|key|token|api[_-]?key|auth)");
        }

        @Override
        public List<StringInfo> getUrls()
        {
            return findStrings("(?i)https?://[^\\s\"']+");
        }

        @Override
        public List<StringInfo> getSqlQueries()
        {
            return findStrings("(?i)(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER)\\s+");
        }
    }

    private class DecompileApiImpl implements DecompileApi
    {

        @Override
        public Optional<String> getSource(String className)
        {
            ClassEntryModel cls = projectModel.findClassByName(className);
            return cls != null ? Optional.of(decompiledSource(cls)) : Optional.empty();
        }

        @Override
        public List<MethodSourceInfo> getMethodSpans(String className)
        {
            ClassEntryModel cls = projectModel.findClassByName(className);
            if (cls == null)
            {
                return Collections.emptyList();
            }
            decompiledSource(cls);
            Map<String, DecompileResult.MethodSpan> spans = cls.getMethodSpans();
            if (spans == null)
            {
                return Collections.emptyList();
            }
            List<MethodSourceInfo> result = new ArrayList<>(spans.size());
            for (Map.Entry<String, DecompileResult.MethodSpan> entry : spans.entrySet())
            {
                String key = entry.getKey();
                int paren = key.indexOf('(');
                String name = paren >= 0 ? key.substring(0, paren) : key;
                String desc = paren >= 0 ? key.substring(paren) : "";
                DecompileResult.MethodSpan span = entry.getValue();
                result.add(new MethodSourceInfo(cls.getClassName(), name, desc, span.getStartLine(), span.getEndLine()));
            }
            return result;
        }

        @Override
        public Optional<MethodSourceInfo> getMethodSpan(String className, String methodName, String descriptor)
        {
            ClassEntryModel cls = projectModel.findClassByName(className);
            if (cls == null)
            {
                return Optional.empty();
            }
            decompiledSource(cls);
            Map<String, DecompileResult.MethodSpan> spans = cls.getMethodSpans();
            DecompileResult.MethodSpan span = spans != null ? spans.get(methodName + descriptor) : null;
            if (span == null)
            {
                return Optional.empty();
            }
            return Optional.of(new MethodSourceInfo(cls.getClassName(), methodName, descriptor, span.getStartLine(), span.getEndLine()));
        }
    }

    private class XrefApiImpl implements XrefApi
    {

        @Override
        public void ensureBuilt()
        {
            XrefQueryService.ensureDatabase(projectModel);
        }

        @Override
        public List<UsageInfo> findUsages(TargetKind kind, String className, String memberName, String descriptor)
        {
            ensureBuilt();
            FindUsagesEvent.TargetType type;
            switch (kind)
            {
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
            for (Xref xref : all)
            {
                result.add(new UsageInfo(xref.getSourceClass(), xref.getSourceMethod(), xref.getSourceMethodDesc(), xref.getType().name(), xref.getTargetMember()));
            }
            return result;
        }
    }

    private class QueryApiImpl implements QueryApi
    {

        @Override
        public QueryResult run(String dsl, long timeBudgetMs, int limit)
        {
            QueryService service = new QueryService(projectModel.getClassPool());
            service.setUserClassNames(projectModel.getUserClassNames());
            QueryService.QueryConfig config = QueryService.QueryConfig.builder().timeBudgetMs(timeBudgetMs).build();
            try
            {
                var result = service.executeAsync(dsl, config, NOOP_PROGRESS).get();
                if (result.hasError())
                {
                    return new QueryResult(true, result.error(), Collections.emptyList(), 0, 0L, false);
                }
                List<QueryMatch> matches = result.results();
                List<String> labels = new ArrayList<>();
                int count = 0;
                for (QueryMatch match : matches)
                {
                    if (count++ >= limit)
                    {
                        break;
                    }
                    labels.add(matchLabel(match));
                }
                return new QueryResult(false, null, labels, result.resultCount(), result.executionTimeMs(), matches.size() > limit);
            }
            catch (Exception e)
            {
                return new QueryResult(true, e.getMessage(), Collections.emptyList(), 0, 0L, false);
            }
            finally
            {
                service.shutdown();
            }
        }
    }

    private class DeadCodeApiImpl implements DeadCodeApi
    {

        @Override
        public DeadCodeResult analyze(boolean publicAsEntryPoints)
        {
            DeadCodeConfig config = new DeadCodeConfig(publicAsEntryPoints, Collections.emptySet(), Collections.emptySet());
            DeadCodeReport report = new DeadCodeAnalyzer(projectModel, config).analyze();
            return new DeadCodeResult(mapDead(report.getDeadClasses()), mapDead(report.getDeadMethods()), mapDead(report.getDeadFields()));
        }

        private List<DeadItemInfo> mapDead(List<DeadItem> items)
        {
            List<DeadItemInfo> out = new ArrayList<>(items.size());
            for (DeadItem item : items)
            {
                out.add(new DeadItemInfo(item.getOwner(), item.displayLabel()));
            }
            return out;
        }
    }

    private String internalName(String className)
    {
        ClassEntryModel cls = projectModel.findClassByName(className);
        return cls != null ? cls.getClassName() : className.replace('.', '/');
    }

    private String decompiledSource(ClassEntryModel cls)
    {
        String cached = cls.getDecompilationCache();
        if (cached != null && cls.getMethodSpans() != null)
        {
            return cached;
        }
        DecompileResult result = new ClassDecompiler(cls.getClassFile()).decompileWithLineMap();
        cls.setDecompilationCache(result.getSource(), result.getLineMaps(), result.getMethodSpans(), result.getFieldSpans(), result.getClassSpan());
        return result.getSource();
    }

    private static String matchLabel(QueryMatch match)
    {
        QueryTarget target = match.getTarget();
        if (target instanceof QueryTarget.MethodTarget)
        {
            return ((QueryTarget.MethodTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.PCTarget)
        {
            return ((QueryTarget.PCTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.ClassTarget)
        {
            return ((QueryTarget.ClassTarget) target).className();
        }
        Object cls = match.getAttribute("class");
        return cls != null ? cls.toString() : "(match)";
    }

    private static final QueryBatchRunner.ProgressListener NOOP_PROGRESS = new QueryBatchRunner.ProgressListener()
    {
        @Override
        public void onPhaseStart(String phase, int total)
        {
        }

        @Override
        public void onProgress(int current, int total, String message)
        {
        }

        @Override
        public void onComplete(int matchCount)
        {
        }
    };
}
