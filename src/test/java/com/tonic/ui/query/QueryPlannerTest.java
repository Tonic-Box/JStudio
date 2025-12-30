package com.tonic.ui.query;

import com.tonic.ui.core.util.ComparisonOperator;
import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.parser.ParseException;
import com.tonic.ui.query.parser.QueryParser;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.QueryPlanner;
import com.tonic.ui.query.planner.probe.ProbeSet;
import com.tonic.ui.query.planner.probe.ProbeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryPlannerTest {

    private QueryParser parser;
    private QueryPlanner planner;

    @BeforeEach
    void setUp() {
        parser = new QueryParser();
        planner = new QueryPlanner(null);
    }

    private Query parse(String dsl) throws ParseException {
        return parser.parse(dsl);
    }

    @Test
    void testParseCallsPredicate() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"java/io/PrintStream.println\")");
        assertNotNull(query);
        assertInstanceOf(FindQuery.class, query);
        assertEquals(Target.METHODS, query.target());
        assertNotNull(query.predicate());
        assertInstanceOf(CallsPredicate.class, query.predicate());

        CallsPredicate cp = (CallsPredicate) query.predicate();
        assertEquals("java/io/PrintStream", cp.ownerClass());
        assertEquals("println", cp.methodName());
    }

    @Test
    void testPlanCallsQuery() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"java/io/PrintStream.println\")");
        ProbePlan plan = planner.plan(query);

        assertNotNull(plan);
        assertNotNull(plan.probes());
        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.CALL));

        var callProbes = plan.probes().getProbesOfType(ProbeSpec.CallProbe.class);
        assertEquals(1, callProbes.size());
        assertEquals("java/io/PrintStream", callProbes.get(0).targetOwner());
        assertEquals("println", callProbes.get(0).targetName());
    }

    @Test
    void testParseAllocCountPredicate() throws ParseException {
        Query query = parse("FIND methods WHERE allocCount(\"java/lang/StringBuilder\") > 5");
        assertNotNull(query);
        assertInstanceOf(AllocCountPredicate.class, query.predicate());

        AllocCountPredicate acp = (AllocCountPredicate) query.predicate();
        assertEquals("java/lang/StringBuilder", acp.typeName());
        assertEquals(ComparisonOperator.GT, acp.operator());
        assertEquals(5, acp.threshold());
    }

    @Test
    void testPlanAllocCountQuery() throws ParseException {
        Query query = parse("FIND methods WHERE allocCount(\"java/lang/StringBuilder\") >= 10");
        ProbePlan plan = planner.plan(query);

        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.ALLOCATION));
        var allocProbes = plan.probes().getProbesOfType(ProbeSpec.AllocProbe.class);
        assertEquals(1, allocProbes.size());
        assertEquals("java/lang/StringBuilder", allocProbes.get(0).typeName());
    }

    @Test
    void testParseContainsStringPredicate() throws ParseException {
        Query query = parse("FIND methods WHERE containsString(\"password\")");
        assertNotNull(query);
        assertInstanceOf(ContainsStringPredicate.class, query.predicate());

        ContainsStringPredicate cs = (ContainsStringPredicate) query.predicate();
        assertEquals("password", cs.pattern());
        assertFalse(cs.isRegex());
    }

    @Test
    void testPlanContainsStringQuery() throws ParseException {
        Query query = parse("FIND methods WHERE containsString(\"secret\")");
        ProbePlan plan = planner.plan(query);

        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.STRING));
        var stringProbes = plan.probes().getProbesOfType(ProbeSpec.StringProbe.class);
        assertEquals(1, stringProbes.size());
        assertEquals("secret", stringProbes.get(0).pattern());
        assertFalse(stringProbes.get(0).isRegex());
    }

    @Test
    void testParseAndPredicate() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"X.foo\") AND allocCount(\"Y\") > 0");
        assertNotNull(query);
        assertInstanceOf(AndPredicate.class, query.predicate());

        AndPredicate and = (AndPredicate) query.predicate();
        assertInstanceOf(CallsPredicate.class, and.left());
        assertInstanceOf(AllocCountPredicate.class, and.right());
    }

    @Test
    void testPlanAndPredicateQuery() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"X.foo\") AND containsString(\"bar\")");
        ProbePlan plan = planner.plan(query);

        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.CALL));
        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.STRING));
        assertEquals(2, plan.probes().getProbes().size());
    }

    @Test
    void testParseClassScope() throws ParseException {
        Query query = parse("FIND methods IN class \"com/example/.*\" WHERE calls(\"X.y\")");
        assertNotNull(query.scope());
        assertInstanceOf(ClassScope.class, query.scope());

        ClassScope cs = (ClassScope) query.scope();
        assertEquals("com/example/.*", cs.pattern());
    }

    @Test
    void testParseMethodScope() throws ParseException {
        Query query = parse("FIND methods IN method \".*init.*\" WHERE allocCount(\"Z\") > 0");
        assertNotNull(query.scope());
        assertInstanceOf(MethodScope.class, query.scope());

        MethodScope ms = (MethodScope) query.scope();
        assertEquals(".*init.*", ms.pattern());
    }

    @Test
    void testParseThrowsPredicate() throws ParseException {
        Query query = parse("FIND methods WHERE throws(\"java/lang/NullPointerException\")");
        assertInstanceOf(ThrowsPredicate.class, query.predicate());

        ThrowsPredicate tp = (ThrowsPredicate) query.predicate();
        assertEquals("java/lang/NullPointerException", tp.exceptionType());
    }

    @Test
    void testPlanThrowsQuery() throws ParseException {
        Query query = parse("FIND methods WHERE throws(\"java/lang/Exception\")");
        ProbePlan plan = planner.plan(query);

        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.EXCEPTION));
        var exceptionProbes = plan.probes().getProbesOfType(ProbeSpec.ExceptionProbe.class);
        assertEquals(1, exceptionProbes.size());
        assertEquals("java/lang/Exception", exceptionProbes.get(0).exceptionType());
    }

    @Test
    void testParseFieldWritePredicate() throws ParseException {
        Query query = parse("FIND methods WHERE writesField(\"com/Foo.bar\")");
        assertInstanceOf(WritesFieldPredicate.class, query.predicate());

        WritesFieldPredicate wf = (WritesFieldPredicate) query.predicate();
        assertEquals("com/Foo", wf.ownerClass());
        assertEquals("bar", wf.fieldName());
    }

    @Test
    void testPlanFieldWriteQuery() throws ParseException {
        Query query = parse("FIND methods WHERE writesField(\"X.y\")");
        ProbePlan plan = planner.plan(query);

        assertTrue(plan.probes().hasProbeType(ProbeSpec.ProbeType.FIELD));
        var fieldProbes = plan.probes().getProbesOfType(ProbeSpec.FieldProbe.class);
        assertEquals(1, fieldProbes.size());
        assertTrue(fieldProbes.get(0).trackWrites());
        assertFalse(fieldProbes.get(0).trackReads());
    }

    @Test
    void testParseDuringClinitScope() throws ParseException {
        Query query = parse("FIND methods DURING <clinit> WHERE calls(\"X.foo\")");
        assertInstanceOf(DuringScope.class, query.scope());

        DuringScope ds = (DuringScope) query.scope();
        assertTrue(ds.isClinit());
    }

    @Test
    void testParseLimit() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"X.y\") LIMIT 100");
        assertNotNull(query.limit());
        assertEquals(100, query.limit().intValue());
    }

    @Test
    void testShowQuery() throws ParseException {
        Query query = parse("SHOW strings WHERE containsString(\"http\")");
        assertInstanceOf(ShowQuery.class, query);
        assertEquals(Target.STRINGS, query.target());
    }

    @Test
    void testFindObjectsTarget() throws ParseException {
        Query query = parse("FIND objects WHERE allocCount(\"X\") > 0");
        assertEquals(Target.OBJECTS, query.target());
    }

    @Test
    void testPlanRequiredCapabilities() throws ParseException {
        Query query = parse("FIND methods WHERE calls(\"X.y\") AND containsString(\"z\")");
        ProbePlan plan = planner.plan(query);

        var caps = plan.probes().getRequiredCapabilities();
        assertTrue(caps.contains(ProbeSet.Capability.CALL_TRACKING));
        assertTrue(caps.contains(ProbeSet.Capability.STRING_TRACKING));
    }
}
