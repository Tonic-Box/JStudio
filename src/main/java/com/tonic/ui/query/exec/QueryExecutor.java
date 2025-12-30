package com.tonic.ui.query.exec;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.probe.ProbeResult;
import com.tonic.ui.vm.VMExecutionService;

import java.util.Arrays;
import java.util.Objects;

public class QueryExecutor {

    private final VMExecutionService vmService;
    private int maxInstructionsPerRun = 100_000;

    public QueryExecutor() {
        this.vmService = VMExecutionService.getInstance();
    }

    public QueryExecutor(VMExecutionService vmService) {
        this.vmService = vmService;
    }

    public void setMaxInstructionsPerRun(int max) {
        this.maxInstructionsPerRun = max;
    }

    public QueryExecutionResult execute(ProbePlan plan, MethodEntry method, Object[] args) {
        if (!vmService.isInitialized()) {
            vmService.initialize();
        }

        String methodSig = method.getOwnerName() + "." + method.getName() + method.getDesc();
        QueryProbeListener probeListener = new QueryProbeListener(plan.probes(), methodSig);

        long startTime = System.currentTimeMillis();

        try {
            vmService.setMaxInstructions(maxInstructionsPerRun);

            BytecodeResult bytecodeResult = vmService.executeMethodWithListener(
                method, args, probeListener
            );

            long endTime = System.currentTimeMillis();
            ProbeResult probeResult = probeListener.buildResult();

            boolean passesPostFilter = plan.postFilter().test(probeResult);

            return new QueryExecutionResult(
                methodSig,
                args,
                probeResult,
                bytecodeResult.isSuccess(),
                bytecodeResult.hasException() ?
                    bytecodeResult.getException().toString() : null,
                endTime - startTime,
                passesPostFilter
            );

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            ProbeResult probeResult = probeListener.buildResult();

            return new QueryExecutionResult(
                methodSig,
                args,
                probeResult,
                false,
                e.getMessage(),
                endTime - startTime,
                false
            );
        }
    }

    public static final class QueryExecutionResult {
        private final String methodSignature;
        private final Object[] inputs;
        private final ProbeResult probeResult;
        private final boolean executionSucceeded;
        private final String error;
        private final long executionTimeMs;
        private final boolean passesPostFilter;

        public QueryExecutionResult(String methodSignature, Object[] inputs, ProbeResult probeResult,
                                    boolean executionSucceeded, String error, long executionTimeMs,
                                    boolean passesPostFilter) {
            this.methodSignature = methodSignature;
            this.inputs = inputs;
            this.probeResult = probeResult;
            this.executionSucceeded = executionSucceeded;
            this.error = error;
            this.executionTimeMs = executionTimeMs;
            this.passesPostFilter = passesPostFilter;
        }

        public String methodSignature() { return methodSignature; }
        public Object[] inputs() { return inputs; }
        public ProbeResult probeResult() { return probeResult; }
        public boolean executionSucceeded() { return executionSucceeded; }
        public String error() { return error; }
        public long executionTimeMs() { return executionTimeMs; }
        public boolean passesPostFilter() { return passesPostFilter; }

        public boolean isMatch() {
            return passesPostFilter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueryExecutionResult)) return false;
            QueryExecutionResult that = (QueryExecutionResult) o;
            return executionSucceeded == that.executionSucceeded &&
                   executionTimeMs == that.executionTimeMs &&
                   passesPostFilter == that.passesPostFilter &&
                   Objects.equals(methodSignature, that.methodSignature) &&
                   Arrays.equals(inputs, that.inputs) &&
                   Objects.equals(probeResult, that.probeResult) &&
                   Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(methodSignature, probeResult, executionSucceeded, error, executionTimeMs, passesPostFilter);
            result = 31 * result + Arrays.hashCode(inputs);
            return result;
        }
    }
}
