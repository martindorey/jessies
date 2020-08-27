package e.edit;

import e.util.*;
import org.jessies.os.Stat;
import java.awt.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;

public class WorkspaceFileList {
    private final Workspace workspace;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    
    private FileIgnorer fileIgnorer;
    private ArrayList<String> fileList;
     
    // How deep to allow us to go into the filesystem tree when scanning for files.
    private static final int MAX_DIR_DEPTH = 40;
    
    private FileAlterationMonitor fileAlterationMonitor;
    
    public WorkspaceFileList(Workspace workspace) {
        this.workspace = workspace;
        try {
            ArrayList<String> result = new ArrayList<>();
            result.addAll(Arrays.asList(StringUtilities.readLinesFromFile(workspace.getFileListCacheFile())));
            fileList = result;
        } catch (Exception ex) {
            // Nothing we can do. Probably just didn't exist.
        }
    }
    
    public void addFileListListener(Listener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }
    
    public void removeFileListListener(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }
    
    public void dispose() {
        fileAlterationMonitor.dispose();
    }
    
    /**
     * Returns the number of indexed files for this workspace, or -1 if no list is currently available.
     */
    public int getIndexedFileCount() {
        List<String> list = fileList;
        return (list != null) ? list.size() : -1;
    }
    
    public synchronized FileIgnorer getFileIgnorer() {
        if (fileIgnorer == null) {
            updateFileIgnorer();
        }
        return fileIgnorer;
    }
    
    private synchronized void updateFileIgnorer() {
        fileIgnorer = new FileIgnorer(FileUtilities.fileFromString(workspace.getRootDirectory()));
    }
    
    public void ensureInFileList(String pathWithinWorkspace) {
        List<String> list = fileList;
        if (list != null && list.contains(pathWithinWorkspace) == false) {
            updateFileList();
        }
    }
    
    public void rootDidChange() {
        initFileAlterationMonitorForRoot(workspace.getRootDirectory());
        if (Evergreen.getInstance().isInitialized()) updateFileList();
    }
    
    /**
     * Fills the file list. It can take some time to scan for files, so we do
     * the job in the background. New requests that arrive while a scan is
     * already in progress will be queued behind the in-progress scan.
     */
    public synchronized void updateFileList() {
        new FileListUpdater().execute();
    }
    
    /**
     * Returns a list of the files matching the given regular expression.
     */
    public List<String> getListOfFilesMatching(String regularExpression) {
        Pattern pattern = PatternUtilities.smartCaseCompile(regularExpression);
        ArrayList<String> result = new ArrayList<>();
        List<String> allFiles = fileList;
        if (allFiles == null) {
            return result;
        }
        for (String candidate : allFiles) {
            Matcher matcher = pattern.matcher(candidate);
            if (matcher.find()) {
                result.add(candidate);
            }
        }
        return result;
    }
    
    private void initFileAlterationMonitorForRoot(String rootDirectory) {
        // Get rid of any existing file alteration monitor.
        if (fileAlterationMonitor != null) {
            fileAlterationMonitor.dispose();
            fileAlterationMonitor = null;
        }
        
        // Start a new thread to check for last-modified time changes...
        fileAlterationMonitor = new FileAlterationMonitor(rootDirectory);
        fileAlterationMonitor.addListener(new FileAlterationMonitor.Listener() {
            public void fileTouched(String pathname) {
                updateFileList();
            }
        });
        
        fileAlterationMonitor.addPathname(rootDirectory);
    }
    
    private class FileListUpdater extends SwingWorker<ArrayList<String>, Object> {
        private final File workspaceRoot;
        private final int prefixCharsToSkip;
        
        public FileListUpdater() {
            this.workspaceRoot = FileUtilities.fileFromString(workspace.getRootDirectory());
            // All children of the root will start with a '/', which we don't want to be part of the name.
            this.prefixCharsToSkip = workspaceRoot.toString().length() + 1;
            fireListeners(false);
            fileList = null;
        }
        
        @Override
        protected ArrayList<String> doInBackground() {
            // Don't hog the CPU while we're still getting started.
            Evergreen.getInstance().awaitInitialization();
            
            ArrayList<String> newFileList = scanWorkspaceForFiles();
            // Many file systems will have returned the files not in alphabetical order, so we sort them ourselves here.
            // Users of the list can then assume it's in order.
            Collections.sort(newFileList, String.CASE_INSENSITIVE_ORDER);
            fileList = newFileList;
            return fileList;
        }
        
        /**
         * Builds a list of files for Open Quickly.
         */
        private ArrayList<String> scanWorkspaceForFiles() {
            final long t0 = System.nanoTime();
            
            // We should reload the file ignorer's configuration when we rescan.
            updateFileIgnorer();
            
            ArrayList<String> result = new ArrayList<>();
            try {
                // TODO: In calls to fileIgnorer here, we send an empty Stat. This means that, if
                // the file or dir is actually accessed via a symlink, the fileIgnorer is not told.
                // We may wish to revisit this, particularly for directories, as it makes little sense
                // to index the contents of a dir twice. On the other hand, who would do such a thing?
                // Is filtering that out really useful?
                // Some investigation will be needed to determine how to check for symlinks, as using
                // 'FOLLOW_LINKS', we end up with the BasicFileAttributes of the linked-to thing, and
                // there's no obvious way of knowing whether we got there via a link or not.
                // Think about this.
                Files.walkFileTree(workspaceRoot.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DIR_DEPTH, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (fileIgnorer.enterDirectory(dir.toFile(), new Stat())) {
                            return FileVisitResult.CONTINUE;
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (fileIgnorer.acceptFile(file.toFile(), new Stat())) {
                            result.add(file.toString().substring(prefixCharsToSkip));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException ex) {
                        // Stuff can be deleted under our feet.
                        // This is not an error, just something we cope with and continue.
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            
            Evergreen.getInstance().showStatus("Scan of workspace \"" + workspace.getWorkspaceName() + "\" complete (" + result.size() + " files)");
            
            final long t1 = System.nanoTime();
            StringUtilities.writeFile(workspace.getFileListCacheFile(), result);
            final long t2 = System.nanoTime();
            
            Log.warn("Scan of workspace \"" + workspace.getWorkspaceName() + "\" took " + TimeUtilities.nsToString(t1 - t0) + " (plus " + TimeUtilities.nsToString(t2 - t1) + " to update cache); found " + result.size() + " files.");
            return result;
        }
        
        @Override
        public void done() {
            fireListeners(true);
        }
    }
    
    private void fireListeners(final boolean isNowValid) {
        synchronized (listeners) {
            for (final Listener l : listeners) {
                // Ensure we're running on the EDT.
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        l.fileListStateChanged(isNowValid);
                    }
                });
            }
        }
    }
    
    public interface Listener {
        /**
         * Invoked to notify listeners of the file list state.
         * Calls do not necessarily imply a change of state since the last notification.
         * This class ensures that you will be called back on the EDT.
         */
        public void fileListStateChanged(boolean isNowValid);
    }
}
