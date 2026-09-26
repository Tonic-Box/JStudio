package com.tonic.live.debug;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.util.Map;

/** Opens JDI connections to JVMs already serving JDWP over the socket transport. */
public final class DebugConnector
{

    private DebugConnector()
    {
    }

    /**
     * Attaches JDI to a JDWP socket address the target is serving.
     *
     * @param host the target's host
     * @param port the JDWP port
     * @return the attached virtual machine
     * @throws IOException if this JDK has no socket attach connector, or the attach fails
     */
    public static VirtualMachine attach(String host, int port) throws IOException
    {
        AttachingConnector connector = socketAttachConnector();
        if (connector == null)
        {
            throw new IOException("no JDI SocketAttach connector available on this JDK");
        }
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(Integer.toString(port));
        try
        {
            return connector.attach(args);
        }
        catch (Exception e)
        {
            throw new IOException("JDI attach to " + host + ":" + port + " failed: " + e.getMessage(), e);
        }
    }

    private static AttachingConnector socketAttachConnector()
    {
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors())
        {
            if ("com.sun.jdi.SocketAttach".equals(c.name()))
            {
                return c;
            }
        }
        return null;
    }
}
