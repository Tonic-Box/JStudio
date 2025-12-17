![img.png](img.png)
![img_1.png](img_1.png)
A Java reverse engineering and static analysis IDE for analyzing, decompiling, and transforming Java bytecode.

Powered by [YABR](https://github.com/Tonic-Box/YABR)

## Features

### Multi-View Code Editor
- **Source View** - Decompiled Java with syntax highlighting
- **Bytecode View** - Disassembled JVM instructions with color-coded opcodes
- **IR View** - SSA-form intermediate representation
- **Hex View** - Raw class file bytes

### Analysis Tools
- **Call Graph** - Visual method call hierarchies
- **Dependencies** - Class dependency visualization
- **Cross-References** - Bidirectional symbol reference tracking
- **Data Flow** - SSA-based data flow analysis
- **Similarity** - Method duplicate detection
- **String Extraction** - Constant pool string search
- **Usages** - Find method calls, field accesses, allocations

### Code Transformation
- **SSA Transforms** - Apply static single assignment optimizations with before/after preview
- **Custom Scripting** - Built-in scripting language for AST and IR manipulation

### UI
- Tabbed editor with multiple views per class
- Navigator tree with package hierarchy
- Properties panel for class/method/field metadata
- Console with multi-level logging
- 8 built-in themes (dark and light)

## Building & Running

### Quick Start
```bash
./gradlew run              # Run the application directly
./gradlew build            # Compile + test + create fat JAR
```

### Distribution
```bash
./gradlew shadowJar        # Create fat JAR only
java -jar build/libs/JStudio.jar           # Launch GUI
java -jar build/libs/JStudio.jar --cli     # Launch CLI mode
```

### Development
```bash
./gradlew clean build      # Fresh build
./gradlew refreshDependencies build  # Force refresh SNAPSHOT deps
```

## Usage

1. **Open a project**: File → Open (Ctrl+O) to load a JAR, directory, or class file
2. **Navigate**: Use the class tree on the left to browse packages and classes
3. **View code**: Double-click a class to open it, use View menu to switch between Source/Bytecode/IR/Hex
4. **Analyze**: Use Analysis menu for call graphs, dependencies, cross-references, etc.
5. **Transform**: Use Scripting menu to open the script editor for custom transformations

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Open Project | Ctrl+O |
| Source View | F5 |
| Bytecode View | F6 |
| IR View | F7 |
| Find in Files | Ctrl+Shift+F |
| Go to Class | Ctrl+Shift+N |
| Increase Font | Ctrl++ |
| Decrease Font | Ctrl+- |

## CLI Mode

JStudio includes a headless CLI for scripted analysis and automation. Add `--cli` anywhere in arguments to enable CLI mode.

### Commands

```bash
# Show help
java -jar JStudio.jar --cli --help

# Display target information
java -jar JStudio.jar --cli info app.jar
java -jar JStudio.jar --cli info app.jar --stats
java -jar JStudio.jar --cli info app.jar -c com.example.Main --methods

# Run a plugin/script on target
java -jar JStudio.jar --cli run app.jar -p scanner.groovy
java -jar JStudio.jar --cli run app.jar -p plugin.jar -o results.json -f json

# Interactive REPL mode
java -jar JStudio.jar --cli repl app.jar

# Batch process multiple targets
java -jar JStudio.jar --cli batch *.jar -p analyzer.groovy --parallel
```

### Info Command Options

| Option | Description |
|--------|-------------|
| `-c, --class <name>` | Show details for specific class |
| `-m, --methods` | List methods |
| `-f, --fields` | List fields |
| `--stats` | Show statistics only |
| `--json` | Output as JSON |

### Run Command Options

| Option | Description |
|--------|-------------|
| `-p, --plugin <file>` | Plugin/script file (.groovy, .jar) |
| `-d, --plugin-dir <dir>` | Directory containing plugins |
| `-o, --output <file>` | Output file |
| `-f, --format <fmt>` | Output format: text, json, csv |
| `-c, --class <pattern>` | Target specific class |
| `-m, --method <pattern>` | Target specific method |
| `--dry-run` | Validate without executing |

### REPL Commands

| Command | Description |
|---------|-------------|
| `:load <path>` | Load JAR/class file/directory |
| `:classes [pattern]` | List classes (optional filter) |
| `:methods <class>` | List methods of a class |
| `:info <class>` | Show class details |
| `:run <script>` | Execute script file |
| `:stats` | Show project statistics |
| `:clear` | Clear screen |
| `:help` | Show help |
| `:quit` | Exit REPL |

### REPL Scripting

The REPL provides Groovy scripting with these variables:

| Variable | Description |
|----------|-------------|
| `project` | ProjectApi for class/method access |
| `analysis` | AnalysisApi for call graph, patterns |
| `yabr` | YabrAccess for raw bytecode access |
| `results` | ResultCollector for findings |
| `log` | Logger for output |

Example:
```groovy
project.classes.each { println it.name }
```

## Dependencies

- [FlatLaf](https://www.formdev.com/flatlaf/) - Modern Swing look and feel
- [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) - Syntax highlighting
- [JGraphX](https://github.com/jgraph/jgraphx) - Graph visualization
- [JavaParser](https://javaparser.org/) - Java parsing
- [YABR](https://github.com/Tonic-Box/YABR) - Bytecode analysis framework
- [picocli](https://picocli.info/) - CLI framework
- [JLine](https://github.com/jline/jline3) - Terminal handling for REPL
- [Groovy](https://groovy-lang.org/) - Scripting language support

## License

MIT
