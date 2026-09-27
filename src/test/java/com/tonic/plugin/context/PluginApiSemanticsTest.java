package com.tonic.plugin.context;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.builder.ClassBuilder;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassPool;
import com.tonic.plugin.api.AnalysisApi;
import com.tonic.plugin.api.ProjectApi;
import com.tonic.plugin.result.Finding;
import com.tonic.plugin.result.ResultCollector;
import com.tonic.type.AccessFlags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the plugin API answers what its documentation promises")
class PluginApiSemanticsTest implements AccessFlags
{

    private ProjectModel project;

    @BeforeEach
    void setUp()
    {
        project = new ProjectModel();
        project.setClassPool(new ClassPool(true));
        project.addClass(ClassBuilder.create("a/b/Target").access(ACC_PUBLIC).interfaces("a/b/Api").addField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "ANSWER", "I").constantValue(42).end().addField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "NAME", "Ljava/lang/String;").constantValue("jstudio").end().addField(ACC_PUBLIC, "count", "I").end().addMethod(ACC_PUBLIC | ACC_STATIC, "foo", "(I)V").code().vreturn().end().end().addMethod(ACC_PUBLIC | ACC_STATIC, "foo", "(J)V").code().vreturn().end().end().addMethod(ACC_PUBLIC, "touch", "()I").code().aload(0).getfield("a/b/Target", "count", "I").ireturn().end().end().build());
        project.addClass(ClassBuilder.create("a/b/Api").access(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT).interfaces("a/b/Root").build());
        project.addClass(ClassBuilder.create("a/b/Root").access(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT).build());
        project.addClass(ClassBuilder.create("a/b/c/Deeper").access(ACC_PUBLIC).superClass("a/b/Target").build());
        project.addClass(ClassBuilder.create("x/BarTarget").access(ACC_PUBLIC).addMethod(ACC_PUBLIC | ACC_STATIC, "callInt", "()V").code().iconst(1).invokestatic("a/b/Target", "foo", "(I)V").vreturn().end().end().addMethod(ACC_PUBLIC | ACC_STATIC, "callLong", "()V").code().lconst(1).invokestatic("a/b/Target", "foo", "(J)V").vreturn().end().end().build());
    }

    @Test
    @DisplayName("a package lookup matches the exact package, dotted or with slashes")
    void packageLookupIsExact()
    {
        ProjectApi api = new ProjectApiImpl(project);

        List<String> dotted = api.getClassesInPackage("a.b").stream().map(ProjectApi.ClassInfo::getName).sorted().collect(Collectors.toList());

        assertEquals(List.of("a/b/Api", "a/b/Root", "a/b/Target"), dotted);
        assertEquals(dotted.size(), api.getClassesInPackage("a/b").size());
    }

    @Test
    @DisplayName("a simple-name lookup matches whole name segments only")
    void simpleNameMatchesSegments()
    {
        assertEquals("a/b/Target", project.findClassByName("Target").getClassName());
        assertEquals("x/BarTarget", project.findClassByName("BarTarget").getClassName());
        assertNull(project.findClassByName("arTarget"));
    }

    @Test
    @DisplayName("method info counts instructions and returns a copy of the code")
    void methodInfo()
    {
        ProjectApi.MethodInfo touch = new ProjectApiImpl(project).getMethod("a/b/Target", "touch", "()I").orElseThrow();

        assertEquals(3, touch.getInstructionCount());
        byte[] code = touch.getBytecode();
        byte[] original = code.clone();
        code[0] = 0;
        assertArrayEquals(original, touch.getBytecode());
    }

    @Test
    @DisplayName("field info reads constant initializers")
    void fieldConstants()
    {
        Map<String, Object> constants = new HashMap<>();
        for (ProjectApi.FieldInfo field : new ProjectApiImpl(project).getClass("a/b/Target").orElseThrow().getFields())
        {
            constants.put(field.getName(), field.getConstantValue());
        }

        assertEquals(42, constants.get("ANSWER"));
        assertEquals("jstudio", constants.get("NAME"));
        assertNull(constants.get("count"));
    }

    @Test
    @DisplayName("call queries cover every overload of a method name")
    void callersCoverOverloads()
    {
        AnalysisApi.CallGraphApi calls = new AnalysisApiImpl(project).getCallGraph();

        List<String> callers = calls.getCallersOf("a.b.Target", "foo").stream().map(AnalysisApi.CallSite::getCallerMethod).sorted().collect(Collectors.toList());

        assertEquals(List.of("callInt", "callLong"), callers);
    }

    @Test
    @DisplayName("field and method searches honour owner and wildcard patterns")
    void patternSearches()
    {
        AnalysisApi.PatternApi patterns = new AnalysisApiImpl(project).getPatterns();

        assertEquals(1, patterns.findFieldAccess("a/b/*", "cou*").size());
        assertEquals(0, patterns.findFieldAccess("x/*", "count").size());
        assertEquals(2, patterns.findMethodCalls("a/b/Tar*", "foo").size());
    }

    @Test
    @DisplayName("type walks include superinterfaces and indirect subtypes")
    void typeWalks()
    {
        AnalysisApi.TypeApi types = new AnalysisApiImpl(project).getTypes();

        assertTrue(types.getSupertypes("a/b/c/Deeper").contains("a/b/Root"));
        assertTrue(types.isSubtypeOf("a.b.c.Deeper", "a.b.Root"));
        assertTrue(types.getSubtypes("a/b/Root").contains("a/b/c/Deeper"));
    }

    @Test
    @DisplayName("custom patterns see the classes' members")
    void customPatternSeesMembers()
    {
        AnalysisApi.PatternApi patterns = new AnalysisApiImpl(project).getPatterns();
        List<Integer> methodCounts = new ArrayList<>();
        patterns.registerCustomPattern("count", cls ->
        {
            if (cls.getName().equals("a/b/Target"))
            {
                methodCounts.add(cls.getMethods().size());
            }
            return List.of();
        });

        patterns.findPattern("count");

        assertFalse(methodCounts.isEmpty());
        assertTrue(methodCounts.get(0) >= 3);
    }

    @Test
    @DisplayName("finding metadata rejects null keys and leaves out null values")
    void metadataNulls()
    {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kept", 1);
        metadata.put("dropped", null);

        Finding finding = Finding.builder().message("m").metadata(metadata).build();

        assertEquals(Map.of("kept", 1), finding.getMetadata());
        assertThrows(NullPointerException.class, () -> Finding.builder().addMetadata(null, 1));
    }

    @Test
    @DisplayName("merging stamps the plugin id and notifies listeners; clear removes data too")
    void collectorMergeAndClear()
    {
        ResultCollector target = new ResultCollector("plugin-id");
        List<Finding> seen = new ArrayList<>();
        target.addListener(seen::add);
        ResultCollector other = new ResultCollector();
        other.add(Finding.builder().message("m").build());
        other.setData("k", "v");

        target.merge(other);

        assertEquals(1, seen.size());
        assertEquals("plugin-id", target.getFindings().get(0).getPluginId());
        target.clear();
        assertTrue(target.getAllData().isEmpty());
    }

    @Test
    @DisplayName("yabr access removes classes from the project and lifts IR on demand")
    void yabrAccess()
    {
        YabrAccessImpl yabr = new YabrAccessImpl(project);

        assertTrue(yabr.liftToIR("a/b/Target", "touch", "()I").isPresent());
        yabr.removeClass("x/BarTarget");
        assertNull(project.findClassByName("x/BarTarget"));
    }
}
