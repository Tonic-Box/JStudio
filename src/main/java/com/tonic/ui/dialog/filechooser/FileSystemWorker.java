package com.tonic.ui.dialog.filechooser;

import javax.swing.SwingWorker;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles async file system operations to keep the UI responsive.
 */
public class FileSystemWorker {

    private static final FileSystemView fsv = FileSystemView.getFileSystemView();

    // Cache recent directory listings for speed
    private static final Map<String, CachedListing> cache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000; // 5 seconds

    /**
     * Listener for directory listing results.
     */
    public interface DirectoryListingListener {
        void onListingComplete(File directory, List<File> files);
        void onListingError(File directory, Exception error);
    }

    /**
     * List files in a directory asynchronously.
     */
    public static void listDirectory(File directory, ExtensionFileFilter filter,
                                     DirectoryListingListener listener) {
        // Check cache first
        String cacheKey = directory.getAbsolutePath();
        CachedListing cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            // Filter cached results
            List<File> filtered = filterFiles(cached.files, filter);
            listener.onListingComplete(directory, filtered);
            return;
        }

        // Load asynchronously
        SwingWorker<List<File>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<File> doInBackground() throws Exception {
                return listFilesSync(directory);
            }

            @Override
            protected void done() {
                try {
                    List<File> allFiles = get();

                    // Update cache
                    cache.put(cacheKey, new CachedListing(allFiles));

                    // Filter and return
                    List<File> filtered = filterFiles(allFiles, filter);
                    listener.onListingComplete(directory, filtered);
                } catch (Exception e) {
                    listener.onListingError(directory, e);
                }
            }
        };

        worker.execute();
    }

    /**
     * List files synchronously (for use in worker thread).
     */
    private static List<File> listFilesSync(File directory) {
        List<File> result = new ArrayList<>();

        if (directory == null || !directory.isDirectory()) {
            return result;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            result.addAll(Arrays.asList(files));

            // Sort: directories first, then by name (case-insensitive)
            result.sort(Comparator
                    .comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));
        }

        return result;
    }

    /**
     * Filter files by extension.
     */
    private static List<File> filterFiles(List<File> files, ExtensionFileFilter filter) {
        if (filter == null || filter.isAllFiles()) {
            return new ArrayList<>(files);
        }

        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (filter.accept(file)) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Get the system file roots (drives on Windows).
     */
    public static File[] getRoots() {
        return File.listRoots();
    }

    /**
     * Get special system folders (Desktop, Documents, etc.).
     */
    public static Map<String, File> getSpecialFolders() {
        Map<String, File> folders = new HashMap<>();

        // Home directory
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File home = new File(userHome);
            folders.put("Home", home);

            // Common subdirectories
            File desktop = new File(home, "Desktop");
            if (desktop.exists()) {
                folders.put("Desktop", desktop);
            }

            File documents = new File(home, "Documents");
            if (documents.exists()) {
                folders.put("Documents", documents);
            }

            File downloads = new File(home, "Downloads");
            if (downloads.exists()) {
                folders.put("Downloads", downloads);
            }
        }

        return folders;
    }

    /**
     * Get display name for a file (using FileSystemView).
     */
    public static String getDisplayName(File file) {
        if (file == null) {
            return "";
        }
        String name = fsv.getSystemDisplayName(file);
        return name != null && !name.isEmpty() ? name : file.getName();
    }

    /**
     * Get system icon for a file.
     */
    public static javax.swing.Icon getSystemIcon(File file) {
        return fsv.getSystemIcon(file);
    }

    /**
     * Check if a file is a file system root (drive).
     */
    public static boolean isRoot(File file) {
        return fsv.isFileSystemRoot(file);
    }

    /**
     * Get the parent directory, handling roots specially.
     */
    public static File getParent(File file) {
        if (file == null) {
            return null;
        }
        File parent = file.getParentFile();
        if (parent == null && fsv.isFileSystemRoot(file)) {
            return null; // At root level
        }
        return parent;
    }

    /**
     * Check if a path is valid and exists.
     */
    public static boolean isValidPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path.trim());
        return file.exists();
    }

    /**
     * Invalidate cache for a specific directory.
     */
    public static void invalidateCache(File directory) {
        if (directory != null) {
            cache.remove(directory.getAbsolutePath());
        }
    }

    /**
     * Clear all cached listings.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Cached directory listing with expiry.
     */
    private static class CachedListing {
        final List<File> files;
        final long timestamp;

        CachedListing(List<File> files) {
            this.files = files;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}
