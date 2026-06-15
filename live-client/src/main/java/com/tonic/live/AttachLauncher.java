package com.tonic.live;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers local JVMs and loads the pure-Java JStudio Live agent into one via the Attach API
 * ({@code jdk.attach}). The agent's {@code agentmain} starts its TCP server on the given port; the caller
 * then connects with {@link LiveAgentClient}.
 *
 * <p>Note: on JDK 21+ dynamic agent loading prints a warning (JEP 451) but is still permitted.
 */
public final class AttachLauncher {

    private AttachLauncher() {
    }

    /** A locally attachable JVM. */
    @Getter
    public static final class JvmProcess {
        private final String id;
        private final String displayName;

        public JvmProcess(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return id + "  " + displayName;
        }
    }

    public static List<JvmProcess> listJvms() {
        List<JvmProcess> result = new ArrayList<>();
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            String name = vmd.displayName();
            result.add(new JvmProcess(vmd.id(), name == null || name.isEmpty() ? "(unknown)" : name));
        }
        return result;
    }

    /**
     * Attaches to {@code pid}, loads the Java agent jar ({@code java.lang.instrument}), and tells it to
     * listen on {@code port}. Detaches once loaded (the agent keeps running in the target).
     */
    public static void loadAgent(String pid, String agentJarPath, int port) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentJarPath, "port=" + port);
        } finally {
            vm.detach();
        }
    }
}
