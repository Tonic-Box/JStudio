package com.tonic.ui.dialog.filechooser;

import javax.swing.Icon;
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

/** File system helpers for the file chooser: background directory listing with a five-second cache, plus roots, special folders and system names and icons. */
public class FileSystemWorker
{

    private static final FileSystemView fsv = FileSystemView.getFileSystemView();

    private static final Map<String, CachedListing> cache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5000;

    /** A callback for a directory listing. */
    public interface DirectoryListingListener
    {
        /**
         * Called with a directory's filtered contents.
         *
         * @param directory the directory listed
         * @param files its entries, directories first, then by name ignoring case
         */
        void onListingComplete(File directory, List<File> files);

        /**
         * Called when listing fails.
         *
         * @param directory the directory listed
         * @param error what went wrong
         */
        void onListingError(File directory, Exception error);
    }

    /**
     * Lists a directory, answering at once from the cache when fresh and otherwise on a background thread with the result delivered on the EDT.
     *
     * @param directory the directory
     * @param filter the filter to apply, or null for none
     * @param listener receives the result; an unreadable directory gives an empty list, not an error
     */
    public static void listDirectory(File directory, ExtensionFileFilter filter, DirectoryListingListener listener)
    {
        String cacheKey = directory.getAbsolutePath();
        CachedListing cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired())
        {
            List<File> filtered = filterFiles(cached.files, filter);
            listener.onListingComplete(directory, filtered);
            return;
        }

        SwingWorker<List<File>, Void> worker = new SwingWorker<>()
        {
            @Override
            protected List<File> doInBackground()
            {
                return listFilesSync(directory);
            }

            @Override
            protected void done()
            {
                try
                {
                    List<File> allFiles = get();

                    cache.put(cacheKey, new CachedListing(allFiles));

                    List<File> filtered = filterFiles(allFiles, filter);
                    listener.onListingComplete(directory, filtered);
                }
                catch (Exception e)
                {
                    listener.onListingError(directory, e);
                }
            }
        };

        worker.execute();
    }

    private static List<File> listFilesSync(File directory)
    {
        List<File> result = new ArrayList<>();

        if (directory == null || !directory.isDirectory())
        {
            return result;
        }

        File[] files = directory.listFiles();
        if (files != null)
        {
            result.addAll(Arrays.asList(files));

            result.sort(Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(f -> f.getName().toLowerCase()));
        }

        return result;
    }

    private static List<File> filterFiles(List<File> files, ExtensionFileFilter filter)
    {
        if (filter == null || filter.isAllFiles())
        {
            return new ArrayList<>(files);
        }

        List<File> result = new ArrayList<>();
        for (File file : files)
        {
            if (filter.accept(file))
            {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Lists the file system roots, which are drives on Windows.
     *
     * @return the roots
     */
    public static File[] getRoots()
    {
        return File.listRoots();
    }

    /**
     * Finds the home directory and the Desktop, Documents and Downloads folders under it that exist.
     *
     * @return the folders by label, empty if user.home is not set
     */
    public static Map<String, File> getSpecialFolders()
    {
        Map<String, File> folders = new HashMap<>();

        String userHome = System.getProperty("user.home");
        if (userHome != null)
        {
            File home = new File(userHome);
            folders.put("Home", home);

            File desktop = new File(home, "Desktop");
            if (desktop.exists())
            {
                folders.put("Desktop", desktop);
            }

            File documents = new File(home, "Documents");
            if (documents.exists())
            {
                folders.put("Documents", documents);
            }

            File downloads = new File(home, "Downloads");
            if (downloads.exists())
            {
                folders.put("Downloads", downloads);
            }
        }

        return folders;
    }

    /**
     * The name the operating system shows for a file.
     *
     * @param file the file
     * @return the system display name, the file name if there is none, or an empty string for null
     */
    public static String getDisplayName(File file)
    {
        if (file == null)
        {
            return "";
        }
        String name = fsv.getSystemDisplayName(file);
        return name != null && !name.isEmpty() ? name : file.getName();
    }

    /**
     * The icon the operating system shows for a file.
     *
     * @param file the file
     * @return the icon, or null if there is none
     */
    public static Icon getSystemIcon(File file)
    {
        return fsv.getSystemIcon(file);
    }

    /**
     * Checks whether a file is a file system root.
     *
     * @param file the file
     * @return true for a root such as a drive
     */
    public static boolean isRoot(File file)
    {
        return fsv.isFileSystemRoot(file);
    }

    /**
     * The parent directory of a file.
     *
     * @param file the file
     * @return the parent, or null for null or a root
     */
    public static File getParent(File file)
    {
        if (file == null)
        {
            return null;
        }
        File parent = file.getParentFile();
        if (parent == null && fsv.isFileSystemRoot(file))
        {
            return null;
        }
        return parent;
    }

    /**
     * Checks whether a path names an existing file or directory.
     *
     * @param path the path, trimmed before use
     * @return true if it exists; false for null or blank
     */
    public static boolean isValidPath(String path)
    {
        if (path == null || path.trim().isEmpty())
        {
            return false;
        }
        File file = new File(path.trim());
        return file.exists();
    }

    /**
     * Drops the cached listing of one directory.
     *
     * @param directory the directory, or null to do nothing
     */
    public static void invalidateCache(File directory)
    {
        if (directory != null)
        {
            cache.remove(directory.getAbsolutePath());
        }
    }

    /** Drops every cached listing. */
    public static void clearCache()
    {
        cache.clear();
    }

    private static class CachedListing
    {
        final List<File> files;
        final long timestamp;

        CachedListing(List<File> files)
        {
            this.files = files;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired()
        {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}
