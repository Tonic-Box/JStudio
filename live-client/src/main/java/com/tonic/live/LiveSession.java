package com.tonic.live;

import com.tonic.live.protocol.AgentInfo;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.protocol.LiveEvent;
import com.tonic.live.protocol.LiveField;
import com.tonic.live.protocol.LiveInstance;
import com.tonic.live.protocol.LoadedClass;
import com.tonic.live.protocol.MetricsSnapshot;
import com.tonic.live.protocol.ScanPage;
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

/** A live session against one target JVM, holding the connected agent client; not thread-safe, since the connection is serial. */
@Getter
public final class LiveSession implements Closeable
{

    private final String pid;
    private final AgentInfo info;
    private final LiveAgentClient client;

    private LiveSession(String pid, AgentInfo info, LiveAgentClient client)
    {
        this.pid = pid;
        this.info = info;
        this.client = client;
    }

    /**
     * Loads the Java agent into a process on a free loopback port, then connects and handshakes.
     *
     * @param pid the target process id
     * @param agentJarPath the agent jar's path
     * @return the connected session
     * @throws Exception if the attach, the agent load, or the connection fails
     */
    public static LiveSession attach(String pid, String agentJarPath) throws Exception
    {
        int port = freePort();
        AttachLauncher.loadAgent(pid, agentJarPath, port);
        return connect(pid, port);
    }

    /**
     * Connects to an agent already loaded and listening, retrying until a 10 second deadline, then handshakes.
     *
     * @param pid the target process id, recorded for display
     * @param port the loopback port the agent listens on
     * @return the connected session
     * @throws Exception if the connection or the handshake fails
     */
    public static LiveSession connect(String pid, int port) throws Exception
    {
        LiveAgentClient client = LiveAgentClient.connect("127.0.0.1", port, 10_000);
        try
        {
            AgentInfo info = client.hello();
            return new LiveSession(pid, info, client);
        }
        catch (IOException e)
        {
            client.close();
            throw e;
        }
    }

    /**
     * Lists every loaded class by name and access flags, without bytecode.
     *
     * @return the loaded classes
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<LoadedClass> enumerateClasses() throws IOException
    {
        return client.listClasses();
    }

    /**
     * Fetches one class's current bytecode from the target.
     *
     * @param internalName the class's internal name, with slashes
     * @return the class file bytes
     * @throws IOException if the request fails or the agent reports an error
     */
    public byte[] fetchClassBytes(String internalName) throws IOException
    {
        return client.getClassBytes(internalName);
    }

    /**
     * Replaces every registered event listener with one.
     *
     * @param listener the listener, or null to leave none
     */
    public void setEventListener(Consumer<LiveEvent> listener)
    {
        client.setEventListener(listener);
    }

    /**
     * Registers an event listener alongside any others.
     *
     * @param listener the listener; null is ignored
     */
    public void addEventListener(Consumer<LiveEvent> listener)
    {
        client.addEventListener(listener);
    }

    /**
     * Unregisters an event listener.
     *
     * @param listener the listener to remove
     */
    public void removeEventListener(Consumer<LiveEvent> listener)
    {
        client.removeEventListener(listener);
    }

    /**
     * Lists the target's live threads.
     *
     * @return the threads
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<ThreadInfo> getThreads() throws IOException
    {
        return client.getThreads();
    }

    /**
     * Replaces a class's bytecode in the target.
     *
     * @param internalName the class's internal name, with slashes
     * @param classBytes the new class file bytes
     * @throws IOException if the request fails or the agent rejects the redefinition
     */
    public void redefineClass(String internalName, byte[] classBytes) throws IOException
    {
        client.redefineClass(internalName, classBytes);
    }

    /**
     * Turns streaming of runtime class loads, as class-loaded events carrying bytes, on or off.
     *
     * @param on whether to stream loads
     * @throws IOException if the request fails or the agent reports an error
     */
    public void setCaptureLoads(boolean on) throws IOException
    {
        client.setCaptureLoads(on);
    }

    /**
     * Snapshots the monitor wait-for graph, for deadlock detection.
     *
     * @return one edge per blocked thread, from the waiter to the monitor's owner
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<ContentionEdge> getContention() throws IOException
    {
        return client.getContention();
    }

    /**
     * Triggers a HotSpot heap dump in the target.
     *
     * @return the local path of the .hprof file
     * @throws IOException if the request fails or the agent reports an error
     */
    public String heapDump() throws IOException
    {
        return client.heapDump();
    }

    /**
     * Reports whether the target's agent can drive Flight Recorder.
     *
     * @return true when the target runtime has JFR
     */
    public boolean supportsJfr()
    {
        return (info.getCapabilities() & com.tonic.live.protocol.LiveProtocol.CAP_JFR) != 0;
    }

    /**
     * Starts a JFR recording.
     *
     * @param profile the base JFR settings profile
     * @param categoryMask the extra event categories to enable, as bits
     * @param maxSizeMb the recording's size cap in megabytes, or 0 for unbounded
     * @throws IOException if the request fails or the agent reports an error
     */
    public void startRecording(String profile, int categoryMask, int maxSizeMb) throws IOException
    {
        client.jfrStart(profile, categoryMask, maxSizeMb);
    }

    /**
     * Stops the active JFR recording and dumps it.
     *
     * @return the local path of the .jfr file
     * @throws IOException if the request fails or the agent reports an error
     */
    public String stopRecording() throws IOException
    {
        return client.jfrStop();
    }

    /**
     * Dumps the active JFR recording without stopping it.
     *
     * @return the local path of the .jfr file
     * @throws IOException if the request fails or the agent reports an error
     */
    public String snapshotRecording() throws IOException
    {
        return client.jfrSnapshot();
    }

    /**
     * Reads the live static fields of a class.
     *
     * @param internalName the class's internal name, with slashes
     * @return the fields with their current values
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<StaticField> getStatics(String internalName) throws IOException
    {
        return client.getStatics(internalName);
    }

    /**
     * Sets a static field's value, or sets it to null.
     *
     * @param className the declaring class's internal name
     * @param field the field name
     * @param setNull whether to set null and ignore value
     * @param value the new value as text
     * @return the field's value as re-read after the change
     * @throws IOException if the request fails or the agent reports an error
     */
    public String setStatic(String className, String field, boolean setNull, String value) throws IOException
    {
        return client.setStatic(className, field, setNull, value);
    }

    /**
     * Runs a first value scan over the app's roots, keeping the matching field locations as the new candidate set.
     *
     * @param valueType the value type, one of the protocol's SCAN_ constants
     * @param scanKind how to match, one of the protocol's SCANKIND_ constants
     * @param value the value to match, as text
     * @param value2 the upper bound for a between scan
     * @param pkgFilter the package prefix to restrict the walk to, or empty for all
     * @param userClassesOnly whether to skip JDK classes
     * @param maxVisited the cap on objects visited
     * @param maxMatches the cap on matches retained
     * @param limit how many matches to return in the first page
     * @return the first page of matches
     * @throws IOException if the request fails or the agent reports an error
     */
    public ScanPage scanFirst(int valueType, int scanKind, String value, String value2, String pkgFilter, boolean userClassesOnly, int maxVisited, int maxMatches, int limit) throws IOException
    {
        return scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly, maxVisited, maxMatches, limit, false, false);
    }

    /**
     * Runs a first value scan, optionally seeded with the object set parked by JDI.
     *
     * @param valueType the value type, one of the protocol's SCAN_ constants
     * @param scanKind how to match, one of the protocol's SCANKIND_ constants
     * @param value the value to match, as text
     * @param value2 the upper bound for a between scan
     * @param pkgFilter the package prefix to restrict the walk to, or empty for all
     * @param userClassesOnly whether to skip JDK classes
     * @param maxVisited the cap on objects visited
     * @param maxMatches the cap on matches retained
     * @param limit how many matches to return in the first page
     * @param useDropbox whether to add the parked objects as extra roots; the caller must park them first
     * @param rootsOnly whether to scan only the parked objects
     * @return the first page of matches
     * @throws IOException if the request fails or the agent reports an error
     */
    public ScanPage scanFirst(int valueType, int scanKind, String value, String value2, String pkgFilter, boolean userClassesOnly, int maxVisited, int maxMatches, int limit, boolean useDropbox, boolean rootsOnly) throws IOException
    {
        return client.scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly, maxVisited, maxMatches, limit, useDropbox, rootsOnly);
    }

    /**
     * Re-reads the retained candidates and narrows them by a comparison.
     *
     * @param comparator the comparison, one of the protocol's CMP_ constants
     * @param value the value compared against, as text
     * @param value2 the upper bound for a between comparison
     * @param offset the index of the first match to return
     * @param limit how many matches to return
     * @return a page of the narrowed matches
     * @throws IOException if the request fails or the agent reports an error
     */
    public ScanPage scanNext(int comparator, String value, String value2, int offset, int limit) throws IOException
    {
        return client.scanNext(comparator, value, value2, offset, limit);
    }

    /**
     * Re-reads the current values of the candidate set, for live refresh.
     *
     * @param pinnedOnly whether to read only the pinned locations
     * @param offset the index of the first location to return
     * @param limit how many locations to return
     * @return a page of locations with current values
     * @throws IOException if the request fails or the agent reports an error
     */
    public ScanPage scanRead(boolean pinnedOnly, int offset, int limit) throws IOException
    {
        return client.scanRead(pinnedOnly, offset, limit);
    }

    /**
     * Writes a value into a scanned field.
     *
     * @param id the scanned location's id
     * @param isNull whether to write null and ignore value
     * @param value the new value as text
     * @return the field's value as re-read after the write
     * @throws IOException if the request fails or the agent reports an error
     */
    public String scanWrite(long id, boolean isNull, String value) throws IOException
    {
        return client.scanWrite(id, isNull, value);
    }

    /**
     * Walks the heap for live instances of a class.
     *
     * @param className the class's internal name
     * @param maxInstances the cap on instances returned
     * @param maxVisited the cap on objects visited
     * @return handles to the instances found
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<LiveInstance> listInstances(String className, int maxInstances, int maxVisited) throws IOException
    {
        return listInstances(className, maxInstances, maxVisited, false);
    }

    /**
     * Lists live instances of a class, by heap walk or from the object set parked by JDI.
     *
     * @param className the class's internal name
     * @param maxInstances the cap on instances returned
     * @param maxVisited the cap on objects visited
     * @param fromDropbox whether to take the parked set instead of walking; the caller must park it first
     * @return handles to the instances found
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<LiveInstance> listInstances(String className, int maxInstances, int maxVisited, boolean fromDropbox)
            throws IOException
    {
        return client.listInstances(className, maxInstances, maxVisited, fromDropbox);
    }

    /**
     * Reads the fields of a live instance.
     *
     * @param handleId the instance's handle
     * @return the fields with their current values
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<LiveField> instanceFields(long handleId) throws IOException
    {
        return client.instanceFields(handleId);
    }

    /**
     * Sets a field of a live instance.
     *
     * @param handleId the instance's handle
     * @param field the field name
     * @param isNull whether to set null and ignore value
     * @param value the new value as text
     * @return the field's value as re-read after the change
     * @throws IOException if the request fails or the agent reports an error
     */
    public String setInstanceField(long handleId, String field, boolean isNull, String value) throws IOException
    {
        return client.setInstanceField(handleId, field, isNull, value);
    }

    /**
     * Freezes a scanned field at a value, re-applied on a timer, or unfreezes it.
     *
     * @param id the scanned location's id
     * @param on whether to freeze
     * @param value the value to hold
     * @throws IOException if the request fails or the agent reports an error
     */
    public void scanFreeze(long id, boolean on, String value) throws IOException
    {
        client.scanFreeze(id, on, value);
    }

    /**
     * Pins a location to the watch list, where it survives narrowing, or unpins it.
     *
     * @param id the scanned location's id
     * @param on whether to pin
     * @throws IOException if the request fails or the agent reports an error
     */
    public void scanPin(long id, boolean on) throws IOException
    {
        client.scanPin(id, on);
    }

    /**
     * Clears the scan session: candidates, pins and freezes.
     *
     * @throws IOException if the request fails or the agent reports an error
     */
    public void scanClear() throws IOException
    {
        client.scanClear();
    }

    /**
     * Lists the static methods of a class.
     *
     * @param internalName the class's internal name, with slashes
     * @return the methods by name and descriptor
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<StaticMethod> listStaticMethods(String internalName) throws IOException
    {
        return client.listStaticMethods(internalName);
    }

    /**
     * Invokes a static method in the target, marshalling the arguments from text.
     *
     * @param className the declaring class's internal name
     * @param name the method name
     * @param desc the method's JVM descriptor
     * @param args the arguments as text
     * @return the formatted result
     * @throws IOException if the request fails or the agent reports an error
     */
    public String invokeStatic(String className, String name, String desc, List<String> args) throws IOException
    {
        return client.invokeStatic(className, name, desc, args);
    }

    /**
     * Defines compiled snippet classes in a throwaway child of a class's loader and runs the wrapper's static run method.
     *
     * @param classes the compiled class files by binary name
     * @param mainBinaryName the binary name of the wrapper class to run
     * @param contextClass the internal name of the class whose loader scopes visibility, or empty
     * @return the captured output and the result or exception
     * @throws IOException if the request fails or the agent reports an error
     */
    public String eval(Map<String, byte[]> classes, String mainBinaryName, String contextClass) throws IOException
    {
        return client.eval(classes, mainBinaryName, contextClass);
    }

    /**
     * Snapshots every thread with its current stack.
     *
     * @param maxDepth the cap on frames per thread
     * @return the threads with their stacks
     * @throws IOException if the request fails or the agent reports an error
     */
    public List<ThreadStack> getThreadStacks(int maxDepth) throws IOException
    {
        return client.getThreadStacks(maxDepth);
    }

    /**
     * Reads the target's runtime metrics: memory, GC, CPU, threads and classes.
     *
     * @return the snapshot
     * @throws IOException if the request fails or the agent reports an error
     */
    public MetricsSnapshot getMetrics() throws IOException
    {
        return client.getMetrics();
    }

    @Override
    public void close() throws IOException
    {
        client.close();
    }

    private static int freePort() throws IOException
    {
        try (ServerSocket s = new ServerSocket(0))
        {
            return s.getLocalPort();
        }
    }
}
