package com.tonic.service.history;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectDatabase;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.model.Snapshot;
import com.tonic.service.ConsoleLogService;
import com.tonic.service.ProjectService;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Per-project Local History with <b>save-gated</b> persistence: snapshots and their content-addressed blobs
 * accumulate in memory during a session and are written to the project's {@code <name>.jstudio.history} zip only on
 * {@link #flush()} (invoked when the project is saved). Reopening a saved project restores the last-saved working
 * state (its newest snapshot), so in-memory bytecode edits survive across sessions; an unsaved session leaves no
 * trace. Reads always target the live current project. Snapshots run synchronously to capture pre-mutation bytes.
 */
public final class LocalHistoryService {

    private static final LocalHistoryService INSTANCE = new LocalHistoryService();
    private static final int MAX_SNAPSHOTS = 30;
    private static final String INDEX_ENTRY = "index.json";
    private static final String BLOBS_DIR = "blobs";

    private final Gson gson = new Gson();
    private final AtomicLong seq = new AtomicLong();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, byte[]> pendingBlobs = new HashMap<>();

    private File storeFile;
    private List<Snapshot> snapshots = new ArrayList<>();

    private LocalHistoryService() {
    }

    public static LocalHistoryService getInstance() {
        return INSTANCE;
    }

    private static ProjectModel project() {
        return ProjectService.getInstance().getCurrentProject();
    }

    /** History is available only for file-backed projects (a live-captured project has no source file). */
    public boolean isEnabled() {
        return storeFile != null;
    }

    /**
     * Points the service at {@code project}'s history store, loading the last-saved snapshots from disk. Idempotent
     * for the project already attached. Returns true when a NEW store was opened (the caller restores the saved
     * working state, or creates a baseline for a first-time project).
     */
    public synchronized boolean attach(ProjectModel project) {
        File newStore = project != null && project.getSourceFile() != null
                ? new File(project.getSourceFile().getParentFile(),
                ProjectDatabase.getProjectFileName(project.getSourceFile()) + ".history")
                : null;

        if (newStore == null) {
            boolean had = storeFile != null;
            storeFile = null;
            snapshots = new ArrayList<>();
            pendingBlobs.clear();
            if (had) {
                notifyListeners();
            }
            return false;
        }
        if (newStore.equals(storeFile)) {
            return false;
        }
        storeFile = newStore;
        snapshots = new ArrayList<>();
        pendingBlobs.clear();
        loadIndex();
        notifyListeners();
        return true;
    }

    public synchronized void detach() {
        storeFile = null;
        snapshots = new ArrayList<>();
        pendingBlobs.clear();
        notifyListeners();
    }

    /** Snapshots newest-first. */
    public synchronized List<Snapshot> list() {
        List<Snapshot> copy = new ArrayList<>(snapshots);
        copy.sort((a, b) -> Long.compare(b.getTimestampMs(), a.getTimestampMs()));
        return copy;
    }

    /** The newest snapshot (the last-saved working state once loaded from disk), or null. */
    public synchronized Snapshot newest() {
        return latest();
    }

    /** Content hashes of the current project's user classes, for diffing a snapshot against the live project. */
    public synchronized Map<String, String> currentClassHashes() {
        Map<String, String> map = new LinkedHashMap<>();
        ProjectModel project = project();
        if (project == null) {
            return map;
        }
        for (ClassEntryModel entry : project.getUserClasses()) {
            try {
                map.put(entry.getClassName(), sha256(entry.getClassFile().write()));
            } catch (Exception ignored) {
                // unreadable class - omit
            }
        }
        return map;
    }

    /**
     * Captures the current project state as an in-memory snapshot (persisted on the next {@link #flush()}). Returns
     * the snapshot, or null if history is disabled or the state is byte-identical to the most recent snapshot.
     */
    public synchronized Snapshot snapshot(String label, Snapshot.Trigger trigger) {
        ProjectModel project = project();
        if (!isEnabled() || project == null) {
            return null;
        }
        Map<String, String> classHashes = new LinkedHashMap<>();
        Map<String, String> resourceHashes = new LinkedHashMap<>();
        Map<String, byte[]> blobs = new HashMap<>();

        for (ClassEntryModel entry : project.getUserClasses()) {
            try {
                byte[] bytes = entry.getClassFile().write();
                String hash = sha256(bytes);
                classHashes.put(entry.getClassName(), hash);
                blobs.put(hash, bytes);
            } catch (Exception e) {
                ConsoleLogService.getInstance().warn("History: skipped " + entry.getClassName() + " (" + e.getMessage() + ")");
            }
        }
        for (ResourceEntryModel resource : project.getAllResources()) {
            String hash = sha256(resource.getData());
            resourceHashes.put(resource.getPath(), hash);
            blobs.put(hash, resource.getData());
        }

        Snapshot latest = latest();
        if (latest != null && latest.getClasses().equals(classHashes) && latest.getResources().equals(resourceHashes)) {
            return null;
        }

        long now = System.currentTimeMillis();
        String id = Long.toString(now, 36) + "-" + Long.toString(seq.incrementAndGet(), 36);
        Snapshot snapshot = new Snapshot(id, now, label, trigger, classHashes, resourceHashes);
        snapshots.add(snapshot);
        pruneToCap();
        blobs.forEach(pendingBlobs::putIfAbsent);
        notifyListeners();
        return snapshot;
    }

    /** Writes all in-memory snapshots + their blobs to the history store. Call when the project is saved. */
    public synchronized void flush() {
        if (storeFile == null) {
            return;
        }
        try (FileSystem fs = openZip(true)) {
            Path blobsDir = fs.getPath(BLOBS_DIR);
            if (!Files.exists(blobsDir)) {
                Files.createDirectories(blobsDir);
            }
            for (Map.Entry<String, byte[]> blob : pendingBlobs.entrySet()) {
                Path path = fs.getPath(BLOBS_DIR, blob.getKey());
                if (!Files.exists(path)) {
                    Files.write(path, blob.getValue());
                }
            }
            Files.write(fs.getPath(INDEX_ENTRY), gson.toJson(snapshots).getBytes(StandardCharsets.UTF_8));
            gc(fs);
            pendingBlobs.clear();
        } catch (IOException e) {
            ConsoleLogService.getInstance().warn("History: failed to write store: " + e.getMessage());
        }
    }

    /** Restores the whole project to {@code snapshot}; the caller must refresh the UI afterward. */
    public synchronized boolean restore(Snapshot snapshot) {
        ProjectModel project = project();
        if (project == null || snapshot == null) {
            return false;
        }
        try {
            Set<String> hashes = new HashSet<>(snapshot.getClasses().values());
            hashes.addAll(snapshot.getResources().values());
            Map<String, byte[]> byHash = readBlobs(hashes);

            Map<String, byte[]> classBytes = new LinkedHashMap<>();
            snapshot.getClasses().forEach((name, hash) -> classBytes.put(name, byHash.get(hash)));
            Map<String, byte[]> resourceBytes = new LinkedHashMap<>();
            snapshot.getResources().forEach((path, hash) -> resourceBytes.put(path, byHash.get(hash)));

            project.replaceUserClasses(classBytes, resourceBytes);
            return true;
        } catch (IOException | RuntimeException e) {
            ConsoleLogService.getInstance().warn("History: restore failed: " + e.getMessage());
            return false;
        }
    }

    /** Restores a single class from {@code snapshot}; the caller must refresh the UI afterward. */
    public synchronized boolean restoreClass(Snapshot snapshot, String internalName) {
        ProjectModel project = project();
        byte[] bytes = classBytes(snapshot, internalName);
        if (project == null || bytes == null) {
            return false;
        }
        try {
            project.replaceClass(internalName, bytes);
            return true;
        } catch (RuntimeException e) {
            ConsoleLogService.getInstance().warn("History: class restore failed: " + e.getMessage());
            return false;
        }
    }

    /** The stored bytes of {@code internalName} as of {@code snapshot} (for the diff view), or null. */
    public synchronized byte[] classBytes(Snapshot snapshot, String internalName) {
        String hash = snapshot.getClasses().get(internalName);
        if (hash == null) {
            return null;
        }
        try {
            return readBlobs(new HashSet<>(List.of(hash))).get(hash);
        } catch (IOException e) {
            ConsoleLogService.getInstance().warn("History: failed to read blob: " + e.getMessage());
            return null;
        }
    }

    public synchronized boolean delete(Snapshot snapshot) {
        if (!snapshots.removeIf(s -> s.getId().equals(snapshot.getId()))) {
            return false;
        }
        notifyListeners();
        return true;
    }

    public void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** The number of registered listeners (test-only hook for leak detection). */
    public int getListenerCount() {
        return listeners.size();
    }

    private void notifyListeners() {
        SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
    }

    private Snapshot latest() {
        Snapshot best = null;
        for (Snapshot s : snapshots) {
            if (best == null || s.getTimestampMs() > best.getTimestampMs()) {
                best = s;
            }
        }
        return best;
    }

    private void pruneToCap() {
        if (snapshots.size() <= MAX_SNAPSHOTS) {
            return;
        }
        snapshots.sort(Comparator.comparingLong(Snapshot::getTimestampMs));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    private void loadIndex() {
        snapshots = new ArrayList<>();
        if (storeFile == null || !storeFile.exists()) {
            return;
        }
        try (FileSystem fs = openZip(false)) {
            Path index = fs.getPath(INDEX_ENTRY);
            if (Files.exists(index)) {
                String json = Files.readString(index);
                List<Snapshot> loaded = gson.fromJson(json, new TypeToken<List<Snapshot>>() {
                }.getType());
                if (loaded != null) {
                    snapshots = new ArrayList<>(loaded);
                }
            }
        } catch (IOException | RuntimeException e) {
            ConsoleLogService.getInstance().warn("History: failed to read store: " + e.getMessage());
        }
    }

    private void gc(FileSystem fs) throws IOException {
        Path blobsDir = fs.getPath(BLOBS_DIR);
        if (!Files.exists(blobsDir)) {
            return;
        }
        Set<String> referenced = new HashSet<>();
        for (Snapshot s : snapshots) {
            referenced.addAll(s.getClasses().values());
            referenced.addAll(s.getResources().values());
        }
        List<Path> orphans;
        try (Stream<Path> entries = Files.list(blobsDir)) {
            orphans = entries.filter(p -> !referenced.contains(p.getFileName().toString())).collect(Collectors.toList());
        }
        for (Path orphan : orphans) {
            Files.deleteIfExists(orphan);
        }
    }

    /** Reads blobs from the in-memory pending set first, falling back to the on-disk store (one zip open). */
    private Map<String, byte[]> readBlobs(Set<String> hashes) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        Set<String> needDisk = new HashSet<>();
        for (String hash : hashes) {
            byte[] pending = pendingBlobs.get(hash);
            if (pending != null) {
                result.put(hash, pending);
            } else {
                needDisk.add(hash);
            }
        }
        if (!needDisk.isEmpty() && storeFile != null && storeFile.exists()) {
            try (FileSystem fs = openZip(false)) {
                for (String hash : needDisk) {
                    Path path = fs.getPath(BLOBS_DIR, hash);
                    if (Files.exists(path)) {
                        result.put(hash, Files.readAllBytes(path));
                    }
                }
            }
        }
        return result;
    }

    private FileSystem openZip(boolean create) throws IOException {
        Map<String, String> env = new HashMap<>();
        if (create) {
            env.put("create", "true");
        }
        return FileSystems.newFileSystem(URI.create("jar:" + storeFile.toPath().toUri()), env);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
