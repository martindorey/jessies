package e.edit;

import e.gui.*;
import e.util.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import javax.swing.*;

/**
 * Parses the external tool descriptions, found in files under ~/.e.edit.Edit/tools/.
 * See the manual for the format of the files.
 * The files and their directories are automatically monitored for changes, so you must not cache the result of getAllTools or getPopUpTools.
 * Note that elements in the lists returned by getAllTools and getPopUpTools may be null, indicating that UI listing tools should show a separator at that point.
 */
public class ExternalTools {
    private static final String MONITOR_NAME = "external tools";
    private static final int MAX_TOOLS_DIR_DEPTH = 10;
    private static final ArrayList<Listener> listeners = new ArrayList<>();
    
    private static FileAlterationMonitor fileAlterationMonitor;
    private static List<ExternalToolAction> allTools;
    private static List<ExternalToolAction> popUpTools;
    
    public interface Listener {
        public void toolsChanged();
    }
    
    private ExternalTools() { /* Not instantiable. */ }
    
    public static synchronized void initTools() {
        rescanToolConfiguration();
    }
    
    public static void addToolsListener(Listener l) {
        listeners.add(l);
    }
    
    private static void fireToolsChanged() {
        for (Listener l : listeners) {
            l.toolsChanged();
        }
    }
    
    private static void setFileAlterationMonitor(FileAlterationMonitor newFileAlterationMonitor) {
        if (fileAlterationMonitor != null) {
            fileAlterationMonitor.dispose();
        }
        fileAlterationMonitor = newFileAlterationMonitor;
        fileAlterationMonitor.addListener(new FileAlterationMonitor.Listener() {
            public void fileTouched(String pathname) {
                rescanToolConfiguration();
            }
        });
    }
    
    /**
     * Returns an up-to-date list of all the tools.
     */
    public static List<ExternalToolAction> getAllTools() {
        return Collections.unmodifiableList(allTools);
    }
    
    /**
     * Returns an up-to-date list of those tools that are meant to be shown on the pop-up menu.
     */
    public static List<ExternalToolAction> getPopUpTools() {
        return Collections.unmodifiableList(popUpTools);
    }
    
    private static void scanToolsDirectory(Path directory, final FileAlterationMonitor newFileAlterationMonitor, List<ExternalToolAction> newAllTools, List<ExternalToolAction> newPopUpTools) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        newFileAlterationMonitor.addPath(directory);
        try {
            Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_TOOLS_DIR_DEPTH, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    newFileAlterationMonitor.addPath(dir);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        parseFile(file, newAllTools, newPopUpTools);
                    } catch (Exception ex) {
                        Log.warn("Problem reading \"" + file + "\"", ex);
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
            Log.warn("Failed to walk tools tree for " + directory.toString(), ex);
        }
    }
    
    private static void rescanToolConfiguration() {
        final List<Path> toolsDirectories = new ArrayList<>();
        toolsDirectories.add(FileUtilities.pathFrom(Evergreen.getResourceFilename("lib", "data", "tools")));
        toolsDirectories.add(FileUtilities.pathFrom("/usr/lib/software.jessies.org/evergreen/tools/"));
        toolsDirectories.add(FileUtilities.pathFrom("/usr/local/software.jessies.org/evergreen/tools/"));
        toolsDirectories.add(FileUtilities.pathFrom(Evergreen.getPreferenceFilename("tools")));
        
        final FileAlterationMonitor newFileAlterationMonitor = new FileAlterationMonitor(MONITOR_NAME);
        final List<ExternalToolAction> newAllTools = new ArrayList<>();
        final List<ExternalToolAction> newPopUpTools = new ArrayList<>();
        
        // FIXME: every change of directory should probably add a separator automatically. (including within a toolsDirectory?)
        for (Path toolsDirectory : toolsDirectories) {
            scanToolsDirectory(toolsDirectory, newFileAlterationMonitor, newAllTools, newPopUpTools);
        }
        // FIXME: strip duplicate and leading/trailing separators after all the directories have been scanned.
        
        allTools = newAllTools;
        popUpTools = newPopUpTools;
        // Even if we found no files, a new directory may have been created.
        // The tools might even have disappeared.
        setFileAlterationMonitor(newFileAlterationMonitor);
        Evergreen.getInstance().showStatus("Tools reloaded");
        fireToolsChanged();
    }
    
    private static void parseFile(Path file, List<ExternalToolAction> newAllTools, List<ExternalToolAction> newPopUpTools) throws IOException {
        String name = null;
        String command = null;
        
        String keyboardEquivalent = null;
        
        String icon = null;
        String stockIcon = null;
        
        boolean checkEverythingSaved = false;
        boolean needsFile = false;
        boolean showOnPopUpMenu = false;
        
        for (String line : Files.readAllLines(file)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equalsPos = line.indexOf('=');
            if (equalsPos == -1) {
                // TODO: isn't this an error worth reporting?
                Log.warn("line without '=' found in properties file");
                return;
            }
            final String key = line.substring(0, equalsPos);
            final String value = line.substring(equalsPos + 1);
            if (key.equals("name")) {
                name = value;
            } else if (key.equals("command")) {
                command = value;
            } else if (key.equals("keyboardEquivalent")) {
                keyboardEquivalent = value;
            } else if (key.equals("icon")) {
                icon = value;
            } else if (key.equals("stockIcon")) {
                stockIcon = value;
            } else if (key.equals("checkEverythingSaved")) {
                checkEverythingSaved = Boolean.valueOf(value);
            } else if (key.equals("needsFile")) {
                needsFile = Boolean.valueOf(value);
            } else if (key.equals("showOnPopUpMenu")) {
                showOnPopUpMenu = Boolean.valueOf(value);
            } else {
                Log.warn("Strange line in tool file \"" + file + "\": " + line);
                return;
            }
        }
        
        if (name == null) {
            Log.warn("No 'name' line in tool file \"" + file + "\"!");
            return;
        }
        if (name.equals("<separator>")) {
            newAllTools.add(null);
            if (showOnPopUpMenu) {
                newPopUpTools.add(null);
            }
            return;
        }
        if (command == null) {
            Log.warn("No 'command' line in tool file \"" + file + "\"!");
            return;
        }
        
        // We accept a shorthand notation for specifying input/output dispositions based on Perl's "open" syntax.
        // Additionally, we support a special "|?", which is the same as | if the executed program is successful,
        // but any output on stderr is written to the errors window; only stdout is written to the document.
        // We're also adding "|!", which runs the external tool with the entire document as input (never just
        // the selection), and the output is interpreted as a set of commands that are used to modify the document,
        // set the selection, or whatever else we want to provide. It's kind of a poor man's language server API,
        // only it doesn't require all the extra work of supporting that, which I'm not going to get time for.
        ToolInputDisposition inputDisposition = ToolInputDisposition.NO_INPUT;
        ToolOutputDisposition outputDisposition = ToolOutputDisposition.ERRORS_WINDOW;
        if (command.startsWith("<")) {
            inputDisposition = ToolInputDisposition.SELECTION_OR_DOCUMENT;
            outputDisposition = ToolOutputDisposition.INSERT;
            needsFile = true;
            command = command.substring(1);
        } else if (command.startsWith(">")) {
            inputDisposition = ToolInputDisposition.SELECTION_OR_DOCUMENT;
            outputDisposition = ToolOutputDisposition.ERRORS_WINDOW;
            needsFile = true;
            command = command.substring(1);
        } else if (command.startsWith("|?")) {
            inputDisposition = ToolInputDisposition.SELECTION_OR_DOCUMENT;
            outputDisposition = ToolOutputDisposition.REPLACE_IF_OK;
            needsFile = true;
            command = command.substring(2);
        } else if (command.startsWith("|!")) {
            inputDisposition = ToolInputDisposition.DOCUMENT;
            outputDisposition = ToolOutputDisposition.COMMANDS;
            needsFile = true;
            command = command.substring(2);
        } else if (command.startsWith("|")) {
            inputDisposition = ToolInputDisposition.SELECTION_OR_DOCUMENT;
            outputDisposition = ToolOutputDisposition.REPLACE;
            needsFile = true;
            command = command.substring(1);
        } else if (command.startsWith("!")) {
            inputDisposition = ToolInputDisposition.NO_INPUT;
            outputDisposition = ToolOutputDisposition.ERRORS_WINDOW;
            needsFile = true;
            command = command.substring(1);
        }
        
        final ExternalToolAction action = new ExternalToolAction(name, inputDisposition, outputDisposition, command);
        action.setCheckEverythingSaved(checkEverythingSaved);
        action.setNeedsFile(needsFile);
        
        configureKeyboardEquivalent(action, keyboardEquivalent);
        configureIcon(action, stockIcon, icon);
        
        newAllTools.add(action);
        if (showOnPopUpMenu) {
            newPopUpTools.add(action);
        }
    }
    
    private static void configureKeyboardEquivalent(Action action, String keyboardEquivalent) {
        if (keyboardEquivalent == null) {
            return;
        }
        
        keyboardEquivalent = keyboardEquivalent.trim().toUpperCase();
        // For now, the heuristic is that special keys (mainly just the function keys) have names and don't want any extra modifiers.
        // Simple letter keys, to keep from colliding with built-in keystrokes, automatically get the platform default modifier plus shift.
        // Neither part of this is really right.
        // For one thing, you might well want to have f1, shift-f1, and so on all do different (though probably related) things.
        // For another, we already use various of the "safe" combinations for built-in functionality; "Find in Files", for example.
        // I can't remember what's wrong with KeyStroke.getKeyStroke(String); I believe the problems were:
        // 1. complex case sensitivity for key names.
        // 2. no support for the idea of a platform-default modifier.
        // 3. users can get themselves into trouble with pressed/released/typed.
        // In the long run, though, we're going to want to implement our own replacement for that, or a pre-processor.
        int modifiers = 0;
        if (keyboardEquivalent.length() == 1) {
            modifiers = GuiUtilities.getDefaultKeyStrokeModifier() | InputEvent.SHIFT_DOWN_MASK;
        }
        
        KeyStroke keyStroke = GuiUtilities.makeKeyStrokeWithModifiers(modifiers, keyboardEquivalent);
        if (keyStroke != null) {
            action.putValue(Action.ACCELERATOR_KEY, keyStroke);
        }
    }
    
    private static void configureIcon(Action action, String stockIcon, String icon) {
        // Allow users to specify a GNOME stock icon, and/or the pathname to an icon.
        if (stockIcon != null) {
            GnomeStockIcon.useStockIcon(action, stockIcon);
        } else if (icon != null) {
            // We trust the user to specify an appropriately-sized icon.
            action.putValue(Action.SMALL_ICON, new ImageIcon(icon));
        }
    }
}
