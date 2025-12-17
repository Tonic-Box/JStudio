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
                "<p>JStudio includes a powerful scripting language for analyzing and transforming Java bytecode. " +
                "The language provides multiple analysis APIs:</p>\n" +
                "<table>\n" +
                "<tr><th>Global Object</th><th>Purpose</th></tr>\n" +
                "<tr><td><code>context</code></td><td>Current class/method info</td></tr>\n" +
                "<tr><td><code>ast</code></td><td>AST node handlers</td></tr>\n" +
                "<tr><td><code>ir</code></td><td>IR instruction handlers</td></tr>\n" +
                "<tr><td><code>annotations</code></td><td>Annotation handlers</td></tr>\n" +
                "<tr><td><code>results</code></td><td>Findings collection and export</td></tr>\n" +
                "<tr><td><code>project</code></td><td>Project-wide queries</td></tr>\n" +
                "<tr><td><code>callgraph</code></td><td>Call graph analysis</td></tr>\n" +
                "<tr><td><code>dataflow</code></td><td>Data flow and taint analysis</td></tr>\n" +
                "<tr><td><code>dependencies</code></td><td>Class dependency analysis</td></tr>\n" +
                "<tr><td><code>patterns</code></td><td>Pattern matching</td></tr>\n" +
                "<tr><td><code>simulation</code></td><td>Abstract interpretation</td></tr>\n" +
                "<tr><td><code>instrument</code></td><td>Bytecode modification</td></tr>\n" +
                "<tr><td><code>types</code></td><td>Type inference</td></tr>\n" +
                "<tr><td><code>strings</code></td><td>String analysis</td></tr>\n" +
                "<tr><td><code>pipeline</code></td><td>Multi-stage workflows</td></tr>\n" +
                "</table>\n" +
                "<div class='note'>\n" +
                "<b>Quick Start:</b> Try the example scripts in the Script Library panel to see the language in action.\n" +
                "</div>\n";
    }

    public static String getLoops() {
        return getStylesheet() +
                "<h1>Loops</h1>\n" +
                "<h2>While Loop</h2>\n" +
                "<pre><span class='kw'>let</span> i = <span class='num'>0</span>;\n" +
                "<span class='kw'>while</span> (i &lt; <span class='num'>10</span>) {\n" +
                "    <span class='fn'>log</span>(i);\n" +
                "    i = i + <span class='num'>1</span>;\n" +
                "}</pre>\n" +
                "<h2>For Loop</h2>\n" +
                "<pre><span class='kw'>for</span> (<span class='kw'>let</span> i = <span class='num'>0</span>; i &lt; <span class='num'>10</span>; i = i + <span class='num'>1</span>) {\n" +
                "    <span class='fn'>log</span>(i);\n" +
                "}</pre>\n" +
                "<h2>For-Of Loop (Arrays)</h2>\n" +
                "<pre><span class='kw'>let</span> items = [<span class='str'>\"a\"</span>, <span class='str'>\"b\"</span>, <span class='str'>\"c\"</span>];\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> item <span class='kw'>of</span> items) {\n" +
                "    <span class='fn'>log</span>(item);\n" +
                "}</pre>\n" +
                "<h2>For-In Loop (Objects)</h2>\n" +
                "<pre><span class='kw'>let</span> obj = { name: <span class='str'>\"test\"</span>, count: <span class='num'>42</span> };\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> key <span class='kw'>in</span> obj) {\n" +
                "    <span class='fn'>log</span>(key + <span class='str'>\": \"</span> + obj[key]);\n" +
                "}</pre>\n" +
                "<h2>Break and Continue</h2>\n" +
                "<pre><span class='kw'>for</span> (<span class='kw'>let</span> i = <span class='num'>0</span>; i &lt; <span class='num'>10</span>; i = i + <span class='num'>1</span>) {\n" +
                "    <span class='kw'>if</span> (i == <span class='num'>5</span>) <span class='kw'>break</span>;     <span class='cmt'>// Exit loop</span>\n" +
                "    <span class='kw'>if</span> (i % <span class='num'>2</span> == <span class='num'>0</span>) <span class='kw'>continue</span>; <span class='cmt'>// Skip even numbers</span>\n" +
                "    <span class='fn'>log</span>(i);\n" +
                "}</pre>\n";
    }

    public static String getArrayMethods() {
        return getStylesheet() +
                "<h1>Array Methods</h1>\n" +
                "<h2>Iteration</h2>\n" +
                "<pre><span class='kw'>let</span> arr = [<span class='num'>1</span>, <span class='num'>2</span>, <span class='num'>3</span>];\n" +
                "\n" +
                "<span class='cmt'>// forEach - iterate over each element</span>\n" +
                "arr.<span class='fn'>forEach</span>((item) => <span class='fn'>log</span>(item));\n" +
                "\n" +
                "<span class='cmt'>// map - transform each element</span>\n" +
                "<span class='kw'>let</span> doubled = arr.<span class='fn'>map</span>((x) => x * <span class='num'>2</span>);  <span class='cmt'>// [2, 4, 6]</span>\n" +
                "\n" +
                "<span class='cmt'>// filter - keep matching elements</span>\n" +
                "<span class='kw'>let</span> evens = arr.<span class='fn'>filter</span>((x) => x % <span class='num'>2</span> == <span class='num'>0</span>);  <span class='cmt'>// [2]</span></pre>\n" +
                "<h2>Search</h2>\n" +
                "<pre><span class='cmt'>// find - get first matching element</span>\n" +
                "<span class='kw'>let</span> found = arr.<span class='fn'>find</span>((x) => x > <span class='num'>1</span>);  <span class='cmt'>// 2</span>\n" +
                "\n" +
                "<span class='cmt'>// some - check if any match</span>\n" +
                "<span class='kw'>let</span> hasEven = arr.<span class='fn'>some</span>((x) => x % <span class='num'>2</span> == <span class='num'>0</span>);  <span class='cmt'>// true</span>\n" +
                "\n" +
                "<span class='cmt'>// every - check if all match</span>\n" +
                "<span class='kw'>let</span> allPositive = arr.<span class='fn'>every</span>((x) => x > <span class='num'>0</span>);  <span class='cmt'>// true</span>\n" +
                "\n" +
                "<span class='cmt'>// includes - check if value exists</span>\n" +
                "<span class='kw'>let</span> has2 = arr.<span class='fn'>includes</span>(<span class='num'>2</span>);  <span class='cmt'>// true</span>\n" +
                "\n" +
                "<span class='cmt'>// indexOf - find position</span>\n" +
                "<span class='kw'>let</span> pos = arr.<span class='fn'>indexOf</span>(<span class='num'>2</span>);  <span class='cmt'>// 1</span></pre>\n" +
                "<h2>Accumulation</h2>\n" +
                "<pre><span class='cmt'>// reduce - accumulate to single value</span>\n" +
                "<span class='kw'>let</span> sum = arr.<span class='fn'>reduce</span>((acc, x) => acc + x, <span class='num'>0</span>);  <span class='cmt'>// 6</span></pre>\n" +
                "<h2>Modification</h2>\n" +
                "<pre><span class='cmt'>// push/pop - add/remove at end</span>\n" +
                "arr.<span class='fn'>push</span>(<span class='num'>4</span>);  <span class='cmt'>// [1, 2, 3, 4]</span>\n" +
                "arr.<span class='fn'>pop</span>();     <span class='cmt'>// returns 4</span>\n" +
                "\n" +
                "<span class='cmt'>// shift/unshift - remove/add at start</span>\n" +
                "arr.<span class='fn'>shift</span>();     <span class='cmt'>// returns 1</span>\n" +
                "arr.<span class='fn'>unshift</span>(<span class='num'>0</span>);  <span class='cmt'>// adds 0 at start</span>\n" +
                "\n" +
                "<span class='cmt'>// slice - extract portion</span>\n" +
                "<span class='kw'>let</span> part = arr.<span class='fn'>slice</span>(<span class='num'>1</span>, <span class='num'>3</span>);  <span class='cmt'>// elements 1-2</span>\n" +
                "\n" +
                "<span class='cmt'>// concat - combine arrays</span>\n" +
                "<span class='kw'>let</span> combined = arr.<span class='fn'>concat</span>([<span class='num'>4</span>, <span class='num'>5</span>]);\n" +
                "\n" +
                "<span class='cmt'>// join - combine to string</span>\n" +
                "<span class='kw'>let</span> str = arr.<span class='fn'>join</span>(<span class='str'>\", \"</span>);  <span class='cmt'>// \"1, 2, 3\"</span></pre>\n";
    }

    public static String getTryCatch() {
        return getStylesheet() +
                "<h1>Try/Catch</h1>\n" +
                "<p>Handle errors gracefully with try/catch blocks:</p>\n" +
                "<pre><span class='kw'>try</span> {\n" +
                "    <span class='cmt'>// Code that might throw</span>\n" +
                "    <span class='kw'>let</span> result = riskyOperation();\n" +
                "    <span class='fn'>log</span>(result);\n" +
                "} <span class='kw'>catch</span> (e) {\n" +
                "    <span class='cmt'>// Handle error</span>\n" +
                "    <span class='fn'>error</span>(<span class='str'>\"Failed: \"</span> + e);\n" +
                "} <span class='kw'>finally</span> {\n" +
                "    <span class='cmt'>// Always runs</span>\n" +
                "    cleanup();\n" +
                "}</pre>\n" +
                "<h2>Without Finally</h2>\n" +
                "<pre><span class='kw'>try</span> {\n" +
                "    processMethod();\n" +
                "} <span class='kw'>catch</span> (error) {\n" +
                "    <span class='fn'>warn</span>(<span class='str'>\"Error processing: \"</span> + error);\n" +
                "}</pre>\n";
    }

    public static String getResultsApi() {
        return getStylesheet() +
                "<h1>Results API</h1>\n" +
                "<p>The <code>results</code> object collects and exports analysis findings:</p>\n" +
                "<h2>Adding Findings</h2>\n" +
                "<pre>results.<span class='fn'>add</span>({ type: <span class='str'>\"issue\"</span>, method: context.methodName });\n" +
                "results.<span class='fn'>add</span>({ type: <span class='str'>\"vuln\"</span>, severity: <span class='str'>\"high\"</span> });\n" +
                "\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Total findings: \"</span> + results.<span class='fn'>count</span>());</pre>\n" +
                "<h2>Querying Results</h2>\n" +
                "<pre><span class='cmt'>// Get all findings</span>\n" +
                "<span class='kw'>let</span> all = results.<span class='fn'>all</span>();\n" +
                "\n" +
                "<span class='cmt'>// Filter by criteria</span>\n" +
                "<span class='kw'>let</span> vulns = results.<span class='fn'>filter</span>((f) => f.type == <span class='str'>\"vuln\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Group by field</span>\n" +
                "<span class='kw'>let</span> byType = results.<span class='fn'>groupBy</span>((f) => f.type);\n" +
                "\n" +
                "<span class='cmt'>// Sort results</span>\n" +
                "<span class='kw'>let</span> sorted = results.<span class='fn'>sortBy</span>((f) => f.severity);\n" +
                "\n" +
                "<span class='cmt'>// Get unique by key</span>\n" +
                "<span class='kw'>let</span> unique = results.<span class='fn'>unique</span>((f) => f.method);</pre>\n" +
                "<h2>Export</h2>\n" +
                "<pre><span class='cmt'>// Export as JSON</span>\n" +
                "results.<span class='fn'>exportJson</span>(<span class='str'>\"findings.json\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Export as CSV</span>\n" +
                "results.<span class='fn'>exportCsv</span>(<span class='str'>\"findings.csv\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Format as table</span>\n" +
                "<span class='fn'>log</span>(results.<span class='fn'>toTable</span>());\n" +
                "\n" +
                "<span class='cmt'>// Get summary stats</span>\n" +
                "<span class='kw'>let</span> summary = results.<span class='fn'>summary</span>();</pre>\n";
    }

    public static String getProjectApi() {
        return getStylesheet() +
                "<h1>Project API</h1>\n" +
                "<p>The <code>project</code> object provides project-wide queries:</p>\n" +
                "<h2>Iterating Classes/Methods</h2>\n" +
                "<pre><span class='cmt'>// Process all classes</span>\n" +
                "project.<span class='fn'>forEachClass</span>((cls) => {\n" +
                "    <span class='fn'>log</span>(cls.className);\n" +
                "});\n" +
                "\n" +
                "<span class='cmt'>// Process all methods</span>\n" +
                "project.<span class='fn'>forEachMethod</span>((method) => {\n" +
                "    <span class='fn'>log</span>(method.className + <span class='str'>\".\"</span> + method.name);\n" +
                "});</pre>\n" +
                "<h2>Finding Methods</h2>\n" +
                "<pre><span class='cmt'>// Find by access and name</span>\n" +
                "<span class='kw'>let</span> handlers = project.<span class='fn'>findMethods</span>({ \n" +
                "    access: <span class='str'>\"public\"</span>, \n" +
                "    name: <span class='str'>\"*Handler\"</span> \n" +
                "});\n" +
                "\n" +
                "<span class='cmt'>// Find annotated methods</span>\n" +
                "<span class='kw'>let</span> injected = project.<span class='fn'>findAnnotated</span>(<span class='str'>\"javax/inject/Inject\"</span>);</pre>\n" +
                "<h2>Properties</h2>\n" +
                "<pre><span class='fn'>log</span>(<span class='str'>\"Classes: \"</span> + project.<span class='fn'>classCount</span>());\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Methods: \"</span> + project.<span class='fn'>methodCount</span>());</pre>\n";
    }

    public static String getCallGraphApi() {
        return getStylesheet() +
                "<h1>Call Graph API</h1>\n" +
                "<p>The <code>callgraph</code> object analyzes method call relationships:</p>\n" +
                "<h2>Building</h2>\n" +
                "<pre>callgraph.<span class='fn'>build</span>();\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Methods: \"</span> + callgraph.<span class='fn'>methodCount</span>());\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Edges: \"</span> + callgraph.<span class='fn'>edgeCount</span>());</pre>\n" +
                "<h2>Querying</h2>\n" +
                "<pre><span class='cmt'>// Get callers/callees</span>\n" +
                "<span class='kw'>let</span> callers = callgraph.<span class='fn'>getCallers</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "<span class='kw'>let</span> callees = callgraph.<span class='fn'>getCallees</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Transitive analysis</span>\n" +
                "<span class='kw'>let</span> allCallees = callgraph.<span class='fn'>getTransitiveCallees</span>(method);\n" +
                "<span class='kw'>let</span> allCallers = callgraph.<span class='fn'>getTransitiveCallers</span>(method);\n" +
                "\n" +
                "<span class='cmt'>// Check reachability</span>\n" +
                "<span class='kw'>if</span> (callgraph.<span class='fn'>canReach</span>(entry, target)) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Reachable!\"</span>);\n" +
                "}</pre>\n" +
                "<h2>Analysis</h2>\n" +
                "<pre><span class='cmt'>// Find entry points</span>\n" +
                "<span class='kw'>let</span> entries = callgraph.<span class='fn'>findEntryPoints</span>();\n" +
                "\n" +
                "<span class='cmt'>// Find dead code</span>\n" +
                "<span class='kw'>let</span> dead = callgraph.<span class='fn'>findDeadMethods</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get all reachable from entries</span>\n" +
                "<span class='kw'>let</span> reachable = callgraph.<span class='fn'>getReachableFrom</span>(entries);</pre>\n";
    }

    public static String getDataFlowApi() {
        return getStylesheet() +
                "<h1>Data Flow API</h1>\n" +
                "<p>The <code>dataflow</code> object performs data flow analysis:</p>\n" +
                "<h2>Building</h2>\n" +
                "<pre><span class='cmt'>// Build for a specific method</span>\n" +
                "dataflow.<span class='fn'>build</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Nodes: \"</span> + dataflow.<span class='fn'>nodeCount</span>());\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Edges: \"</span> + dataflow.<span class='fn'>edgeCount</span>());</pre>\n" +
                "<h2>Querying</h2>\n" +
                "<pre><span class='cmt'>// Get all nodes/edges</span>\n" +
                "<span class='kw'>let</span> nodes = dataflow.<span class='fn'>getNodes</span>();\n" +
                "<span class='kw'>let</span> edges = dataflow.<span class='fn'>getEdges</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get sources and sinks</span>\n" +
                "<span class='kw'>let</span> sources = dataflow.<span class='fn'>getSources</span>();\n" +
                "<span class='kw'>let</span> sinks = dataflow.<span class='fn'>getSinks</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get by type</span>\n" +
                "<span class='kw'>let</span> params = dataflow.<span class='fn'>getParams</span>();\n" +
                "<span class='kw'>let</span> invokes = dataflow.<span class='fn'>getInvokes</span>();</pre>\n" +
                "<h2>Taint Analysis</h2>\n" +
                "<pre><span class='cmt'>// Check if data flows between nodes</span>\n" +
                "<span class='kw'>if</span> (dataflow.<span class='fn'>flowsTo</span>(source, sink)) {\n" +
                "    <span class='fn'>warn</span>(<span class='str'>\"Potential vulnerability!\"</span>);\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Run full taint analysis</span>\n" +
                "<span class='kw'>let</span> flows = dataflow.<span class='fn'>taintAnalysis</span>(<span class='str'>\"params\"</span>, <span class='str'>\"invokes\"</span>);\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> flow <span class='kw'>of</span> flows) {\n" +
                "    results.<span class='fn'>add</span>({ type: <span class='str'>\"taint\"</span>, source: flow.source, sink: flow.sink });\n" +
                "}</pre>\n";
    }

    public static String getDependencyApi() {
        return getStylesheet() +
                "<h1>Dependency API</h1>\n" +
                "<p>The <code>dependencies</code> object analyzes class dependencies:</p>\n" +
                "<h2>Building</h2>\n" +
                "<pre>dependencies.<span class='fn'>build</span>();\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Classes: \"</span> + dependencies.<span class='fn'>classCount</span>());\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Edges: \"</span> + dependencies.<span class='fn'>edgeCount</span>());</pre>\n" +
                "<h2>Querying</h2>\n" +
                "<pre><span class='cmt'>// Direct dependencies</span>\n" +
                "<span class='kw'>let</span> deps = dependencies.<span class='fn'>getDependencies</span>(<span class='str'>\"com/example/MyClass\"</span>);\n" +
                "<span class='kw'>let</span> dependents = dependencies.<span class='fn'>getDependents</span>(<span class='str'>\"com/example/MyClass\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Transitive dependencies</span>\n" +
                "<span class='kw'>let</span> allDeps = dependencies.<span class='fn'>getTransitiveDeps</span>(<span class='str'>\"com/example/MyClass\"</span>);\n" +
                "<span class='kw'>let</span> allDependents = dependencies.<span class='fn'>getTransitiveDependents</span>(<span class='str'>\"com/example/MyClass\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Check relationships</span>\n" +
                "<span class='kw'>if</span> (dependencies.<span class='fn'>dependsOn</span>(classA, classB)) {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Direct dependency\"</span>);\n" +
                "}</pre>\n" +
                "<h2>Analysis</h2>\n" +
                "<pre><span class='cmt'>// Find circular dependencies</span>\n" +
                "<span class='kw'>let</span> cycles = dependencies.<span class='fn'>findCycles</span>();\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> cycle <span class='kw'>of</span> cycles) {\n" +
                "    <span class='fn'>warn</span>(<span class='str'>\"Cycle: \"</span> + cycle.<span class='fn'>join</span>(<span class='str'>\" -> \"</span>));\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Find leaf/root classes</span>\n" +
                "<span class='kw'>let</span> leaves = dependencies.<span class='fn'>findLeafClasses</span>();\n" +
                "<span class='kw'>let</span> roots = dependencies.<span class='fn'>findRootClasses</span>();</pre>\n";
    }

    public static String getPatternApi() {
        return getStylesheet() +
                "<h1>Pattern API</h1>\n" +
                "<p>The <code>patterns</code> object searches for bytecode patterns:</p>\n" +
                "<pre><span class='cmt'>// Find method calls</span>\n" +
                "<span class='kw'>let</span> calls = patterns.<span class='fn'>findMethodCalls</span>(<span class='str'>\"java/sql/Statement.execute*\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find field accesses</span>\n" +
                "<span class='kw'>let</span> fields = patterns.<span class='fn'>findFieldAccesses</span>(<span class='str'>\"*password*\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find allocations</span>\n" +
                "<span class='kw'>let</span> allocs = patterns.<span class='fn'>findAllocations</span>(<span class='str'>\"java/io/File\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find casts</span>\n" +
                "<span class='kw'>let</span> casts = patterns.<span class='fn'>findCasts</span>(<span class='str'>\"java/lang/String\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find instanceof checks</span>\n" +
                "<span class='kw'>let</span> checks = patterns.<span class='fn'>findInstanceOf</span>();\n" +
                "\n" +
                "<span class='cmt'>// Find null checks and throws</span>\n" +
                "<span class='kw'>let</span> nulls = patterns.<span class='fn'>findNullChecks</span>();\n" +
                "<span class='kw'>let</span> throws = patterns.<span class='fn'>findThrows</span>();</pre>\n";
    }

    public static String getSimulationApi() {
        return getStylesheet() +
                "<h1>Simulation API</h1>\n" +
                "<p>The <code>simulation</code> object provides abstract interpretation:</p>\n" +
                "<h2>Setup</h2>\n" +
                "<pre><span class='cmt'>// Load a method</span>\n" +
                "simulation.<span class='fn'>load</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Register callbacks</span>\n" +
                "simulation.<span class='fn'>onInstruction</span>((instr) => {\n" +
                "    <span class='fn'>log</span>(instr.type);\n" +
                "});\n" +
                "\n" +
                "simulation.<span class='fn'>onInvoke</span>((call) => {\n" +
                "    <span class='fn'>log</span>(<span class='str'>\"Call: \"</span> + call.owner + <span class='str'>\".\"</span> + call.name);\n" +
                "});</pre>\n" +
                "<h2>Execution</h2>\n" +
                "<pre><span class='cmt'>// Run entire simulation</span>\n" +
                "simulation.<span class='fn'>run</span>();\n" +
                "\n" +
                "<span class='cmt'>// Or step through</span>\n" +
                "<span class='kw'>while</span> (simulation.<span class='fn'>step</span>()) {\n" +
                "    <span class='kw'>let</span> state = simulation.<span class='fn'>getState</span>();\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Collect execution trace</span>\n" +
                "<span class='kw'>let</span> trace = simulation.<span class='fn'>trace</span>();</pre>\n";
    }

    public static String getInstrumentApi() {
        return getStylesheet() +
                "<h1>Instrumentation API</h1>\n" +
                "<p>The <code>instrument</code> object modifies bytecode:</p>\n" +
                "<pre><span class='cmt'>// Add hooks before/after calls</span>\n" +
                "instrument.<span class='fn'>beforeCall</span>({\n" +
                "    target: <span class='str'>\"java/sql/Statement.execute*\"</span>,\n" +
                "    inject: (call) => <span class='fn'>log</span>(<span class='str'>\"SQL: \"</span> + call.name)\n" +
                "});\n" +
                "\n" +
                "<span class='cmt'>// Replace calls</span>\n" +
                "instrument.<span class='fn'>replaceCall</span>({\n" +
                "    target: <span class='str'>\"System.out.println\"</span>,\n" +
                "    with: (call) => <span class='fn'>log</span>(<span class='str'>\"Replaced println\"</span>)\n" +
                "});\n" +
                "\n" +
                "<span class='cmt'>// Remove instructions</span>\n" +
                "instrument.<span class='fn'>removeInstruction</span>({\n" +
                "    filter: (instr) => instr.type == <span class='str'>\"InvokeInstruction\"</span>\n" +
                "});\n" +
                "\n" +
                "<span class='cmt'>// Apply to method</span>\n" +
                "<span class='kw'>let</span> mods = instrument.<span class='fn'>apply</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Made \"</span> + mods + <span class='str'>\" modifications\"</span>);</pre>\n";
    }

    public static String getTypesApi() {
        return getStylesheet() +
                "<h1>Types API</h1>\n" +
                "<p>The <code>types</code> object provides type analysis:</p>\n" +
                "<pre><span class='cmt'>// Analyze a method</span>\n" +
                "types.<span class='fn'>analyze</span>(<span class='str'>\"com/example/Service.process\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Get all variables</span>\n" +
                "<span class='kw'>let</span> vars = types.<span class='fn'>getVariables</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get type of specific variable</span>\n" +
                "<span class='kw'>let</span> type = types.<span class='fn'>getType</span>(<span class='str'>\"v0\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find all casts and allocations</span>\n" +
                "<span class='kw'>let</span> casts = types.<span class='fn'>findCasts</span>();\n" +
                "<span class='kw'>let</span> allocs = types.<span class='fn'>findAllocations</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get return and parameter types</span>\n" +
                "<span class='kw'>let</span> retType = types.<span class='fn'>getReturnType</span>();\n" +
                "<span class='kw'>let</span> params = types.<span class='fn'>getParameterTypes</span>();\n" +
                "\n" +
                "<span class='cmt'>// Find variables by type</span>\n" +
                "<span class='kw'>let</span> strings = types.<span class='fn'>findByType</span>(<span class='str'>\"String\"</span>);</pre>\n";
    }

    public static String getStringsApi() {
        return getStylesheet() +
                "<h1>Strings API</h1>\n" +
                "<p>The <code>strings</code> object analyzes string constants:</p>\n" +
                "<pre><span class='cmt'>// Extract all strings</span>\n" +
                "strings.<span class='fn'>extract</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get all/unique strings</span>\n" +
                "<span class='kw'>let</span> all = strings.<span class='fn'>getAll</span>();\n" +
                "<span class='kw'>let</span> unique = strings.<span class='fn'>unique</span>();\n" +
                "\n" +
                "<span class='cmt'>// Search strings</span>\n" +
                "<span class='kw'>let</span> matches = strings.<span class='fn'>find</span>(<span class='str'>\"password\"</span>);\n" +
                "<span class='kw'>let</span> regex = strings.<span class='fn'>findRegex</span>(<span class='str'>\"api[_-]?key\"</span>);\n" +
                "\n" +
                "<span class='cmt'>// Find specific patterns</span>\n" +
                "<span class='kw'>let</span> urls = strings.<span class='fn'>findUrls</span>();\n" +
                "<span class='kw'>let</span> paths = strings.<span class='fn'>findPaths</span>();\n" +
                "<span class='kw'>let</span> sql = strings.<span class='fn'>findSql</span>();\n" +
                "<span class='kw'>let</span> secrets = strings.<span class='fn'>findSecrets</span>();\n" +
                "\n" +
                "<span class='cmt'>// Group by class</span>\n" +
                "<span class='kw'>let</span> byClass = strings.<span class='fn'>groupByClass</span>();</pre>\n";
    }

    public static String getPipelineApi() {
        return getStylesheet() +
                "<h1>Pipeline API</h1>\n" +
                "<p>The <code>pipeline</code> object creates multi-stage workflows:</p>\n" +
                "<pre><span class='cmt'>// Create pipeline with stages</span>\n" +
                "pipeline\n" +
                "    .<span class='fn'>stage</span>(<span class='str'>\"Build\"</span>, () => {\n" +
                "        callgraph.<span class='fn'>build</span>();\n" +
                "        dependencies.<span class='fn'>build</span>();\n" +
                "        <span class='kw'>return</span> <span class='str'>\"built\"</span>;\n" +
                "    })\n" +
                "    .<span class='fn'>stage</span>(<span class='str'>\"Analyze\"</span>, (prev) => {\n" +
                "        <span class='kw'>let</span> dead = callgraph.<span class='fn'>findDeadMethods</span>();\n" +
                "        <span class='kw'>let</span> cycles = dependencies.<span class='fn'>findCycles</span>();\n" +
                "        <span class='kw'>return</span> { dead: dead, cycles: cycles };\n" +
                "    })\n" +
                "    .<span class='fn'>stage</span>(<span class='str'>\"Report\"</span>, (prev) => {\n" +
                "        <span class='fn'>log</span>(<span class='str'>\"Dead methods: \"</span> + prev.dead.length);\n" +
                "        results.<span class='fn'>exportJson</span>(<span class='str'>\"report.json\"</span>);\n" +
                "    })\n" +
                "    .<span class='fn'>run</span>();\n" +
                "\n" +
                "<span class='cmt'>// Check status</span>\n" +
                "<span class='kw'>let</span> status = pipeline.<span class='fn'>getStatus</span>();\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Total time: \"</span> + pipeline.<span class='fn'>getTotalTime</span>() + <span class='str'>\"ms\"</span>);</pre>\n";
    }

    public static String getSecurityExample() {
        return getStylesheet() +
                "<h1>Example: Security Scanner</h1>\n" +
                "<pre><span class='cmt'>// Build analysis graphs</span>\n" +
                "callgraph.<span class='fn'>build</span>();\n" +
                "\n" +
                "<span class='cmt'>// Find SQL injection vulnerabilities</span>\n" +
                "<span class='kw'>let</span> sqlCalls = patterns.<span class='fn'>findMethodCalls</span>(<span class='str'>\"java/sql/Statement.execute*\"</span>);\n" +
                "\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> call <span class='kw'>of</span> sqlCalls) {\n" +
                "    dataflow.<span class='fn'>build</span>({ className: call.className, name: call.methodName });\n" +
                "    \n" +
                "    <span class='kw'>let</span> flows = dataflow.<span class='fn'>taintAnalysis</span>(<span class='str'>\"params\"</span>, <span class='str'>\"invokes\"</span>);\n" +
                "    <span class='kw'>if</span> (flows.length > <span class='num'>0</span>) {\n" +
                "        results.<span class='fn'>add</span>({\n" +
                "            type: <span class='str'>\"SQL_INJECTION\"</span>,\n" +
                "            severity: <span class='str'>\"HIGH\"</span>,\n" +
                "            location: call.className + <span class='str'>\".\"</span> + call.methodName\n" +
                "        });\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "<span class='cmt'>// Find hardcoded secrets</span>\n" +
                "strings.<span class='fn'>extract</span>();\n" +
                "<span class='kw'>let</span> secrets = strings.<span class='fn'>findSecrets</span>();\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> s <span class='kw'>of</span> secrets) {\n" +
                "    results.<span class='fn'>add</span>({\n" +
                "        type: <span class='str'>\"HARDCODED_SECRET\"</span>,\n" +
                "        severity: <span class='str'>\"MEDIUM\"</span>,\n" +
                "        location: s.className,\n" +
                "        value: s.value.<span class='fn'>substring</span>(<span class='num'>0</span>, <span class='num'>20</span>)\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Found \"</span> + results.<span class='fn'>count</span>() + <span class='str'>\" issues\"</span>);\n" +
                "results.<span class='fn'>exportJson</span>(<span class='str'>\"security-report.json\"</span>);</pre>\n";
    }

    public static String getDeadCodeExample() {
        return getStylesheet() +
                "<h1>Example: Dead Code Finder</h1>\n" +
                "<pre><span class='cmt'>// Build call graph</span>\n" +
                "callgraph.<span class='fn'>build</span>();\n" +
                "\n" +
                "<span class='cmt'>// Get entry points and find reachable code</span>\n" +
                "<span class='kw'>let</span> entries = callgraph.<span class='fn'>findEntryPoints</span>();\n" +
                "<span class='kw'>let</span> reachable = callgraph.<span class='fn'>getReachableFrom</span>(entries);\n" +
                "\n" +
                "<span class='cmt'>// Find unreachable methods</span>\n" +
                "<span class='kw'>let</span> allMethods = callgraph.<span class='fn'>getAllMethods</span>();\n" +
                "<span class='kw'>let</span> reachableNames = reachable.<span class='fn'>map</span>((m) => m.fullName);\n" +
                "\n" +
                "<span class='kw'>for</span> (<span class='kw'>let</span> method <span class='kw'>of</span> allMethods) {\n" +
                "    <span class='kw'>if</span> (!reachableNames.<span class='fn'>includes</span>(method.fullName)) {\n" +
                "        results.<span class='fn'>add</span>({\n" +
                "            type: <span class='str'>\"DEAD_CODE\"</span>,\n" +
                "            method: method.fullName\n" +
                "        });\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "<span class='fn'>log</span>(<span class='str'>\"Found \"</span> + results.<span class='fn'>count</span>() + <span class='str'>\" dead methods\"</span>);\n" +
                "<span class='fn'>log</span>(results.<span class='fn'>toTable</span>());</pre>\n";
    }

    public static String[] getSectionTitles() {
        return new String[] {
                "Overview",
                "Language",
                "  Loops",
                "  Array Methods",
                "  Try/Catch",
                "Analysis APIs",
                "  Results",
                "  Project",
                "  Call Graph",
                "  Data Flow",
                "  Dependencies",
                "  Patterns",
                "Advanced APIs",
                "  Simulation",
                "  Instrumentation",
                "  Types",
                "  Strings",
                "  Pipeline",
                "Examples",
                "  Security Scanner",
                "  Dead Code Finder"
        };
    }

    public static String getContentForSection(String section) {
        switch (section) {
            case "Overview":
                return getOverview();
            case "  Loops":
                return getLoops();
            case "  Array Methods":
                return getArrayMethods();
            case "  Try/Catch":
                return getTryCatch();
            case "  Results":
                return getResultsApi();
            case "  Project":
                return getProjectApi();
            case "  Call Graph":
                return getCallGraphApi();
            case "  Data Flow":
                return getDataFlowApi();
            case "  Dependencies":
                return getDependencyApi();
            case "  Patterns":
                return getPatternApi();
            case "  Simulation":
                return getSimulationApi();
            case "  Instrumentation":
                return getInstrumentApi();
            case "  Types":
                return getTypesApi();
            case "  Strings":
                return getStringsApi();
            case "  Pipeline":
                return getPipelineApi();
            case "  Security Scanner":
                return getSecurityExample();
            case "  Dead Code Finder":
                return getDeadCodeExample();
            case "Language":
                return getLoops();
            case "Analysis APIs":
                return getResultsApi();
            case "Advanced APIs":
                return getSimulationApi();
            case "Examples":
                return getSecurityExample();
            default:
                return getOverview();
        }
    }
}
