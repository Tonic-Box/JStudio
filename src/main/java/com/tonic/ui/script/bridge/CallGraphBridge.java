package com.tonic.ui.script.bridge;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassPool;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;

public class CallGraphBridge extends AbstractBridge {

    private CallGraph callGraph;

    public CallGraphBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        super(interpreter, projectModel);
    }

    @Override
    public ScriptValue createBridgeObject() {
        return createCallGraphObject();
    }

    public ScriptValue createCallGraphObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("build", ScriptValue.function(
            ScriptFunction.native0("build", this::build)
        ));

        props.put("isBuilt", ScriptValue.function(
            ScriptFunction.native0("isBuilt", () -> ScriptValue.bool(callGraph != null))
        ));

        props.put("getCallers", ScriptValue.function(
            ScriptFunction.native1("getCallers", this::getCallers)
        ));

        props.put("getCallees", ScriptValue.function(
            ScriptFunction.native1("getCallees", this::getCallees)
        ));

        props.put("canReach", ScriptValue.function(
            ScriptFunction.native2("canReach", this::canReach)
        ));

        props.put("findEntryPoints", ScriptValue.function(
            ScriptFunction.native0("findEntryPoints", this::findEntryPoints)
        ));

        props.put("getTransitiveCallees", ScriptValue.function(
            ScriptFunction.native1("getTransitiveCallees", this::getTransitiveCallees)
        ));

        props.put("getTransitiveCallers", ScriptValue.function(
            ScriptFunction.native1("getTransitiveCallers", this::getTransitiveCallers)
        ));

        props.put("findDeadMethods", ScriptValue.function(
            ScriptFunction.native0("findDeadMethods", this::findDeadMethods)
        ));

        props.put("getReachableFrom", ScriptValue.function(
            ScriptFunction.native1("getReachableFrom", this::getReachableFrom)
        ));

        props.put("methodCount", ScriptValue.function(
            ScriptFunction.native0("methodCount", () ->
                ScriptValue.number(callGraph != null ? callGraph.size() : 0))
        ));

        props.put("edgeCount", ScriptValue.function(
            ScriptFunction.native0("edgeCount", () ->
                ScriptValue.number(callGraph != null ? callGraph.edgeCount() : 0))
        ));

        props.put("getAllMethods", ScriptValue.function(
            ScriptFunction.native0("getAllMethods", this::getAllMethods)
        ));

        props.put("findMethods", ScriptValue.function(
            ScriptFunction.native1("findMethods", this::findMethods)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue build() {
        ClassPool classPool = projectModel.getClassPool();
        if (classPool == null) {
            log("No class pool available");
            return ScriptValue.bool(false);
        }

        log("Building call graph...");
        callGraph = CallGraph.build(classPool);
        log("Call graph built: " + callGraph.size() + " methods, " + callGraph.edgeCount() + " edges");
        return ScriptValue.bool(true);
    }

    private void ensureBuilt() {
        if (callGraph == null) {
            build();
        }
    }

    private ScriptValue getCallers(ScriptValue methodRef) {
        ensureBuilt();
        MethodReference ref = parseMethodRef(methodRef);
        if (ref == null) return ScriptValue.array(new ArrayList<>());

        Set<MethodReference> callers = callGraph.getCallers(ref);
        return wrapMethodRefs(callers);
    }

    private ScriptValue getCallees(ScriptValue methodRef) {
        ensureBuilt();
        MethodReference ref = parseMethodRef(methodRef);
        if (ref == null) return ScriptValue.array(new ArrayList<>());

        Set<MethodReference> callees = callGraph.getCallees(ref);
        return wrapMethodRefs(callees);
    }

    private ScriptValue canReach(ScriptValue from, ScriptValue to) {
        ensureBuilt();
        MethodReference fromRef = parseMethodRef(from);
        MethodReference toRef = parseMethodRef(to);
        if (fromRef == null || toRef == null) return ScriptValue.bool(false);

        return ScriptValue.bool(callGraph.canReach(fromRef, toRef));
    }

    private ScriptValue findEntryPoints() {
        ensureBuilt();
        List<ScriptValue> entries = new ArrayList<>();

        for (CallGraphNode node : callGraph.getPoolNodes()) {
            MethodReference ref = node.getReference();
            if ("main".equals(ref.getName()) && "([Ljava/lang/String;)V".equals(ref.getDescriptor())) {
                entries.add(wrapMethodRef(ref));
            }
            if ("<clinit>".equals(ref.getName())) {
                entries.add(wrapMethodRef(ref));
            }
            if ("<init>".equals(ref.getName()) && !node.hasCaller()) {
                entries.add(wrapMethodRef(ref));
            }
        }

        return ScriptValue.array(entries);
    }

    private ScriptValue getTransitiveCallees(ScriptValue methodRef) {
        ensureBuilt();
        MethodReference ref = parseMethodRef(methodRef);
        if (ref == null) return ScriptValue.array(new ArrayList<>());

        Set<MethodReference> reachable = callGraph.getReachableFrom(Collections.singleton(ref));
        reachable.remove(ref);
        return wrapMethodRefs(reachable);
    }

    private ScriptValue getTransitiveCallers(ScriptValue methodRef) {
        ensureBuilt();
        MethodReference ref = parseMethodRef(methodRef);
        if (ref == null) return ScriptValue.array(new ArrayList<>());

        Set<MethodReference> callers = new LinkedHashSet<>();
        Deque<MethodReference> worklist = new ArrayDeque<>();
        worklist.add(ref);

        while (!worklist.isEmpty()) {
            MethodReference current = worklist.poll();
            if (!callers.add(current)) continue;

            for (MethodReference caller : callGraph.getCallers(current)) {
                if (!callers.contains(caller)) {
                    worklist.add(caller);
                }
            }
        }

        callers.remove(ref);
        return wrapMethodRefs(callers);
    }

    private ScriptValue findDeadMethods() {
        ensureBuilt();
        Set<MethodReference> dead = callGraph.findMethodsWithNoCallers();
        return wrapMethodRefs(dead);
    }

    private ScriptValue getReachableFrom(ScriptValue entryPoints) {
        ensureBuilt();
        List<MethodReference> refs = new ArrayList<>();

        if (entryPoints.isArray()) {
            for (ScriptValue val : entryPoints.asArray()) {
                MethodReference ref = parseMethodRef(val);
                if (ref != null) refs.add(ref);
            }
        } else {
            MethodReference ref = parseMethodRef(entryPoints);
            if (ref != null) refs.add(ref);
        }

        Set<MethodReference> reachable = callGraph.getReachableFrom(refs);
        return wrapMethodRefs(reachable);
    }

    private ScriptValue getAllMethods() {
        ensureBuilt();
        List<ScriptValue> methods = new ArrayList<>();
        for (CallGraphNode node : callGraph.getPoolNodes()) {
            methods.add(wrapMethodRef(node.getReference()));
        }
        return ScriptValue.array(methods);
    }

    private ScriptValue findMethods(ScriptValue filter) {
        ensureBuilt();
        List<ScriptValue> result = new ArrayList<>();

        if (filter.isFunction()) {
            ScriptFunction fn = filter.asFunction();
            for (CallGraphNode node : callGraph.getPoolNodes()) {
                List<ScriptValue> args = new ArrayList<>();
                args.add(wrapMethodRef(node.getReference()));
                if (fn.call(interpreter, args).asBoolean()) {
                    result.add(wrapMethodRef(node.getReference()));
                }
            }
        } else if (filter.isObject()) {
            Map<String, ScriptValue> criteria = filter.asObject();
            String namePattern = criteria.containsKey("name") ? criteria.get("name").asString() : null;
            String ownerPattern = criteria.containsKey("owner") ? criteria.get("owner").asString() : null;

            for (CallGraphNode node : callGraph.getPoolNodes()) {
                MethodReference ref = node.getReference();
                boolean matches = true;

                if (namePattern != null && !matchesPattern(ref.getName(), namePattern)) {
                    matches = false;
                }
                if (ownerPattern != null && !matchesPattern(ref.getOwner(), ownerPattern)) {
                    matches = false;
                }

                if (matches) {
                    result.add(wrapMethodRef(ref));
                }
            }
        }

        return ScriptValue.array(result);
    }

    private MethodReference parseMethodRef(ScriptValue value) {
        if (value.isString()) {
            String s = value.asString();
            int dotIdx = s.lastIndexOf('.');
            if (dotIdx > 0) {
                String owner = s.substring(0, dotIdx).replace('.', '/');
                String rest = s.substring(dotIdx + 1);
                int parenIdx = rest.indexOf('(');
                if (parenIdx > 0) {
                    return new MethodReference(owner, rest.substring(0, parenIdx), rest.substring(parenIdx));
                }
                return new MethodReference(owner, rest, "");
            }
        } else if (value.isObject()) {
            Map<String, ScriptValue> obj = value.asObject();
            String owner = obj.containsKey("className") ? obj.get("className").asString() :
                          (obj.containsKey("owner") ? obj.get("owner").asString() : null);
            String name = obj.containsKey("name") ? obj.get("name").asString() : null;
            String desc = obj.containsKey("desc") ? obj.get("desc").asString() : "";

            if (owner != null && name != null) {
                return new MethodReference(owner, name, desc);
            }
        }
        return null;
    }

    private ScriptValue wrapMethodRef(MethodReference ref) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("owner", ScriptValue.string(ref.getOwner()));
        props.put("className", ScriptValue.string(ref.getOwner()));
        props.put("name", ScriptValue.string(ref.getName()));
        props.put("desc", ScriptValue.string(ref.getDescriptor()));
        props.put("fullName", ScriptValue.string(ref.getOwner() + "." + ref.getName() + ref.getDescriptor()));
        props.put("signature", ScriptValue.string(ref.getName() + ref.getDescriptor()));
        return ScriptValue.object(props);
    }

    private ScriptValue wrapMethodRefs(Set<MethodReference> refs) {
        List<ScriptValue> list = new ArrayList<>();
        for (MethodReference ref : refs) {
            list.add(wrapMethodRef(ref));
        }
        return ScriptValue.array(list);
    }

    private boolean matchesPattern(String text, String pattern) {
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            return text.contains(pattern.substring(1, pattern.length() - 1));
        } else if (pattern.startsWith("*")) {
            return text.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        } else {
            return text.equals(pattern) || text.matches(pattern);
        }
    }

    public CallGraph getCallGraph() {
        return callGraph;
    }
}
