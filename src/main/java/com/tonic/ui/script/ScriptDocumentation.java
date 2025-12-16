package com.tonic.ui.script;

import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import java.awt.Color;

public class ScriptDocumentation {

    private ScriptDocumentation() {
    }

    public static String getStylesheet() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        String bg = colorToHex(theme.getBgPrimary());
        String bgSecondary = colorToHex(theme.getBgSecondary());
        String text = colorToHex(theme.getTextPrimary());
        String textSecondary = colorToHex(theme.getTextSecondary());
        String accent = colorToHex(theme.getAccent());
        String keyword = colorToHex(theme.getJavaKeyword());
        String string = colorToHex(theme.getJavaString());
        String comment = colorToHex(theme.getJavaComment());
        String number = colorToHex(theme.getJavaNumber());
        String method = colorToHex(theme.getJavaMethod());

        return "<style>\n" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; color: " + text + "; background: " + bg + "; padding: 15px; margin: 0; line-height: 1.6; }\n" +
                "h1 { color: " + accent + "; font-size: 22px; margin-top: 0; border-bottom: 2px solid " + accent + "; padding-bottom: 8px; }\n" +
                "h2 { color: " + accent + "; font-size: 18px; margin-top: 20px; }\n" +
                "h3 { color: " + textSecondary + "; font-size: 15px; margin-top: 16px; }\n" +
                "p { margin: 8px 0; }\n" +
                "code { font-family: 'JetBrains Mono', 'Consolas', monospace; background: " + bgSecondary + "; padding: 2px 6px; border-radius: 3px; font-size: 13px; }\n" +
                "pre { font-family: 'JetBrains Mono', 'Consolas', monospace; background: " + bgSecondary + "; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 13px; line-height: 1.5; }\n" +
                ".kw { color: " + keyword + "; font-weight: bold; }\n" +
                ".str { color: " + string + "; }\n" +
                ".cmt { color: " + comment + "; font-style: italic; }\n" +
                ".num { color: " + number + "; }\n" +
                ".fn { color: " + method + "; }\n" +
                ".note { background: " + bgSecondary + "; border-left: 3px solid " + accent + "; padding: 10px 15px; margin: 12px 0; }\n" +
                "table { border-collapse: collapse; width: 100%; margin: 12px 0; }\n" +
                "th, td { text-align: left; padding: 8px 12px; border: 1px solid " + textSecondary + "; }\n" +
                "th { background: " + bgSecondary + "; }\n" +
                "ul { margin: 8px 0; padding-left: 24px; }\n" +
                "li { margin: 4px 0; }\n" +
                "</style>\n";
    }

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static String getOverview() {
        return getStylesheet() +
                "<h1>JStudio Script Language</h1>\n" +
                "<p>JStudio includes a lightweight scripting language for analyzing and transforming Java bytecode. " +
                "Scripts can operate on multiple levels:</p>\n" +
                "<ul>\n" +
                "<li><b>AST Mode</b> - Work with high-level Abstract Syntax Tree nodes (method calls, field access, expressions)</li>\n" +
                "<li><b>IR Mode</b> - Work with low-level Intermediate Representation (individual instructions, basic blocks)</li>\n" +
                "<li><b>Annotation API</b> - Analyze and remove Java annotations at the bytecode level (works with both modes)</li>\n" +
                "</ul>\n" +
                "<p>The language uses JavaScript-like syntax with some differences. Select a topic from the navigation " +
                "panel to learn more.</p>\n" +
                "<div class='note'>\n" +
                "<b>Quick Start:</b> Try the example scripts in the Script Library panel to see the language in action.\n" +
                "</div>\n";
    }

    public static String getVariables() {
        return getStylesheet() +
                "<h1>Variables</h1>\n" +
                "<p>Declare variables using <code>let</code> for mutable values or <code>const</code> for constants.</p>\n" +
                "<h2>Mutable Variables (let)</h2>\n" +
                "<pre><span class='kw'>let</span> x = <span class='num'>10</span>;\n" +
                "x = <span class='num'>20</span>;  <span class='cmt'>// OK - can reassign</span>\n" +
                "\n" +
                "<span class='kw'>let</span> name = <span class='str'>\"Hello\"</span>;\n" +
                "name = <span class='str'>\"World\"</span>;  <span class='cmt'>// OK</span></pre>\n" +
                "<h2>Constants (const)</h2>\n" +
                "<pre><span class='kw'>const</span> PI = <span class='num'>3.14159</span>;\n" +
                "PI = <span class='num'>3</span>;  <span class='cmt'>// ERROR! Cannot reassign const</span>\n" +
                "\n" +
                "<span class='kw'>const</span> CONFIG = { debug: <span class='kw'>true</span> };\n" +
                "CONFIG.debug = <span class='kw'>false</span>;  <span class='cmt'>// OK - object properties can change</span></pre>\n" +
                "<div class='note'>\n" +
                "<b>Note:</b> <code>const</code> prevents reassignment of the variable itself, but object/array contents can still be modified.\n" +
                "</div>\n" +
                "<h2>Scope</h2>\n" +
                "<p>Variables are block-scoped. A variable declared inside a block <code>{ }</code> is not accessible outside it.</p>\n" +
                "<pre><span class='kw'>let</span> x = <span class='num'>1</span>;\n" +
                "<span class='kw'>if</span> (<span class='kw'>true</span>) {\n" +
                "    <span class='kw'>let</span> y = <span class='num'>2</span>;\n" +
                "    <span class='fn'>log</span>(x);  <span class='cmt'>// OK - x is accessible</span>\n" +
                "}\n" +
                "<span class='fn'>log</span>(y);  <span class='cmt'>// ERROR - y is not defined here</span></pre>\n";
    }

    public static String getDataTypes() {
        return getStylesheet() +
                "<h1>Data Types</h1>\n" +
                "<p>The scripting language supports these data types:</p>\n" +
                "<table>\n" +
                "<tr><th>Type</th><th>Example</th><th>Description</th></tr>\n" +
                "<tr><td><code>null</code></td><td><code>null</code></td><td>Represents no value</td></tr>\n" +
                "<tr><td><code>boolean</code></td><td><code>true</code>, <code>false</code></td><td>Logical values</td></tr>\n" +
                "<tr><td><code>number</code></td><td><code>42</code>, <code>3.14</code></td><td>Integer or floating-point</td></tr>\n" +
                "<tr><td><code>string</code></td><td><code>\"hello\"</code></td><td>Text values</td></tr>\n" +
                "<tr><td><code>function</code></td><td><code>(x) => x * 2</code></td><td>Callable functions</td></tr>\n" +
                "<tr><td><code>object</code></td><td><code>{ key: value }</code></td><td>Key-value pairs</td></tr>\n" +
                "<tr><td><code>array</code></td><td><code>[1, 2, 3]</code></td><td>Ordered collections</td></tr>\n" +
                "</table>\n" +
                "<h2>Type Checking</h2>\n" +
                "<pre><span class='fn'>typeof</span>(<span class='num'>42</span>)        <span class='cmt'>// \"number\"</span>\n" +
                "<span class='fn'>typeof</span>(<span class='str'>\"hello\"</span>)   <span class='cmt'>// \"string\"</span>\n" +
                "<span class='fn'>typeof</span>(<span class='kw'>true</span>)      <span class='cmt'>// \"boolean\"</span>\n" +
                "<span class='fn'>typeof</span>(<span class='kw'>null</span>)      <span class='cmt'>// \"null\"</span>\n" +
                "<span class='fn'>typeof</span>([<span class='num'>1</span>,<span class='num'>2</span>])     <span class='cmt'>// \"array\"</span>\n" +
                "<span class='fn'>typeof</span>({a:<span class='num'>1</span>})     <span class='cmt'>// \"object\"</span></pre>\n" +
                "<h2>Truthiness</h2>\n" +
                "<p>These values are considered <b>falsy</b>:</p>\n" +
                "<ul>\n" +
                "<li><code>false</code></li>\n" +
                "<li><code>null</code></li>\n" +
                "<li><code>0</code></li>\n" +
                "<li><code>\"\"</code> (empty string)</li>\n" +
                "</ul>\n" +
                "<p>Everything else is <b>truthy</b>.</p>\n";
    }

    public static String getOperators() {
        return getStylesheet() +
                "<h1>Operators</h1>\n" +
                "<h2>Arithmetic</h2>\n" +
                "<table>\n" +
                "<tr><th>Operator</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>+</code></td><td>Addition / Concatenation</td><td><code>5 + 3</code> = 8</td></tr>\n" +
                "<tr><td><code>-</code></td><td>Subtraction</td><td><code>5 - 3</code> = 2</td></tr>\n" +
                "<tr><td><code>*</code></td><td>Multiplication</td><td><code>5 * 3</code> = 15</td></tr>\n" +
                "<tr><td><code>/</code></td><td>Division</td><td><code>6 / 2</code> = 3</td></tr>\n" +
                "<tr><td><code>%</code></td><td>Modulo (remainder)</td><td><code>7 % 3</code> = 1</td></tr>\n" +
                "</table>\n" +
                "<h2>Comparison</h2>\n" +
                "<table>\n" +
                "<tr><th>Operator</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>==</code></td><td>Equal</td><td><code>5 == 5</code> = true</td></tr>\n" +
                "<tr><td><code>!=</code></td><td>Not equal</td><td><code>5 != 3</code> = true</td></tr>\n" +
                "<tr><td><code>&lt;</code></td><td>Less than</td><td><code>3 &lt; 5</code> = true</td></tr>\n" +
                "<tr><td><code>&gt;</code></td><td>Greater than</td><td><code>5 &gt; 3</code> = true</td></tr>\n" +
                "<tr><td><code>&lt;=</code></td><td>Less or equal</td><td><code>3 &lt;= 3</code> = true</td></tr>\n" +
                "<tr><td><code>&gt;=</code></td><td>Greater or equal</td><td><code>5 &gt;= 5</code> = true</td></tr>\n" +
                "</table>\n" +
                "<h2>Logical</h2>\n" +
                "<table>\n" +
                "<tr><th>Operator</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>&&</code></td><td>Logical AND</td><td><code>true && false</code> = false</td></tr>\n" +
                "<tr><td><code>||</code></td><td>Logical OR</td><td><code>true || false</code> = true</td></tr>\n" +
                "<tr><td><code>!</code></td><td>Logical NOT</td><td><code>!true</code> = false</td></tr>\n" +
                "</table>\n" +
                "<h2>Ternary Operator</h2>\n" +
                "<pre><span class='kw'>let</span> result = condition ? valueIfTrue : valueIfFalse;\n" +
                "\n" +
                "<span class='kw'>let</span> status = count > <span class='num'>0</span> ? <span class='str'>\"has items\"</span> : <span class='str'>\"empty\"</span>;</pre>\n" +
                "<h2>Optional Chaining</h2>\n" +
                "<p>Use <code>?.</code> to safely access properties that might not exist:</p>\n" +
                "<pre><span class='kw'>let</span> name = node?.target?.name;  <span class='cmt'>// Returns null if any part is null</span></pre>\n";
    }

    public static String getComments() {
        return getStylesheet() +
                "<h1>Comments</h1>\n" +
                "<h2>Single-Line Comments</h2>\n" +
                "<pre><span class='cmt'>// This is a single-line comment</span>\n" +
                "<span class='kw'>let</span> x = <span class='num'>10</span>;  <span class='cmt'>// Comment at end of line</span></pre>\n" +
                "<h2>Multi-Line Comments</h2>\n" +
                "<pre><span class='cmt'>/*\n" +
                "  This is a multi-line comment.\n" +
                "  It can span multiple lines.\n" +
                "*/</span>\n" +
                "<span class='kw'>let</span> y = <span class='num'>20</span>;</pre>\n";
    }

    public static String getControlFlow() {
        return getStylesheet() +
                "<h1>Control Flow</h1>\n" +
                "<h2>If / Else</h2>\n" +
                "<pre><span class='kw'>if</span> (condition) {\n" +
                "    <span class='cmt'>// executed if condition is true</span>\n" +
                "}\n" +
                "\n" +
                "<span class='kw'>if</span> (x > <span class='num'>10</span>) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"big\"</span>);\n" +
                "} <span class='kw'>else if</span> (x > <span class='num'>5</span>) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"medium\"</span>);\n" +
                "} <span class='kw'>else</span> {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"small\"</span>);\n" +
                "}</pre>\n" +
                "<h2>Blocks</h2>\n" +
                "<p>Use curly braces to group multiple statements:</p>\n" +
                "<pre><span class='kw'>if</span> (condition) {\n" +
                "    <span class='kw'>let</span> temp = calculate();\n" +
                "    process(temp);\n" +
                "    <span class='fn'>log</span>(temp);\n" +
                "}</pre>\n";
    }

    public static String getFunctions() {
        return getStylesheet() +
                "<h1>Functions</h1>\n" +
                "<p>Functions are defined using arrow syntax:</p>\n" +
                "<h2>Basic Syntax</h2>\n" +
                "<pre><span class='cmt'>// No parameters</span>\n" +
                "<span class='kw'>let</span> greet = () => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Hello!\"</span>);\n" +
                "};\n" +
                "\n" +
                "<span class='cmt'>// Single parameter (parentheses optional)</span>\n" +
                "<span class='kw'>let</span> double = x => x * <span class='num'>2</span>;\n" +
                "<span class='kw'>let</span> double2 = (x) => x * <span class='num'>2</span>;\n" +
                "\n" +
                "<span class='cmt'>// Multiple parameters</span>\n" +
                "<span class='kw'>let</span> add = (a, b) => a + b;\n" +
                "\n" +
                "<span class='cmt'>// Multi-line function body</span>\n" +
                "<span class='kw'>let</span> process = (x) => {\n" +
                "    <span class='kw'>let</span> result = x * <span class='num'>2</span>;\n" +
                "    <span class='fn'>log</span>(result);\n" +
                "    <span class='kw'>return</span> result;\n" +
                "};</pre>\n" +
                "<h2>Calling Functions</h2>\n" +
                "<pre>greet();           <span class='cmt'>// \"Hello!\"</span>\n" +
                "double(<span class='num'>5</span>);         <span class='cmt'>// 10</span>\n" +
                "add(<span class='num'>3</span>, <span class='num'>4</span>);        <span class='cmt'>// 7</span>\n" +
                "process(<span class='num'>10</span>);       <span class='cmt'>// logs 20, returns 20</span></pre>\n" +
                "<h2>Closures</h2>\n" +
                "<p>Functions can access variables from their enclosing scope:</p>\n" +
                "<pre><span class='kw'>let</span> counter = <span class='num'>0</span>;\n" +
                "<span class='kw'>let</span> increment = () => {\n" +
                "    counter = counter + <span class='num'>1</span>;\n" +
                "    <span class='kw'>return</span> counter;\n" +
                "};\n" +
                "increment();  <span class='cmt'>// 1</span>\n" +
                "increment();  <span class='cmt'>// 2</span></pre>\n";
    }

    public static String getBuiltinFunctions() {
        return getStylesheet() +
                "<h1>Built-in Functions</h1>\n" +
                "<h2>Logging</h2>\n" +
                "<table>\n" +
                "<tr><th>Function</th><th>Description</th></tr>\n" +
                "<tr><td><code>log(value)</code></td><td>Print value to console</td></tr>\n" +
                "<tr><td><code>warn(value)</code></td><td>Print warning (yellow)</td></tr>\n" +
                "<tr><td><code>error(value)</code></td><td>Print error (red)</td></tr>\n" +
                "</table>\n" +
                "<pre><span class='fn'>log</span>(<span class='str'>\"Processing method: \"</span> + context.methodName);\n" +
                "<span class='fn'>warn</span>(<span class='str'>\"Deprecated API usage detected\"</span>);\n" +
                "<span class='fn'>error</span>(<span class='str'>\"Invalid bytecode sequence\"</span>);</pre>\n" +
                "<h2>Type Functions</h2>\n" +
                "<table>\n" +
                "<tr><th>Function</th><th>Description</th></tr>\n" +
                "<tr><td><code>typeof(value)</code></td><td>Returns type as string</td></tr>\n" +
                "<tr><td><code>parseInt(str)</code></td><td>Parse string to integer</td></tr>\n" +
                "<tr><td><code>parseFloat(str)</code></td><td>Parse string to float</td></tr>\n" +
                "</table>\n" +
                "<pre><span class='kw'>let</span> type = <span class='fn'>typeof</span>(value);\n" +
                "<span class='kw'>let</span> num = <span class='fn'>parseInt</span>(<span class='str'>\"42\"</span>);    <span class='cmt'>// 42</span>\n" +
                "<span class='kw'>let</span> dec = <span class='fn'>parseFloat</span>(<span class='str'>\"3.14\"</span>); <span class='cmt'>// 3.14</span></pre>\n";
    }

    public static String getStringMethods() {
        return getStylesheet() +
                "<h1>String Methods</h1>\n" +
                "<p>Strings have these built-in methods and properties:</p>\n" +
                "<h2>Properties</h2>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>.length</code></td><td>Number of characters</td><td><code>\"hello\".length</code> = 5</td></tr>\n" +
                "</table>\n" +
                "<h2>Case Methods</h2>\n" +
                "<table>\n" +
                "<tr><th>Method</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>.toLowerCase()</code></td><td>Convert to lowercase</td><td><code>\"HELLO\".toLowerCase()</code> = \"hello\"</td></tr>\n" +
                "<tr><td><code>.toUpperCase()</code></td><td>Convert to uppercase</td><td><code>\"hello\".toUpperCase()</code> = \"HELLO\"</td></tr>\n" +
                "</table>\n" +
                "<h2>Search Methods</h2>\n" +
                "<table>\n" +
                "<tr><th>Method</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>.startsWith(str)</code></td><td>Check if starts with string</td><td><code>\"hello\".startsWith(\"he\")</code> = true</td></tr>\n" +
                "<tr><td><code>.endsWith(str)</code></td><td>Check if ends with string</td><td><code>\"hello\".endsWith(\"lo\")</code> = true</td></tr>\n" +
                "<tr><td><code>.includes(str)</code></td><td>Check if contains string</td><td><code>\"hello\".includes(\"ll\")</code> = true</td></tr>\n" +
                "<tr><td><code>.indexOf(str)</code></td><td>Find position of substring</td><td><code>\"hello\".indexOf(\"l\")</code> = 2</td></tr>\n" +
                "</table>\n" +
                "<h2>Manipulation Methods</h2>\n" +
                "<table>\n" +
                "<tr><th>Method</th><th>Description</th><th>Example</th></tr>\n" +
                "<tr><td><code>.trim()</code></td><td>Remove whitespace from ends</td><td><code>\" hi \".trim()</code> = \"hi\"</td></tr>\n" +
                "<tr><td><code>.substring(start, end)</code></td><td>Extract portion of string</td><td><code>\"hello\".substring(1,4)</code> = \"ell\"</td></tr>\n" +
                "<tr><td><code>.replace(old, new)</code></td><td>Replace first occurrence</td><td><code>\"hello\".replace(\"l\",\"L\")</code> = \"heLlo\"</td></tr>\n" +
                "<tr><td><code>.split(sep)</code></td><td>Split into array</td><td><code>\"a,b,c\".split(\",\")</code> = [\"a\",\"b\",\"c\"]</td></tr>\n" +
                "</table>\n" +
                "<h2>Example Usage</h2>\n" +
                "<pre><span class='kw'>let</span> name = context.methodName;\n" +
                "\n" +
                "<span class='kw'>if</span> (name.<span class='fn'>startsWith</span>(<span class='str'>\"get\"</span>)) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Found getter: \"</span> + name);\n" +
                "}\n" +
                "\n" +
                "<span class='kw'>if</span> (name.<span class='fn'>includes</span>(<span class='str'>\"Debug\"</span>)) {\n" +
                "    <span class='fn'>warn</span>(<span class='str'>\"Debug method found\"</span>);\n" +
                "}</pre>\n";
    }

    public static String getContextObject() {
        return getStylesheet() +
                "<h1>Context Object</h1>\n" +
                "<p>The <code>context</code> object provides information about the current method being processed:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>context.className</code></td><td>string</td><td>Full class name (e.g., \"com/example/MyClass\")</td></tr>\n" +
                "<tr><td><code>context.simpleClassName</code></td><td>string</td><td>Class name without package (e.g., \"MyClass\")</td></tr>\n" +
                "<tr><td><code>context.packageName</code></td><td>string</td><td>Package name (e.g., \"com/example\")</td></tr>\n" +
                "<tr><td><code>context.methodName</code></td><td>string</td><td>Current method name (e.g., \"processData\")</td></tr>\n" +
                "<tr><td><code>context.methodDescriptor</code></td><td>string</td><td>Method signature (e.g., \"(ILjava/lang/String;)V\")</td></tr>\n" +
                "</table>\n" +
                "<h2>Example Usage</h2>\n" +
                "<pre><span class='cmt'>// Log all methods in a specific package</span>\n" +
                "<span class='kw'>if</span> (context.packageName.<span class='fn'>startsWith</span>(<span class='str'>\"com/myapp\"</span>)) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Processing: \"</span> + context.simpleClassName + <span class='str'>\".\"</span> + context.methodName);\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Skip constructors</span>\n" +
                "<span class='kw'>if</span> (context.methodName == <span class='str'>\"&lt;init&gt;\"</span>) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Skipping constructor\"</span>);\n" +
                "}</pre>\n";
    }

    public static String getAnnotationApiOverview() {
        return getStylesheet() +
                "<h1>Annotation API</h1>\n" +
                "<p>The Annotation API lets you analyze and remove Java annotations at the bytecode level. " +
                "This works independently of AST/IR modes and operates on RuntimeVisibleAnnotations.</p>\n" +
                "<h2>How It Works</h2>\n" +
                "<ol>\n" +
                "<li>Register handlers for class, method, or field annotations</li>\n" +
                "<li>Each handler receives an annotation object with type and values</li>\n" +
                "<li>Return <code>null</code> to remove the annotation, or the annotation to keep it</li>\n" +
                "</ol>\n" +
                "<h2>Available Handlers</h2>\n" +
                "<table>\n" +
                "<tr><th>Handler</th><th>Triggered By</th></tr>\n" +
                "<tr><td><code>annotations.onClassAnnotation(fn)</code></td><td>Annotations on the class itself</td></tr>\n" +
                "<tr><td><code>annotations.onMethodAnnotation(fn)</code></td><td>Annotations on methods</td></tr>\n" +
                "<tr><td><code>annotations.onFieldAnnotation(fn)</code></td><td>Annotations on fields</td></tr>\n" +
                "</table>\n" +
                "<div class='note'>\n" +
                "<b>Note:</b> Annotation handlers work with both AST and IR modes. The <code>annotations</code> object is always available.\n" +
                "</div>\n";
    }

    public static String getAnnotationProperties() {
        return getStylesheet() +
                "<h1>Annotation Properties</h1>\n" +
                "<p>Each annotation object passed to handlers has these properties:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>anno.type</code></td><td>string</td><td>Full type descriptor (e.g., \"Ljavax/inject/Named;\")</td></tr>\n" +
                "<tr><td><code>anno.simpleName</code></td><td>string</td><td>Simple annotation name (e.g., \"Named\")</td></tr>\n" +
                "<tr><td><code>anno.target</code></td><td>string</td><td>Name of the annotated element (class, method, or field name)</td></tr>\n" +
                "<tr><td><code>anno.values</code></td><td>object</td><td>Element-value pairs from the annotation</td></tr>\n" +
                "</table>\n" +
                "<h2>Annotation Values</h2>\n" +
                "<p>The <code>values</code> object contains annotation element values:</p>\n" +
                "<pre><span class='cmt'>// For @Named(\"myBean\")</span>\n" +
                "anno.values.value  <span class='cmt'>// \"myBean\"</span>\n" +
                "\n" +
                "<span class='cmt'>// For @RequestMapping(path=\"/api\", method=\"GET\")</span>\n" +
                "anno.values.path   <span class='cmt'>// \"/api\"</span>\n" +
                "anno.values.method <span class='cmt'>// \"GET\"</span></pre>\n" +
                "<h2>Type Descriptor Format</h2>\n" +
                "<p>The <code>type</code> property uses JVM internal format:</p>\n" +
                "<table>\n" +
                "<tr><th>Annotation</th><th>Type Descriptor</th><th>Simple Name</th></tr>\n" +
                "<tr><td><code>@Named</code></td><td><code>Ljavax/inject/Named;</code></td><td>Named</td></tr>\n" +
                "<tr><td><code>@Inject</code></td><td><code>Ljavax/inject/Inject;</code></td><td>Inject</td></tr>\n" +
                "<tr><td><code>@Override</code></td><td><code>Ljava/lang/Override;</code></td><td>Override</td></tr>\n" +
                "<tr><td><code>@Deprecated</code></td><td><code>Ljava/lang/Deprecated;</code></td><td>Deprecated</td></tr>\n" +
                "</table>\n";
    }

    public static String getAnnotationExamples() {
        return getStylesheet() +
                "<h1>Annotation Examples</h1>\n" +
                "<h2>Strip @Named Annotations</h2>\n" +
                "<pre><span class='cmt'>// Remove all @Named annotations from classes, methods, and fields</span>\n" +
                "\n" +
                "annotations.<span class='fn'>onClassAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Named\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Removing @Named from class\"</span>);\n" +
                "        <span class='kw'>return</span> <span class='kw'>null</span>;\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});\n" +
                "\n" +
                "annotations.<span class='fn'>onMethodAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Named\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Removing @Named from \"</span> + anno.target);\n" +
                "        <span class='kw'>return</span> <span class='kw'>null</span>;\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});\n" +
                "\n" +
                "annotations.<span class='fn'>onFieldAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Named\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Removing @Named from \"</span> + anno.target);\n" +
                "        <span class='kw'>return</span> <span class='kw'>null</span>;\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});</pre>\n" +
                "<h2>Find All Deprecated Elements</h2>\n" +
                "<pre><span class='cmt'>// List all @Deprecated annotations</span>\n" +
                "\n" +
                "annotations.<span class='fn'>onClassAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Deprecated\"</span>) {\n" +
                "        <span class='fn'>warn</span>(<span class='str'>\"Deprecated class: \"</span> + anno.target);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});\n" +
                "\n" +
                "annotations.<span class='fn'>onMethodAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Deprecated\"</span>) {\n" +
                "        <span class='fn'>warn</span>(<span class='str'>\"Deprecated method: \"</span> + anno.target);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});</pre>\n" +
                "<h2>Strip All Injection Annotations</h2>\n" +
                "<pre><span class='cmt'>// Remove @Inject, @Named, @Qualifier from fields</span>\n" +
                "\n" +
                "<span class='kw'>let</span> injectionAnnotations = [<span class='str'>\"Inject\"</span>, <span class='str'>\"Named\"</span>, <span class='str'>\"Qualifier\"</span>, <span class='str'>\"Autowired\"</span>];\n" +
                "\n" +
                "annotations.<span class='fn'>onFieldAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (injectionAnnotations.<span class='fn'>includes</span>(anno.simpleName)) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Stripping @\"</span> + anno.simpleName + <span class='str'>\" from \"</span> + anno.target);\n" +
                "        <span class='kw'>return</span> <span class='kw'>null</span>;\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});</pre>\n" +
                "<h2>Log Annotation Values</h2>\n" +
                "<pre><span class='cmt'>// Log @Named annotation values</span>\n" +
                "\n" +
                "annotations.<span class='fn'>onFieldAnnotation</span>((anno) => {\n" +
                "    <span class='kw'>if</span> (anno.simpleName == <span class='str'>\"Named\"</span>) {\n" +
                "        <span class='kw'>let</span> name = anno.values.value;\n" +
                "        <span class='kw'>if</span> (name != <span class='kw'>null</span>) {\n" +
                "            <span class='fn'>log</span>(<span class='str'>\"Field \"</span> + anno.target + <span class='str'>\" named: \"</span> + name);\n" +
                "        }\n" +
                "    }\n" +
                "    <span class='kw'>return</span> anno;\n" +
                "});</pre>\n";
    }

    public static String getAstApiOverview() {
        return getStylesheet() +
                "<h1>AST API Overview</h1>\n" +
                "<p>The AST (Abstract Syntax Tree) API lets you work with high-level code constructs. " +
                "Use this mode when you want to analyze or transform code at the statement/expression level.</p>\n" +
                "<h2>How It Works</h2>\n" +
                "<ol>\n" +
                "<li>Register handlers for node types you're interested in</li>\n" +
                "<li>The script engine walks the AST and calls your handlers</li>\n" +
                "<li>Return <code>null</code> to remove a node, or a modified node to replace it</li>\n" +
                "</ol>\n" +
                "<h2>Available Handlers</h2>\n" +
                "<table>\n" +
                "<tr><th>Handler</th><th>Triggered By</th></tr>\n" +
                "<tr><td><code>ast.onMethodCall(fn)</code></td><td>Method invocations</td></tr>\n" +
                "<tr><td><code>ast.onFieldAccess(fn)</code></td><td>Field reads/writes</td></tr>\n" +
                "<tr><td><code>ast.onBinaryExpr(fn)</code></td><td>Binary operations (+, -, ==, etc.)</td></tr>\n" +
                "<tr><td><code>ast.onUnaryExpr(fn)</code></td><td>Unary operations (!, -, ++, etc.)</td></tr>\n" +
                "<tr><td><code>ast.onIf(fn)</code></td><td>If statements</td></tr>\n" +
                "<tr><td><code>ast.onReturn(fn)</code></td><td>Return statements</td></tr>\n" +
                "</table>\n" +
                "<div class='note'>\n" +
                "<b>Mode:</b> Set the script mode to <b>AST</b> in the dropdown to use these handlers.\n" +
                "</div>\n";
    }

    public static String getAstMethodCall() {
        return getStylesheet() +
                "<h1>AST: Method Calls</h1>\n" +
                "<p>Handle method invocation nodes with <code>ast.onMethodCall()</code>:</p>\n" +
                "<h2>Node Properties</h2>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.target</code></td><td>object</td><td>The object being called on (may be null for static)</td></tr>\n" +
                "<tr><td><code>node.name</code></td><td>string</td><td>Method name being called</td></tr>\n" +
                "<tr><td><code>node.owner</code></td><td>string</td><td>Class that owns the method</td></tr>\n" +
                "<tr><td><code>node.descriptor</code></td><td>string</td><td>Method signature</td></tr>\n" +
                "<tr><td><code>node.args</code></td><td>array</td><td>Array of argument nodes</td></tr>\n" +
                "<tr><td><code>node.isStatic</code></td><td>boolean</td><td>True if static method call</td></tr>\n" +
                "</table>\n" +
                "<h2>Example: Log All Method Calls</h2>\n" +
                "<pre>ast.<span class='fn'>onMethodCall</span>((node) => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Call: \"</span> + node.owner + <span class='str'>\".\"</span> + node.name);\n" +
                "    <span class='kw'>return</span> node;  <span class='cmt'>// Keep unchanged</span>\n" +
                "});</pre>\n" +
                "<h2>Example: Find System.out.println Calls</h2>\n" +
                "<pre>ast.<span class='fn'>onMethodCall</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.owner == <span class='str'>\"java/io/PrintStream\"</span> &&\n" +
                "        node.name == <span class='str'>\"println\"</span>) {\n" +
                "        <span class='fn'>warn</span>(<span class='str'>\"Found println at \"</span> + context.methodName);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n" +
                "<h2>Example: Remove Debug Logging</h2>\n" +
                "<pre>ast.<span class='fn'>onMethodCall</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.name == <span class='str'>\"debug\"</span> || node.name == <span class='str'>\"trace\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Removing: \"</span> + node.name);\n" +
                "        <span class='kw'>return</span> <span class='kw'>null</span>;  <span class='cmt'>// Remove the call</span>\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n";
    }

    public static String getAstFieldAccess() {
        return getStylesheet() +
                "<h1>AST: Field Access</h1>\n" +
                "<p>Handle field read/write nodes with <code>ast.onFieldAccess()</code>:</p>\n" +
                "<h2>Node Properties</h2>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.target</code></td><td>object</td><td>Object containing the field</td></tr>\n" +
                "<tr><td><code>node.name</code></td><td>string</td><td>Field name</td></tr>\n" +
                "<tr><td><code>node.owner</code></td><td>string</td><td>Class that owns the field</td></tr>\n" +
                "<tr><td><code>node.descriptor</code></td><td>string</td><td>Field type descriptor</td></tr>\n" +
                "<tr><td><code>node.isStatic</code></td><td>boolean</td><td>True if static field</td></tr>\n" +
                "<tr><td><code>node.isRead</code></td><td>boolean</td><td>True if reading field</td></tr>\n" +
                "<tr><td><code>node.isWrite</code></td><td>boolean</td><td>True if writing field</td></tr>\n" +
                "</table>\n" +
                "<h2>Example: Find Field Usage</h2>\n" +
                "<pre>ast.<span class='fn'>onFieldAccess</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.name == <span class='str'>\"DEBUG_MODE\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"DEBUG_MODE accessed in \"</span> + context.methodName);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n";
    }

    public static String getAstExpressions() {
        return getStylesheet() +
                "<h1>AST: Expressions</h1>\n" +
                "<h2>Binary Expressions</h2>\n" +
                "<p>Handle binary operations with <code>ast.onBinaryExpr()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.left</code></td><td>object</td><td>Left operand</td></tr>\n" +
                "<tr><td><code>node.right</code></td><td>object</td><td>Right operand</td></tr>\n" +
                "<tr><td><code>node.operator</code></td><td>string</td><td>Operator (+, -, *, /, ==, etc.)</td></tr>\n" +
                "</table>\n" +
                "<pre>ast.<span class='fn'>onBinaryExpr</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.operator == <span class='str'>\"==\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Found equality check\"</span>);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n" +
                "<h2>Unary Expressions</h2>\n" +
                "<p>Handle unary operations with <code>ast.onUnaryExpr()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.operand</code></td><td>object</td><td>The operand</td></tr>\n" +
                "<tr><td><code>node.operator</code></td><td>string</td><td>Operator (!, -, ~, ++, --)</td></tr>\n" +
                "<tr><td><code>node.isPrefix</code></td><td>boolean</td><td>True if prefix operator</td></tr>\n" +
                "</table>\n" +
                "<pre>ast.<span class='fn'>onUnaryExpr</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.operator == <span class='str'>\"!\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Found negation\"</span>);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n";
    }

    public static String getAstControlFlow() {
        return getStylesheet() +
                "<h1>AST: Control Flow</h1>\n" +
                "<h2>If Statements</h2>\n" +
                "<p>Handle if statements with <code>ast.onIf()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.condition</code></td><td>object</td><td>The condition expression</td></tr>\n" +
                "<tr><td><code>node.thenBranch</code></td><td>object</td><td>Statements when true</td></tr>\n" +
                "<tr><td><code>node.elseBranch</code></td><td>object</td><td>Statements when false (may be null)</td></tr>\n" +
                "</table>\n" +
                "<pre>ast.<span class='fn'>onIf</span>((node) => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Found if statement\"</span>);\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n" +
                "<h2>Return Statements</h2>\n" +
                "<p>Handle return statements with <code>ast.onReturn()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>node.value</code></td><td>object</td><td>Return value expression (null for void)</td></tr>\n" +
                "</table>\n" +
                "<pre>ast.<span class='fn'>onReturn</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.value != <span class='kw'>null</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Returns a value\"</span>);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n";
    }

    public static String getIrApiOverview() {
        return getStylesheet() +
                "<h1>IR API Overview</h1>\n" +
                "<p>The IR (Intermediate Representation) API lets you work at the instruction level. " +
                "Use this mode for low-level bytecode analysis and precise transformations.</p>\n" +
                "<h2>How It Works</h2>\n" +
                "<ol>\n" +
                "<li>Register handlers for instruction types you're interested in</li>\n" +
                "<li>Use iteration methods to walk through blocks and instructions</li>\n" +
                "<li>Return <code>null</code> to remove an instruction, or a modified instruction to replace it</li>\n" +
                "</ol>\n" +
                "<h2>Available Handlers</h2>\n" +
                "<table>\n" +
                "<tr><th>Handler</th><th>Triggered By</th></tr>\n" +
                "<tr><td><code>ir.onBinaryOp(fn)</code></td><td>Binary operations (add, sub, mul, etc.)</td></tr>\n" +
                "<tr><td><code>ir.onUnaryOp(fn)</code></td><td>Unary operations (neg, not)</td></tr>\n" +
                "<tr><td><code>ir.onInvoke(fn)</code></td><td>Method invocations</td></tr>\n" +
                "<tr><td><code>ir.onGetField(fn)</code></td><td>Field reads</td></tr>\n" +
                "<tr><td><code>ir.onPutField(fn)</code></td><td>Field writes</td></tr>\n" +
                "<tr><td><code>ir.onConstant(fn)</code></td><td>Constant values</td></tr>\n" +
                "<tr><td><code>ir.onBranch(fn)</code></td><td>Branch instructions</td></tr>\n" +
                "<tr><td><code>ir.onReturn(fn)</code></td><td>Return instructions</td></tr>\n" +
                "</table>\n" +
                "<h2>Iteration Methods</h2>\n" +
                "<table>\n" +
                "<tr><th>Method</th><th>Description</th></tr>\n" +
                "<tr><td><code>ir.forEachBlock(fn)</code></td><td>Iterate over all basic blocks</td></tr>\n" +
                "<tr><td><code>ir.forEachInstruction(fn)</code></td><td>Iterate over all instructions</td></tr>\n" +
                "</table>\n" +
                "<div class='note'>\n" +
                "<b>Mode:</b> Set the script mode to <b>IR</b> in the dropdown to use these handlers.\n" +
                "</div>\n";
    }

    public static String getIrInstructions() {
        return getStylesheet() +
                "<h1>IR: Instructions</h1>\n" +
                "<h2>Invoke Instructions</h2>\n" +
                "<p>Handle method calls with <code>ir.onInvoke()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.owner</code></td><td>string</td><td>Class containing the method</td></tr>\n" +
                "<tr><td><code>instr.name</code></td><td>string</td><td>Method name</td></tr>\n" +
                "<tr><td><code>instr.descriptor</code></td><td>string</td><td>Method signature</td></tr>\n" +
                "<tr><td><code>instr.opcode</code></td><td>string</td><td>INVOKEVIRTUAL, INVOKESTATIC, etc.</td></tr>\n" +
                "<tr><td><code>instr.args</code></td><td>array</td><td>Argument values</td></tr>\n" +
                "</table>\n" +
                "<pre>ir.<span class='fn'>onInvoke</span>((instr) => {\n" +
                "    <span class='fn'>log</span>(instr.opcode + <span class='str'>\" \"</span> + instr.owner + <span class='str'>\".\"</span> + instr.name);\n" +
                "    <span class='kw'>return</span> instr;\n" +
                "});</pre>\n" +
                "<h2>Field Instructions</h2>\n" +
                "<p>Handle field access with <code>ir.onGetField()</code> and <code>ir.onPutField()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.owner</code></td><td>string</td><td>Class containing the field</td></tr>\n" +
                "<tr><td><code>instr.name</code></td><td>string</td><td>Field name</td></tr>\n" +
                "<tr><td><code>instr.descriptor</code></td><td>string</td><td>Field type</td></tr>\n" +
                "<tr><td><code>instr.isStatic</code></td><td>boolean</td><td>True for static fields</td></tr>\n" +
                "</table>\n" +
                "<h2>Constant Instructions</h2>\n" +
                "<p>Handle constants with <code>ir.onConstant()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.value</code></td><td>any</td><td>The constant value</td></tr>\n" +
                "<tr><td><code>instr.type</code></td><td>string</td><td>Value type (int, long, string, etc.)</td></tr>\n" +
                "</table>\n" +
                "<pre>ir.<span class='fn'>onConstant</span>((instr) => {\n" +
                "    <span class='kw'>if</span> (instr.type == <span class='str'>\"string\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"String constant: \"</span> + instr.value);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> instr;\n" +
                "});</pre>\n";
    }

    public static String getIrOperations() {
        return getStylesheet() +
                "<h1>IR: Operations</h1>\n" +
                "<h2>Binary Operations</h2>\n" +
                "<p>Handle arithmetic/logic operations with <code>ir.onBinaryOp()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.operator</code></td><td>string</td><td>ADD, SUB, MUL, DIV, AND, OR, etc.</td></tr>\n" +
                "<tr><td><code>instr.left</code></td><td>object</td><td>Left operand value</td></tr>\n" +
                "<tr><td><code>instr.right</code></td><td>object</td><td>Right operand value</td></tr>\n" +
                "<tr><td><code>instr.type</code></td><td>string</td><td>Result type (int, long, etc.)</td></tr>\n" +
                "</table>\n" +
                "<pre>ir.<span class='fn'>onBinaryOp</span>((instr) => {\n" +
                "    <span class='kw'>if</span> (instr.operator == <span class='str'>\"DIV\"</span>) {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Division found\"</span>);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> instr;\n" +
                "});</pre>\n" +
                "<h2>Branch Operations</h2>\n" +
                "<p>Handle branches with <code>ir.onBranch()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.condition</code></td><td>object</td><td>Branch condition (null for unconditional)</td></tr>\n" +
                "<tr><td><code>instr.trueTarget</code></td><td>object</td><td>Target block if true</td></tr>\n" +
                "<tr><td><code>instr.falseTarget</code></td><td>object</td><td>Target block if false</td></tr>\n" +
                "</table>\n" +
                "<h2>Return Operations</h2>\n" +
                "<p>Handle returns with <code>ir.onReturn()</code>:</p>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>instr.value</code></td><td>object</td><td>Return value (null for void)</td></tr>\n" +
                "</table>\n";
    }

    public static String getIrIteration() {
        return getStylesheet() +
                "<h1>IR: Iteration</h1>\n" +
                "<p>Use iteration methods to walk through the IR structure:</p>\n" +
                "<h2>Iterate Over Blocks</h2>\n" +
                "<pre>ir.<span class='fn'>forEachBlock</span>((block) => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Block: \"</span> + block.label);\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"  Instructions: \"</span> + block.instructions.length);\n" +
                "});</pre>\n" +
                "<h2>Iterate Over Instructions</h2>\n" +
                "<pre>ir.<span class='fn'>forEachInstruction</span>((instr) => {\n" +
                "    <span class='fn'>log</span>(instr.opcode + <span class='str'>\" - \"</span> + <span class='fn'>typeof</span>(instr));\n" +
                "});</pre>\n" +
                "<h2>Block Properties</h2>\n" +
                "<table>\n" +
                "<tr><th>Property</th><th>Type</th><th>Description</th></tr>\n" +
                "<tr><td><code>block.label</code></td><td>string</td><td>Block identifier</td></tr>\n" +
                "<tr><td><code>block.instructions</code></td><td>array</td><td>Instructions in this block</td></tr>\n" +
                "<tr><td><code>block.predecessors</code></td><td>array</td><td>Blocks that jump to this one</td></tr>\n" +
                "<tr><td><code>block.successors</code></td><td>array</td><td>Blocks this jumps to</td></tr>\n" +
                "</table>\n";
    }

    public static String getExamples() {
        return getStylesheet() +
                "<h1>Example Scripts</h1>\n" +
                "<h2>Find All String Constants</h2>\n" +
                "<pre><span class='cmt'>// IR Mode - Find all string literals</span>\n" +
                "ir.<span class='fn'>onConstant</span>((instr) => {\n" +
                "    <span class='kw'>if</span> (instr.type == <span class='str'>\"string\"</span>) {\n" +
                "        <span class='fn'>log</span>(context.simpleClassName + <span class='str'>\".\"</span> + context.methodName +\n" +
                "            <span class='str'>\": \\\"\"</span> + instr.value + <span class='str'>\"\\\"\"</span>);\n" +
                "    }\n" +
                "    <span class='kw'>return</span> instr;\n" +
                "});</pre>\n" +
                "<h2>Log All Method Calls</h2>\n" +
                "<pre><span class='cmt'>// AST Mode - Log every method invocation</span>\n" +
                "ast.<span class='fn'>onMethodCall</span>((node) => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"[\"</span> + context.simpleClassName + <span class='str'>\"] \"</span> +\n" +
                "        node.owner + <span class='str'>\".\"</span> + node.name + <span class='str'>\"()\"</span>);\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n" +
                "<h2>Find Debug Print Statements</h2>\n" +
                "<pre><span class='cmt'>// AST Mode - Find System.out.println calls</span>\n" +
                "<span class='kw'>let</span> count = <span class='num'>0</span>;\n" +
                "\n" +
                "ast.<span class='fn'>onMethodCall</span>((node) => {\n" +
                "    <span class='kw'>if</span> (node.owner == <span class='str'>\"java/io/PrintStream\"</span>) {\n" +
                "        <span class='kw'>if</span> (node.name == <span class='str'>\"println\"</span> || node.name == <span class='str'>\"print\"</span>) {\n" +
                "            count = count + <span class='num'>1</span>;\n" +
                "            <span class='fn'>warn</span>(<span class='str'>\"Print found in \"</span> + context.methodName);\n" +
                "        }\n" +
                "    }\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});\n" +
                "\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Total print statements: \"</span> + count);</pre>\n" +
                "<h2>Analyze Method Complexity</h2>\n" +
                "<pre><span class='cmt'>// IR Mode - Count branches per method</span>\n" +
                "<span class='kw'>let</span> branches = <span class='num'>0</span>;\n" +
                "\n" +
                "ir.<span class='fn'>onBranch</span>((instr) => {\n" +
                "    <span class='kw'>if</span> (instr.condition != <span class='kw'>null</span>) {\n" +
                "        branches = branches + <span class='num'>1</span>;\n" +
                "    }\n" +
                "    <span class='kw'>return</span> instr;\n" +
                "});\n" +
                "\n" +
                "<span class='kw'>if</span> (branches > <span class='num'>10</span>) {\n" +
                "    <span class='fn'>warn</span>(context.methodName + <span class='str'>\" has high complexity: \"</span> + branches + <span class='str'>\" branches\"</span>);\n" +
                "}</pre>\n" +
                "<h2>Find Field Usage</h2>\n" +
                "<pre><span class='cmt'>// AST Mode - Track field access patterns</span>\n" +
                "ast.<span class='fn'>onFieldAccess</span>((node) => {\n" +
                "    <span class='kw'>let</span> action = node.isRead ? <span class='str'>\"READ\"</span> : <span class='str'>\"WRITE\"</span>;\n" +
                "    <span class='fn'>log</span>(action + <span class='str'>\": \"</span> + node.owner + <span class='str'>\".\"</span> + node.name);\n" +
                "    <span class='kw'>return</span> node;\n" +
                "});</pre>\n";
    }

    public static String getTips() {
        return getStylesheet() +
                "<h1>Tips & Best Practices</h1>\n" +
                "<h2>Choosing a Mode</h2>\n" +
                "<ul>\n" +
                "<li><b>Use AST mode</b> when working with high-level constructs (method calls, field access, expressions)</li>\n" +
                "<li><b>Use IR mode</b> when you need precise control or are analyzing bytecode patterns</li>\n" +
                "</ul>\n" +
                "<h2>Performance Tips</h2>\n" +
                "<ul>\n" +
                "<li>Filter early - check conditions before doing expensive operations</li>\n" +
                "<li>Use <code>context.packageName</code> to skip uninteresting classes</li>\n" +
                "<li>Limit output - don't log every instruction in large classes</li>\n" +
                "</ul>\n" +
                "<h2>Debugging Scripts</h2>\n" +
                "<ul>\n" +
                "<li>Use <code>log()</code> liberally while developing</li>\n" +
                "<li>Check <code>typeof()</code> when unsure about a value's type</li>\n" +
                "<li>Use <code>warn()</code> for important findings that shouldn't get lost in output</li>\n" +
                "<li>Check the console panel for error messages</li>\n" +
                "</ul>\n" +
                "<h2>Common Patterns</h2>\n" +
                "<pre><span class='cmt'>// Skip library code</span>\n" +
                "<span class='kw'>if</span> (context.packageName.<span class='fn'>startsWith</span>(<span class='str'>\"java/\"</span>) ||\n" +
                "    context.packageName.<span class='fn'>startsWith</span>(<span class='str'>\"javax/\"</span>)) {\n" +
                "    <span class='kw'>return</span> node;\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Skip constructors</span>\n" +
                "<span class='kw'>if</span> (context.methodName == <span class='str'>\"&lt;init&gt;\"</span> ||\n" +
                "    context.methodName == <span class='str'>\"&lt;clinit&gt;\"</span>) {\n" +
                "    <span class='kw'>return</span> node;\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Focus on specific package</span>\n" +
                "<span class='kw'>if</span> (context.packageName.<span class='fn'>includes</span>(<span class='str'>\"myapp/core\"</span>)) {\n" +
                "    <span class='cmt'>// Process this class</span>\n" +
                "}</pre>\n" +
                "<h2>Return Values</h2>\n" +
                "<ul>\n" +
                "<li><code>return node;</code> - Keep the node unchanged</li>\n" +
                "<li><code>return null;</code> - Remove the node</li>\n" +
                "<li><code>return modifiedNode;</code> - Replace with modified version</li>\n" +
                "</ul>\n";
    }

    public static String[] getSectionTitles() {
        return new String[] {
                "Overview",
                "Basics",
                "  Variables",
                "  Data Types",
                "  Operators",
                "  Comments",
                "Control Flow",
                "  If/Else & Blocks",
                "  Functions",
                "Built-in Functions",
                "  Logging & Types",
                "  String Methods",
                "Context Object",
                "Annotation API",
                "  Annotation Overview",
                "  Annotation Properties",
                "  Annotation Examples",
                "AST API",
                "  AST Overview",
                "  Method Calls",
                "  Field Access",
                "  Expressions",
                "  AST Control Flow",
                "IR API",
                "  IR Overview",
                "  Instructions",
                "  Operations",
                "  Iteration",
                "Examples",
                "Tips & Best Practices"
        };
    }

    public static String getContentForSection(String section) {
        switch (section) {
            case "Overview":
                return getOverview();
            case "  Variables":
                return getVariables();
            case "  Data Types":
                return getDataTypes();
            case "  Operators":
                return getOperators();
            case "  Comments":
                return getComments();
            case "  If/Else & Blocks":
                return getControlFlow();
            case "  Functions":
                return getFunctions();
            case "  Logging & Types":
                return getBuiltinFunctions();
            case "  String Methods":
                return getStringMethods();
            case "Context Object":
                return getContextObject();
            case "Annotation API":
            case "  Annotation Overview":
                return getAnnotationApiOverview();
            case "  Annotation Properties":
                return getAnnotationProperties();
            case "  Annotation Examples":
                return getAnnotationExamples();
            case "AST API":
            case "  AST Overview":
                return getAstApiOverview();
            case "  Method Calls":
                return getAstMethodCall();
            case "  Field Access":
                return getAstFieldAccess();
            case "  Expressions":
                return getAstExpressions();
            case "  AST Control Flow":
                return getAstControlFlow();
            case "IR API":
            case "  IR Overview":
                return getIrApiOverview();
            case "  Instructions":
                return getIrInstructions();
            case "  Operations":
                return getIrOperations();
            case "  Iteration":
                return getIrIteration();
            case "Examples":
                return getExamples();
            case "Tips & Best Practices":
                return getTips();
            case "Basics":
                return getVariables();
            case "Built-in Functions":
                return getBuiltinFunctions();
            default:
                return getOverview();
        }
    }
}
