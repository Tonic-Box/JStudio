package com.tonic.cli.repl;

import com.tonic.plugin.context.PluginContextImpl;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class REPLMode {

    private ProjectModel project;
    private PluginContextImpl context;
    private GroovyShell shell;
    private Binding binding;
    private LineReader reader;
    private boolean running;

    public REPLMode() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .build();

        reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .history(new DefaultHistory())
            .build();

        binding = new Binding();
        shell = new GroovyShell(binding);
    }

    public void printBanner() {
        System.out.println("+=============================================================+");
        System.out.println("|           JStudio Interactive REPL                          |");
        System.out.println("|   Java Bytecode Analysis and Transformation Tool            |");
        System.out.println("+=============================================================+");
        System.out.println();
        System.out.println("Type :help for available commands, :quit to exit");
        System.out.println();
    }

    public void loadTarget(File file) throws Exception {
        project = loadProjectFromFile(file);
        context = new PluginContextImpl(project, "repl");
        updateBindings();
        System.out.println("Loaded: " + file.getName() + " (" + project.getClassCount() + " classes)");
    }

    private ProjectModel loadProjectFromFile(File file) throws Exception {
        ProjectService service = ProjectService.getInstance();
        String name = file.getName().toLowerCase();

        if (file.isDirectory()) {
            return service.loadDirectory(file, null);
        } else if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return service.loadJar(file, null);
        } else if (name.endsWith(".class")) {
            return service.loadClassFile(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + name);
        }
    }

    public void executeScript(File scriptFile) throws Exception {
        String script = new String(Files.readAllBytes(scriptFile.toPath()));
        Object result = shell.evaluate(script);
        if (result != null) {
            System.out.println("=> " + result);
        }
    }

    public void run() {
        running = true;

        while (running) {
            try {
                String line = reader.readLine("jstudio> ");
                if (line == null) {
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith(":")) {
                    handleCommand(line);
                } else {
                    executeGroovy(line);
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        System.out.println("Goodbye!");
    }

    private void handleCommand(String line) throws Exception {
        String[] parts = line.substring(1).split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help":
            case "h":
                printHelp();
                break;
            case "quit":
            case "q":
            case "exit":
                running = false;
                break;
            case "load":
            case "l":
                loadTarget(new File(arg));
                break;
            case "classes":
            case "cls":
                listClasses(arg);
                break;
            case "methods":
            case "m":
                listMethods(arg);
                break;
            case "info":
            case "i":
                showInfo(arg);
                break;
            case "run":
            case "r":
                executeScript(new File(arg));
                break;
            case "stats":
                showStats();
                break;
            case "clear":
                System.out.print("\033[H\033[2J");
                System.out.flush();
                break;
            default:
                System.out.println("Unknown command: " + cmd + ". Type :help for available commands.");
        }
    }

    private void executeGroovy(String code) {
        try {
            Object result = shell.evaluate(code);
            if (result != null) {
                System.out.println("=> " + result);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :load <path>      Load JAR/class file/directory");
        System.out.println("  :classes [pat]    List classes (optional pattern filter)");
        System.out.println("  :methods <class>  List methods of a class");
        System.out.println("  :info <class>     Show class details");
        System.out.println("  :run <script>     Execute script file");
        System.out.println("  :stats            Show project statistics");
        System.out.println("  :clear            Clear screen");
        System.out.println("  :help             Show this help");
        System.out.println("  :quit             Exit REPL");
        System.out.println();
        System.out.println("Available variables:");
        System.out.println("  project   - ProjectApi for class/method access");
        System.out.println("  analysis  - AnalysisApi for call graph, patterns, etc.");
        System.out.println("  yabr      - YabrAccess for raw bytecode access");
        System.out.println("  results   - ResultCollector for findings");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  project.classes.each { println it.name }");
    }

    private void listClasses(String pattern) {
        if (project == null) {
            System.out.println("No project loaded. Use :load <path>");
            return;
        }

        for (ClassEntryModel entry : project.getAllClasses()) {
            String name = entry.getClassName().replace('/', '.');
            if (pattern.isEmpty() || name.contains(pattern)) {
                System.out.println("  " + name);
            }
        }
    }

    private void listMethods(String className) {
        if (project == null) {
            System.out.println("No project loaded. Use :load <path>");
            return;
        }

        String normalized = className.replace('.', '/');
        ClassEntryModel entry = project.findClassByName(normalized);

        if (entry == null) {
            System.out.println("Class not found: " + className);
            return;
        }

        System.out.println("Methods in " + className + ":");
        for (MethodEntryModel method : entry.getMethods()) {
            System.out.println("  " + method.getDisplaySignature());
        }
    }

    private void showInfo(String className) {
        if (project == null) {
            System.out.println("No project loaded. Use :load <path>");
            return;
        }

        String normalized = className.replace('.', '/');
        ClassEntryModel entry = project.findClassByName(normalized);

        if (entry == null) {
            System.out.println("Class not found: " + className);
            return;
        }

        System.out.println("Class: " + entry.getClassName().replace('/', '.'));
        System.out.println("Superclass: " + (entry.getSuperClassName() != null ?
            entry.getSuperClassName().replace('/', '.') : "none"));

        if (!entry.getInterfaceNames().isEmpty()) {
            System.out.println("Interfaces:");
            for (String iface : entry.getInterfaceNames()) {
                System.out.println("  " + iface.replace('/', '.'));
            }
        }

        System.out.println("Methods: " + entry.getMethods().size());
        System.out.println("Fields: " + entry.getFields().size());
    }

    private void showStats() {
        if (project == null) {
            System.out.println("No project loaded. Use :load <path>");
            return;
        }

        int methodCount = 0;
        int fieldCount = 0;
        for (ClassEntryModel entry : project.getAllClasses()) {
            methodCount += entry.getMethods().size();
            fieldCount += entry.getFields().size();
        }

        System.out.println("Project Statistics:");
        System.out.println("  Classes: " + project.getClassCount());
        System.out.println("  Methods: " + methodCount);
        System.out.println("  Fields: " + fieldCount);
        System.out.println("  Packages: " + project.getPackages().size());
    }

    private void updateBindings() {
        if (context != null) {
            binding.setVariable("project", context.getProject());
            binding.setVariable("analysis", context.getAnalysis());
            binding.setVariable("yabr", context.getYabr());
            binding.setVariable("results", context.getResults());
            binding.setVariable("log", context.getLogger());
        }
    }
}
