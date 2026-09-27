package com.tonic.simulation.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.analysis.simulation.core.SimulationContext;
import com.tonic.analysis.simulation.core.SimulationEngine;
import com.tonic.analysis.simulation.listener.SimulationListener;
import com.tonic.analysis.ssa.SSA;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.type.AccessFlags;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("simulation listeners report only what the method's values prove")
class SimulationListenerTest implements AccessFlags
{

    private static final String QUERY = "(Ljava/lang/String;)Ljava/sql/ResultSet;";

    private ClassPool pool;
    private ClassFile cf;

    @BeforeEach
    void setUp()
    {
        pool = new ClassPool(true);
        cf = ClassBuilder.create("a/Sim").access(ACC_PUBLIC)
                .addMethod(ACC_PUBLIC | ACC_STATIC, "opaque", "(I)I").code().iconst(3).iconst(2).if_icmplt("done").iload(0).ifgt("done").label("done").iconst(0).ireturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "safe", "(Ljava/lang/String;)V").code().aconst_null().ldc("select 1").invokeinterface("java/sql/Statement", "executeQuery", QUERY).pop().vreturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "unsafe", "(Ljava/lang/String;)V").code().aconst_null().aload(0).invokeinterface("java/sql/Statement", "executeQuery", QUERY).pop().vreturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "fromEnv", "()V").code().ldc("X").invokestatic("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;").astore(0).aconst_null().aload(0).invokeinterface("java/sql/Statement", "executeQuery", QUERY).pop().vreturn().end().end()
                .build();
        pool.put(cf);
    }

    private void simulate(String name, SimulationListener listener)
    {
        SimulationEngine engine = new SimulationEngine(SimulationContext.defaults().withClassPool(pool).withValueTracking(true).withStackOperationTracking(true));
        engine.addListener(listener);
        engine.simulate(new SSA(cf.getConstPool()).lift(cf.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow()));
    }

    @Test
    @DisplayName("only a branch decided by constants is opaque, located at its own bytecode offset")
    void onlyConstantBranchIsOpaque()
    {
        OpaquePredicateListener listener = new OpaquePredicateListener();

        simulate("opaque", listener);

        List<OpaquePredicateListener.BranchAnalysis> opaque = listener.getOpaquePredicates();
        assertEquals(1, opaque.size());
        assertTrue(opaque.get(0).isAlwaysFalse());
        assertEquals(2, opaque.get(0).getBytecodeOffset());
    }

    @Test
    @DisplayName("a sink given only constants is not a taint flow even when the method has parameters")
    void untaintedSinkIsIgnored()
    {
        TaintTrackingListener listener = new TaintTrackingListener();

        simulate("safe", listener);

        assertEquals(0, listener.getTaintFlowCount());
    }

    @Test
    @DisplayName("a parameter reaching a sink is reported with its origin")
    void parameterReachesSink()
    {
        TaintTrackingListener listener = new TaintTrackingListener();

        simulate("unsafe", listener);

        assertEquals(1, listener.getTaintFlowCount());
        assertTrue(listener.getTaintFlows().get(0).getSourceDescription().startsWith("Method parameter 0"));
    }

    @Test
    @DisplayName("a source called with untainted arguments still taints its result")
    void sourceTaintsWithoutTaintedArguments()
    {
        TaintTrackingListener listener = new TaintTrackingListener();

        simulate("fromEnv", listener);

        assertEquals(1, listener.getTaintFlowCount());
        assertTrue(listener.getTaintFlows().get(0).getSourceDescription().contains("getenv"));
    }
}
