package com.tonic.ui.query.planner.filter;

import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.analysis.xref.XrefType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.ast.ArgumentType;
import com.tonic.ui.query.util.ArgumentTypeAnalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter methods that reference a specific target via xrefs.
 */
public class XrefMethodFilter implements StaticFilter {

    private final XrefDatabase xrefDb;
    private final String targetOwner;
    private final String targetName;
    private final String targetDesc;
    private final XrefType refType;
    private final ArgumentType argumentType;

    private final StringBuilder diagnosticsLog = new StringBuilder();
    private final Map<String, List<Xref>> xrefsByMethod = new HashMap<>();

    private static final ThreadLocal<XrefMethodFilter> lastFilter = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> staticMode = ThreadLocal.withInitial(() -> false);

    public XrefMethodFilter(XrefDatabase xrefDb, String targetOwner, String targetName,
                            String targetDesc, XrefType refType) {
        this(xrefDb, targetOwner, targetName, targetDesc, refType, ArgumentType.ANY);
    }

    public XrefMethodFilter(XrefDatabase xrefDb, String targetOwner, String targetName,
                            String targetDesc, XrefType refType, ArgumentType argumentType) {
        this.xrefDb = xrefDb;
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.targetDesc = targetDesc;
        this.refType = refType;
        this.argumentType = argumentType != null ? argumentType : ArgumentType.ANY;
    }

    public static XrefMethodFilter callsMethod(XrefDatabase xrefDb, String owner, String name, String desc) {
        return new XrefMethodFilter(xrefDb, owner, name, desc, XrefType.METHOD_CALL, ArgumentType.ANY);
    }

    public static XrefMethodFilter callsMethod(XrefDatabase xrefDb, String owner, String name, String desc,
                                               ArgumentType argumentType) {
        return new XrefMethodFilter(xrefDb, owner, name, desc, XrefType.METHOD_CALL, argumentType);
    }

    public static XrefMethodFilter readsField(XrefDatabase xrefDb, String owner, String name, String desc) {
        return new XrefMethodFilter(xrefDb, owner, name, desc, XrefType.FIELD_READ);
    }

    public static XrefMethodFilter writesField(XrefDatabase xrefDb, String owner, String name, String desc) {
        return new XrefMethodFilter(xrefDb, owner, name, desc, XrefType.FIELD_WRITE);
    }

    public String getDiagnostics() {
        return diagnosticsLog.toString();
    }

    public Map<String, List<Xref>> getXrefsByMethod() {
        return Collections.unmodifiableMap(xrefsByMethod);
    }

    public static String getLastDiagnostics() {
        XrefMethodFilter filter = lastFilter.get();
        return filter != null ? filter.getDiagnostics() : "";
    }

    public static Map<String, List<Xref>> getLastXrefsByMethod() {
        XrefMethodFilter filter = lastFilter.get();
        return filter != null ? filter.getXrefsByMethod() : Collections.emptyMap();
    }

    public static boolean wasStaticMode() {
        return staticMode.get();
    }

    public static void setStaticMode(boolean value) {
        staticMode.set(value);
    }

    public static void clearThreadLocal() {
        lastFilter.remove();
        staticMode.remove();
    }

    @Override
    public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
        lastFilter.set(this);
        diagnosticsLog.setLength(0);
        xrefsByMethod.clear();

        boolean isStaticMode = staticMode.get();
        diagnosticsLog.append("Mode: ").append(isStaticMode ? "STATIC (no execution)" : "DYNAMIC (fuzzing)").append("\n");
        diagnosticsLog.append("hasXrefBackedFilter: ").append(isStaticMode).append("\n\n");

        if (xrefDb == null) {
            diagnosticsLog.append("ERROR: XrefDB is null!\n");
            diagnosticsLog.append("This means xrefs were not built or not passed correctly.\n");
            return methods.collect(Collectors.toSet());
        }

        diagnosticsLog.append("XrefDB stats: ").append(xrefDb.getTotalXrefCount()).append(" total xrefs\n");
        diagnosticsLog.append("Looking for: owner=").append(targetOwner)
            .append(", name=").append(targetName)
            .append(", desc=").append(targetDesc).append("\n");
        if (argumentType != ArgumentType.ANY) {
            diagnosticsLog.append("Argument filter: ").append(argumentType).append("\n");
        }

        Map<String, List<Xref>> xrefsBySignature = new HashMap<>();

        List<Xref> refs;
        if (refType == XrefType.FIELD_READ || refType == XrefType.FIELD_WRITE) {
            refs = xrefDb.getRefsToField(targetOwner, targetName, targetDesc);
        } else if (targetDesc == null || targetDesc.isEmpty()) {
            refs = xrefDb.getRefsToMethodByName(targetOwner, targetName);
        } else {
            refs = xrefDb.getRefsToMethod(targetOwner, targetName, targetDesc);
        }

        diagnosticsLog.append("XrefDB returned ").append(refs != null ? refs.size() : 0).append(" refs\n");

        if (refs != null) {
            for (var ref : refs) {
                diagnosticsLog.append("  Ref from: ").append(ref.getSourceClass())
                    .append(".").append(ref.getSourceMethod()).append("\n");
                if (ref.getType() == refType || refType == null) {
                    String fullSig = ref.getSourceClass() + "." + ref.getSourceMethod() + ref.getSourceMethodDesc();
                    xrefsBySignature.computeIfAbsent(fullSig, k -> new ArrayList<>()).add(ref);
                }
            }
        }

        diagnosticsLog.append("Total unique caller signatures before arg filter: ")
            .append(xrefsBySignature.size()).append("\n");

        List<MethodEntry> methodList = methods.collect(Collectors.toList());
        diagnosticsLog.append("Checking against ").append(methodList.size()).append(" methods in pool\n");

        Map<String, MethodEntry> methodBySignature = new HashMap<>();
        for (MethodEntry m : methodList) {
            String sig = m.getOwnerName() + "." + m.getName() + m.getDesc();
            methodBySignature.put(sig, m);
        }

        Set<MethodEntry> result = new HashSet<>();

        for (Map.Entry<String, List<Xref>> entry : xrefsBySignature.entrySet()) {
            String sig = entry.getKey();
            MethodEntry method = methodBySignature.get(sig);
            if (method == null) {
                continue;
            }

            List<Xref> matchingXrefs = new ArrayList<>();

            if (argumentType == ArgumentType.ANY) {
                matchingXrefs.addAll(entry.getValue());
            } else {
                for (Xref xref : entry.getValue()) {
                    if (ArgumentTypeAnalyzer.hasArgumentOfType(method, xref, argumentType)) {
                        matchingXrefs.add(xref);
                        diagnosticsLog.append("    Xref at ").append(xref.getInstructionIndex())
                            .append(" matches arg filter ").append(argumentType).append("\n");
                    } else {
                        diagnosticsLog.append("    Xref at ").append(xref.getInstructionIndex())
                            .append(" REJECTED by arg filter ").append(argumentType).append("\n");
                    }
                }
            }

            if (!matchingXrefs.isEmpty()) {
                result.add(method);
                xrefsByMethod.put(sig, matchingXrefs);
                diagnosticsLog.append("  MATCH: ").append(sig)
                    .append(" (").append(matchingXrefs.size()).append(" call sites)\n");
            }
        }

        diagnosticsLog.append("Final result: ").append(result.size()).append(" methods\n");
        return result;
    }

    @Override
    public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
        Set<MethodEntry> matchingMethods = filterMethods(
            classes.flatMap(cf -> cf.getMethods().stream())
        );

        return matchingMethods.stream()
            .map(MethodEntry::getClassFile)
            .collect(Collectors.toSet());
    }
}
