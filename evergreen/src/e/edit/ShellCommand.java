package e.edit;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class ShellCommand {
    private final PTextArea textArea;
    private final EErrorsWindow errorsWindow;
    private final String context;
    private final String command;
    private final Map<String, String> environmentAdditions;
    private final ToolInputDisposition inputDisposition;
    private final ToolOutputDisposition outputDisposition;
    
    private final StringBuilder capturedOutput = new StringBuilder();
    
    private Process process;
    
    /** The count of open streams. */
    private int openStreamCount = 0;
    
    private Runnable launchRunnable = new NoOpRunnable();
    private Runnable completionRunnable = new NoOpRunnable();
    
    /**
     * Creates a ShellCommand that, when runCommand is invoked, will spawn 'command' (via the user's shell) in the directory 'context'.
     * 
     * The map 'environmentAdditions' will be added to the current environment to determine the child's starting environment.
     * 
     * Any Runnable supplied to setLaunchRunnable will be run as soon as the child process has been created.
     * Any Runnable supplied to setCompletionRunnable will be run as soon as the child process exits.
     * 
     * The 'inputDisposition' and 'outputDisposition' determine where the child's standard input comes from, and what will be done with its standard output and error.
     * It is always necessary to supply an 'errorsWindow', but 'textArea' is only needed for certain input/output dispositions.
     */
    public ShellCommand(PTextArea textArea, EErrorsWindow errorsWindow, String context, String command, Map<String, String> environmentAdditions, ToolInputDisposition inputDisposition, ToolOutputDisposition outputDisposition) {
        this.textArea = textArea;
        this.errorsWindow = errorsWindow;
        this.context = context;
        this.command = command.trim();
        this.environmentAdditions = environmentAdditions;
        this.inputDisposition = inputDisposition;
        this.outputDisposition = outputDisposition;
    }
    
    private ProcessBuilder makeProcessBuilder() {
        final ProcessBuilder processBuilder = new ProcessBuilder(ProcessUtilities.makeShellCommandArray(command));
        processBuilder.directory(FileUtilities.pathFrom(context).toFile());
        processBuilder.environment().putAll(environmentAdditions);
        return processBuilder;
    }

    public void runCommand() throws IOException {
        final String data = chooseStandardInputData();
        
        process = makeProcessBuilder().start();

        GuiUtilities.invokeLater(launchRunnable);
        
        errorsWindow.showStatus("Started task \"" + command + "\"");
        errorsWindow.taskDidStart(process);
        
        ThreadUtilities.newSingleThreadExecutor("stdin pump for " + command).execute(new StandardInputPump(data));
        startMonitoringStream(process.getInputStream(), false);
        startMonitoringStream(process.getErrorStream(), true);
    }
    
    private class StandardInputPump implements Runnable {
        private final String utf8;
        
        private StandardInputPump(String utf8) {
            this.utf8 = utf8;
        }
        
        public void run() {
            OutputStream os = process.getOutputStream();
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                out.append(utf8);
                out.flush();
                out.close();
            } catch (Exception ex) {
                Log.warn("Problem pumping standard input for task \"" + command + "\"", ex);
                errorsWindow.appendLines(true, Collections.singletonList("Problem pumping standard input for task \"" + command + "\": " + ex.getMessage() + "."));
            } finally {
                try {
                    os.close();
                } catch (IOException ex) {
                    errorsWindow.appendLines(true, Collections.singletonList("Couldn't close standard input for task \"" + command + "\": " + ex.getMessage() + "."));
                }
            }
        }
    }
    
    private String chooseStandardInputData() {
        String result = "";
        if (textArea != null) {
            switch (inputDisposition) {
            case NO_INPUT:
                break;
            case SELECTION_OR_DOCUMENT:
                result = textArea.hasSelection() ? textArea.getSelectedText() : textArea.getTextBuffer().toString();
                break;
            case DOCUMENT:
                result = textArea.getTextBuffer().toString();
                break;
            }
        }
        return result;
    }
    
    private void startMonitoringStream(InputStream stream, boolean isStdErr) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(stream, "UTF-8");
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        StreamMonitor streamMonitor = new StreamMonitor(bufferedReader, isStdErr);
        // Circumvent SwingWorker's MAX_WORKER_THREADS limit, as a ShellCommand may run for arbitrarily long.
        final String threadName = (isStdErr ? "stderr" : "stdout") + " pump for " + command;
        ThreadUtilities.newSingleThreadExecutor(threadName).execute(streamMonitor);
    }
    
    // We use SwingWorker to batch up groups of lines rather than process each one individually.
    private class StreamMonitor extends SwingWorker<Void, String> {
        private final BufferedReader stream;
        private final boolean isStdErr;
        
        private StreamMonitor(BufferedReader stream, boolean isStdErr) {
            this.stream = stream;
            this.isStdErr = isStdErr;
        }
        
        @Override protected Void doInBackground() throws IOException {
            streamOpened();
            String line;
            while ((line = stream.readLine()) != null) {
                publish(line);
            }
            return null;
        }
        
        @Override protected void done() {
            try {
                // Wait for the stream to empty and all lines to have been processed.
                get();
            } catch (Exception ex) {
                Log.warn("Unexpected failure", ex);
            }
            streamClosed();
        }
        
        @Override protected void process(List<String> lines) {
            processLines(isStdErr, lines);
        }
    }
    
    /**
     * Invoked on the EDT by StreamMonitor.process.
     */
    private void processLines(boolean isStdErr, List<String> lines) {
        switch (outputDisposition) {
        case CREATE_NEW_DOCUMENT:
            Log.warn("CREATE_NEW_DOCUMENT not yet implemented.");
            break;
        case DISCARD:
            break;
        case ERRORS_WINDOW:
            errorsWindow.appendLines(isStdErr, lines);
            break;
        case REPLACE_IF_OK:
            if (isStdErr) {
                errorsWindow.appendLines(isStdErr, lines);
            } else {
                for (String line : lines) {
                    capturedOutput.append(line).append('\n');
                }
            }
            break;
        case CLIPBOARD:
        case DIALOG:
        case INSERT:
        case REPLACE:
            for (String line : lines) {
                capturedOutput.append(line).append('\n');
            }
            break;
        }
    }
    
    /**
     * Invoked on the EDT when the StreamMonitors finish.
     */
    private void processFinished() {
        // Get the process' exit status.
        // FIXME: strictly, we don't know the process exited, only that it closed its streams.
        int exitStatus = 0;
        try {
            exitStatus = process.waitFor();
        } catch (InterruptedException ex) {
            Log.warn("Process.waitFor interrupted", ex);
        }
        
        // Deal with the output we may have collected.
        switch (outputDisposition) {
        case CLIPBOARD:
            StringSelection selection = new StringSelection(capturedOutput.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            break;
        case CREATE_NEW_DOCUMENT:
            Log.warn("CREATE_NEW_DOCUMENT not yet implemented.");
            break;
        case DIALOG:
            JFrameUtilities.showTextWindow(Evergreen.getInstance().getFrame(), "Subprocess Output", capturedOutput.toString());
            break;
        case DISCARD:
            break;
        case ERRORS_WINDOW:
            // We dealt with the sub-process output as we went along.
            break;
        case INSERT:
            textArea.replaceSelection(capturedOutput);
            break;
        case REPLACE_IF_OK:
            if (exitStatus == 0) {
                if (textArea.hasSelection()) {
                    textArea.replaceSelection(capturedOutput);
                } else {
                    textArea.setText(capturedOutput);
                }
            }
            break;
        case REPLACE:
            if (textArea.hasSelection()) {
                textArea.replaceSelection(capturedOutput);
            } else {
                textArea.setText(capturedOutput);
            }
            break;
        }
        
        // Keep the errors window informed.
        errorsWindow.showStatus("Task \"" + command + "\" finished");
        if (exitStatus != 0) {
            // A non-zero exit status is always potentially interesting.
            errorsWindow.appendLines(true, Collections.singletonList("Task \"" + command + "\" failed with exit status " + exitStatus));
        }
        errorsWindow.taskDidExit(exitStatus);
        
        // Run any user-specified completion code.
        GuiUtilities.invokeLater(completionRunnable);
    }
    
    /**
     * Invoked by StreamMonitor when one of this task's streams is opened.
     */
    private synchronized void streamOpened() {
        ++openStreamCount;
    }
    
    /**
     * Invoked on the EDT by StreamMonitor when one of this task's streams is closed.
     * If there are no streams left open, we assume the process has exited.
     */
    private synchronized void streamClosed() {
        openStreamCount--;
        if (openStreamCount == 0) {
            processFinished();
        }
    }
    
    /**
     * Returns the command supplied to the constructor.
     */
    public String getCommand() {
        return this.command;
    }
    
    /**
     * Sets the Runnable to be invoked on the event dispatch thread when the
     * shell command completes.
     */
    public void setCompletionRunnable(Runnable completionRunnable) {
        this.completionRunnable = completionRunnable;
    }
    
    /**
     * Sets the Runnable to be invoked on the event dispatch thread when the
     * shell command is started.
     */
    public void setLaunchRunnable(Runnable launchRunnable) {
        this.launchRunnable = launchRunnable;
    }
}
