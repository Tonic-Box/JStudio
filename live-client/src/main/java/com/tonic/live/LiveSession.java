package com.tonic.live;

import com.tonic.live.protocol.AgentInfo;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.protocol.LiveEvent;
import com.tonic.live.protocol.LoadedClass;
import com.tonic.live.protocol.MetricsSnapshot;
import com.tonic.live.protocol.StaticField;
import com.tonic.live.protocol.StaticMethod;
import com.tonic.live.protocol.ThreadInfo;
import com.tonic.live.protocol.ThreadStack;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A live debugging session against one target JVM: loads the pure-Java agent (via attach) and holds the
 * connected {@link LiveAgentClient}. The Java agent supports class browse, live method-body patch
 * (redefine), runtime class capture, the thread list, and deadlock detection.
 *
 * <p>Byte fetching is lazy by design (callers pull a class's bytes on demand); enumeration is eager and
 * cheap (names + access flags). Not thread-safe - the underlying connection is serial.
 */
@Getter
public final class LiveSession implements Closeable {

    private final String pid;
    private final AgentInfo info;
    private final LiveAgentClient client;

    private LiveSession(String pid, AgentInfo info, LiveAgentClient client) {
        this.pid = pid;
        this.info = info;
        this.client = client;
    }

    /**
     * Attaches to {@code pid}, loads the Java agent jar (via {@code java.lang.instrument}), connects, and
     * performs the handshake. A free loopback port is chosen automatically.
     */
    public static LiveSession attach(String pid, String agentJarPath) throws Exception {
        int port = freePort();
        AttachLauncher.loadAgent(pid, agentJarPath, port);
        LiveAgentClient client = LiveAgentClient.connect("127.0.0.1", port, 10_000);
        try {
            AgentInfo info = client.hello();
            return new LiveSession(pid, info, client);
        } catch (IOException e) {
            client.close();
            throw e;
        }
    }

    /** Eagerly enumerate all loaded classes (names + access flags); cheap, no bytecode transferred. */
    public List<LoadedClass> enumerateClasses() throws IOException {
        return client.listClasses();
    }

    /** Pull one class's current bytecode from the target ("com/foo/Bar"). */
    public byte[] fetchClassBytes(String internalName) throws IOException {
        return client.getClassBytes(internalName);
    }

    public void setEventListener(Consumer<LiveEvent> listener) {
        client.setEventListener(listener);
    }

    public void addEventListener(Consumer<LiveEvent> listener) {
        client.addEventListener(listener);
    }

    public void removeEventListener(Consumer<LiveEvent> listener) {
        client.removeEventListener(listener);
    }

    public List<ThreadInfo> getThreads() throws IOException {
        return client.getThreads();
    }

    /** Live method-body redefinition: replace {@code internalName}'s bytecode in the target. */
    public void redefineClass(String internalName, byte[] classBytes) throws IOException {
        client.redefineClass(internalName, classBytes);
    }

    /** Arm/disarm streaming of runtime class loads (CLASS_LOADED events with real bytes). */
    public void setCaptureLoads(boolean on) throws IOException {
        client.setCaptureLoads(on);
    }

    /** Snapshot the wait-for graph for deadlock detection ({@link Deadlocks#find}). */
    public List<ContentionEdge> getContention() throws IOException {
        return client.getContention();
    }

    /** Triggers a HotSpot heap dump in the target; returns the local .hprof file path. */
    public String heapDump() throws IOException {
        return client.heapDump();
    }

    /** Reads the live static fields of a class. */
    public List<StaticField> getStatics(String internalName) throws IOException {
        return client.getStatics(internalName);
    }

    /** Sets a static field's value (or to null); returns the field's re-read value. */
    public String setStatic(String className, String field, boolean setNull, String value) throws IOException {
        return client.setStatic(className, field, setNull, value);
    }

    /** Lists the static methods of a class. */
    public List<StaticMethod> listStaticMethods(String internalName) throws IOException {
        return client.listStaticMethods(internalName);
    }

    /** Invokes a static method (args marshalled from strings); returns the formatted result. */
    public String invokeStatic(String className, String name, String desc, List<String> args) throws IOException {
        return client.invokeStatic(className, name, desc, args);
    }

    /**
     * Compiles-then-runs: defines a snippet class (its {@code static Object run()}) in the target, in a
     * throwaway child of {@code contextClass}'s classloader, and returns the captured output + result.
     */
    public String eval(Map<String, byte[]> classes, String mainBinaryName, String contextClass) throws IOException {
        return client.eval(classes, mainBinaryName, contextClass);
    }

    /** Snapshots all threads with their current stacks. */
    public List<ThreadStack> getThreadStacks(int maxDepth) throws IOException {
        return client.getThreadStacks(maxDepth);
    }

    /** Reads a snapshot of the target JVM's runtime metrics (memory, GC, CPU, threads, classes). */
    public MetricsSnapshot getMetrics() throws IOException {
        return client.getMetrics();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
