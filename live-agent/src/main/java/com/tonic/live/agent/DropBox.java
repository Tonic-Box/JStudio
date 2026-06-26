package com.tonic.live.agent;

/**
 * Hand-off slot between the JDI debugger and the in-process agent. When JDI is attached it parks a snapshot of
 * object references here over JDWP (a class's complete instance set, or stack-frame-held roots); the agent then
 * reads them in-process and uses its fast reflection. The agent clears the slot after each consume, so the
 * strong reference only pins the set for the brief consume window.
 *
 * <p>Lives under {@code com.tonic.live.agent} so it is hidden from the live class browser (the agent filters
 * that prefix) and so JDI can resolve it via {@code classesByName} on the agent's classloader.
 */
public final class DropBox {

    /** References parked by JDI for the agent to consume; null when empty. */
    public static volatile Object[] BOX;

    private DropBox() {
    }
}
