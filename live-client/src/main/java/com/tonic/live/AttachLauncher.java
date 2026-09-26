package com.tonic.live;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** Discovers local JVMs and loads agents into them through the Attach API; on JDK 21 and later dynamic loading prints a warning but is still allowed. */
public final class AttachLauncher
{

    private AttachLauncher()
    {
    }

    /** A locally attachable JVM. */
    @Getter
    public static final class JvmProcess
    {
        private final String id;
        private final String displayName;

        /**
         * Creates a process entry.
         *
         * @param id the process id
         * @param displayName the JVM's display name
         */
        public JvmProcess(String id, String displayName)
        {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return id + "  " + displayName;
        }
    }

    /**
     * Lists the JVMs attachable on this machine.
     *
     * @return the processes, with "(unknown)" for a missing display name
     */
    public static List<JvmProcess> listJvms()
    {
        List<JvmProcess> result = new ArrayList<>();
        for (VirtualMachineDescriptor vmd : VirtualMachine.list())
        {
            String name = vmd.displayName();
            result.add(new JvmProcess(vmd.id(), name == null || name.isEmpty() ? "(unknown)" : name));
        }
        return result;
    }

    /**
     * Attaches to a process, loads the Java agent jar told to listen on a port, and detaches; the agent keeps running.
     *
     * @param pid the target process id
     * @param agentJarPath the agent jar's path
     * @param port the loopback port the agent should listen on
     * @throws Exception if the attach or the agent load fails
     */
    public static void loadAgent(String pid, String agentJarPath, int port) throws Exception
    {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try
        {
            vm.loadAgent(agentJarPath, "port=" + port);
        }
        finally
        {
            vm.detach();
        }
    }

    /**
     * Late-loads the JDK's JDWP agent into a process so a JDI debugger can attach over a loopback socket.
     *
     * @param pid the target process id
     * @param port the loopback port JDWP should serve
     * @throws Exception if the attach or the load fails, as on JVMs that block agent loading
     */
    public static void loadJdwp(String pid, int port) throws Exception
    {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try
        {
            vm.loadAgentLibrary("jdwp", "transport=dt_socket,server=y,suspend=n,address=127.0.0.1:" + port);
        }
        finally
        {
            vm.detach();
        }
    }
}
