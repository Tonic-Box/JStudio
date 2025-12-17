package com.tonic.cli.commands;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "info",
    description = "Display information about target files",
    mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target JAR file, class file, or directory")
    private File target;

    @Option(names = {"-c", "--class"}, description = "Show details for specific class")
    private String className;

    @Option(names = {"-m", "--methods"}, description = "List methods")
    private boolean showMethods;

    @Option(names = {"-f", "--fields"}, description = "List fields")
    private boolean showFields;

    @Option(names = {"--stats"}, description = "Show statistics only")
    private boolean statsOnly;

    @Option(names = {"--json"}, description = "Output as JSON")
    private boolean json;

    @Override
    public Integer call() {
        if (target == null || !target.exists()) {
            System.err.println("Error: Target not found: " + target);
            return 1;
        }

        try {
            ProjectModel project = loadProject(target);

            if (statsOnly) {
                printStats(project);
            } else if (className != null) {
                printClassInfo(project, className);
            } else {
                printOverview(project);
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private ProjectModel loadProject(File target) throws Exception {
        ProjectService service = ProjectService.getInstance();
        String name = target.getName().toLowerCase();

        if (target.isDirectory()) {
            return service.loadDirectory(target, null);
        } else if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return service.loadJar(target, null);
        } else if (name.endsWith(".class")) {
            return service.loadClassFile(target);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + name);
        }
    }

    private void printStats(ProjectModel project) {
        int classCount = project.getClassCount();
        int methodCount = 0;
        int fieldCount = 0;

        for (ClassEntryModel entry : project.getAllClasses()) {
            methodCount += entry.getMethods().size();
            fieldCount += entry.getFields().size();
        }

        if (json) {
            System.out.println("{");
            System.out.println("  \"classes\": " + classCount + ",");
            System.out.println("  \"methods\": " + methodCount + ",");
            System.out.println("  \"fields\": " + fieldCount);
            System.out.println("}");
        } else {
            System.out.println("Statistics:");
            System.out.println("  Classes: " + classCount);
            System.out.println("  Methods: " + methodCount);
            System.out.println("  Fields:  " + fieldCount);
        }
    }

    private void printOverview(ProjectModel project) {
        System.out.println("Target: " + target.getName());
        System.out.println("Classes: " + project.getClassCount());
        System.out.println();
        System.out.println("Packages:");

        for (String pkg : project.getPackages()) {
            int count = project.getClassesInPackage(pkg).size();
            System.out.println("  " + (pkg.isEmpty() ? "(default)" : pkg.replace('/', '.')) + " (" + count + " classes)");
        }
    }

    private void printClassInfo(ProjectModel project, String name) {
        String normalized = name.replace('.', '/');
        ClassEntryModel entry = project.findClassByName(normalized);

        if (entry == null) {
            System.err.println("Class not found: " + name);
            return;
        }

        ClassFile cf = entry.getClassFile();

        System.out.println("Class: " + entry.getClassName().replace('/', '.'));
        System.out.println("Access: " + formatAccess(entry.getAccessFlags()));

        if (entry.getSuperClassName() != null && !entry.getSuperClassName().equals("java/lang/Object")) {
            System.out.println("Extends: " + entry.getSuperClassName().replace('/', '.'));
        }

        if (!entry.getInterfaceNames().isEmpty()) {
            System.out.println("Implements:");
            for (String iface : entry.getInterfaceNames()) {
                System.out.println("  " + iface.replace('/', '.'));
            }
        }

        if (showFields || (!showMethods)) {
            System.out.println();
            System.out.println("Fields (" + entry.getFields().size() + "):");
            for (var field : entry.getFields()) {
                System.out.println("  " + formatField(field));
            }
        }

        if (showMethods || (!showFields)) {
            System.out.println();
            System.out.println("Methods (" + entry.getMethods().size() + "):");
            for (var method : entry.getMethods()) {
                System.out.println("  " + formatMethod(method));
            }
        }
    }

    private String formatAccess(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & 0x0001) != 0) sb.append("public ");
        if ((flags & 0x0002) != 0) sb.append("private ");
        if ((flags & 0x0004) != 0) sb.append("protected ");
        if ((flags & 0x0008) != 0) sb.append("static ");
        if ((flags & 0x0010) != 0) sb.append("final ");
        if ((flags & 0x0400) != 0) sb.append("abstract ");
        return sb.toString().trim();
    }

    private String formatField(com.tonic.ui.model.FieldEntryModel field) {
        return formatAccess(field.getAccessFlags()) + " " +
               formatType(field.getDescriptor()) + " " + field.getName();
    }

    private String formatMethod(com.tonic.ui.model.MethodEntryModel method) {
        return formatAccess(method.getAccessFlags()) + " " + method.getDisplaySignature();
    }

    private String formatType(String desc) {
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1).replace('/', '.');
        }
        switch (desc) {
            case "I": return "int";
            case "J": return "long";
            case "Z": return "boolean";
            case "B": return "byte";
            case "C": return "char";
            case "S": return "short";
            case "F": return "float";
            case "D": return "double";
            case "V": return "void";
            default:
                if (desc.startsWith("[")) return formatType(desc.substring(1)) + "[]";
                return desc;
        }
    }
}
