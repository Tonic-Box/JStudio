package com.tonic.ui.query.planner.filter;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.analysis.xref.XrefType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.ast.ArgumentType;
import com.tonic.ui.query.util.ArgumentTypeAnalyzer;

import java.util.*;
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

    private static StringBuilder lastDiagnostics = new StringBuilder();
    private static Map<String, List<com.tonic.analysis.xref.Xref>> lastXrefsByMethod = new HashMap<>();

    public static String getLastDiagnostics() {
        return lastDiagnostics.toString();
    }

    public static Map<String, List<com.tonic.analysis.xref.Xref>> getLastXrefsByMethod() {
        return Collections.unmodifiableMap(lastXrefsByMethod);
    }

    private static boolean lastWasStaticMode = false;

    public static boolean wasStaticMode() {
        return lastWasStaticMode;
    }

    public static void setStaticMode(boolean value) {
        lastWasStaticMode = value;
    }

    @Override
    public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
        lastDiagnostics = new StringBuilder();
        lastXrefsByMethod = new HashMap<>();
        lastDiagnostics.append("Mode: ").append(lastWasStaticMode ? "STATIC (no execution)" : "DYNAMIC (fuzzing)").append("\n");
        lastDiagnostics.append("hasXrefBackedFilter: ").append(lastWasStaticMode).append("\n\n");

        if (xrefDb == null) {
            lastDiagnostics.append("ERROR: XrefDB is null!\n");
            lastDiagnostics.append("This means xrefs were not built or not passed correctly.\n");
            return methods.collect(Collectors.toSet());
        }

        lastDiagnostics.append("XrefDB stats: ").append(xrefDb.getTotalXrefCount()).append(" total xrefs\n");
        lastDiagnostics.append("Looking for: owner=").append(targetOwner)
            .append(", name=").append(targetName)
            .append(", desc=").append(targetDesc).append("\n");
        if (argumentType != ArgumentType.ANY) {
            lastDiagnostics.append("Argument filter: ").append(argumentType).append("\n");
        }

        Map<String, List<com.tonic.analysis.xref.Xref>> xrefsBySignature = new HashMap<>();

        java.util.List<com.tonic.analysis.xref.Xref> refs;
        if (refType == XrefType.FIELD_READ || refType == XrefType.FIELD_WRITE) {
            refs = xrefDb.getRefsToField(targetOwner, targetName, targetDesc);
        } else if (targetDesc == null || targetDesc.isEmpty()) {
            refs = xrefDb.getRefsToMethodByName(targetOwner, targetName);
        } else {
            refs = xrefDb.getRefsToMethod(targetOwner, targetName, targetDesc);
        }

        lastDiagnostics.append("XrefDB returned ").append(refs != null ? refs.size() : 0).append(" refs\n");

        if (refs != null) {
            for (var ref : refs) {
                lastDiagnostics.append("  Ref from: ").append(ref.getSourceClass())
                    .append(".").append(ref.getSourceMethod()).append("\n");
                if (ref.getType() == refType || refType == null) {
                    String fullSig = ref.getSourceClass() + "." + ref.getSourceMethod() + ref.getSourceMethodDesc();
                    xrefsBySignature.computeIfAbsent(fullSig, k -> new ArrayList<>()).add(ref);
                }
            }
        }

        lastDiagnostics.append("Total unique caller signatures before arg filter: ")
            .append(xrefsBySignature.size()).append("\n");

        java.util.List<MethodEntry> methodList = methods.collect(Collectors.toList());
        lastDiagnostics.append("Checking against ").append(methodList.size()).append(" methods in pool\n");

        Map<String, MethodEntry> methodBySignature = new HashMap<>();
        for (MethodEntry m : methodList) {
            String sig = m.getOwnerName() + "." + m.getName() + m.getDesc();
            methodBySignature.put(sig, m);
        }

        Set<MethodEntry> result = new HashSet<>();
        Map<String, List<com.tonic.analysis.xref.Xref>> filteredXrefsByMethod = new HashMap<>();

        for (Map.Entry<String, List<com.tonic.analysis.xref.Xref>> entry : xrefsBySignature.entrySet()) {
            String sig = entry.getKey();
            MethodEntry method = methodBySignature.get(sig);
            if (method == null) {
                continue;
            }

            List<com.tonic.analysis.xref.Xref> matchingXrefs = new ArrayList<>();

            if (argumentType == ArgumentType.ANY) {
                matchingXrefs.addAll(entry.getValue());
            } else {
                for (com.tonic.analysis.xref.Xref xref : entry.getValue()) {
                    if (ArgumentTypeAnalyzer.hasArgumentOfType(method, xref, argumentType)) {
                        matchingXrefs.add(xref);
                        lastDiagnostics.append("    Xref at ").append(xref.getInstructionIndex())
                            .append(" matches arg filter ").append(argumentType).append("\n");
                    } else {
                        lastDiagnostics.append("    Xref at ").append(xref.getInstructionIndex())
                            .append(" REJECTED by arg filter ").append(argumentType).append("\n");
                    }
                }
            }

            if (!matchingXrefs.isEmpty()) {
                result.add(method);
                filteredXrefsByMethod.put(sig, matchingXrefs);
                lastDiagnostics.append("  MATCH: ").append(sig)
                    .append(" (").append(matchingXrefs.size()).append(" call sites)\n");
            }
        }

        lastXrefsByMethod = filteredXrefsByMethod;
        lastDiagnostics.append("Final result: ").append(result.size()).append(" methods\n");
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
