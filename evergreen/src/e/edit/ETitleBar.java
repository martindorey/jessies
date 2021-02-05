package e.edit;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import e.gui.*;
import e.util.*;

/**
 * A simple title-bar with a label and a right-aligned close button.
 */
public class ETitleBar extends JPanel {
    /**
     * Ensures that all title bars correctly reflect the focus ownership of
     * their associated windows.
     */
    static {
        new KeyboardFocusMonitor() {
            public void focusChanged(Component oldOwner, Component newOwner) {
                setActive(oldOwner, false);
                setActive(newOwner, true);
            }
            
            public void setActive(Component c, boolean active) {
                EWindow window = (EWindow) SwingUtilities.getAncestorOfClass(EWindow.class, c);
                if (window == null) {
                    return;
                }
                window.getTitleBar().setActive(active);
                // Make sure the JScrollPane focus ring is redrawn on Mac OS.
                window.repaint();
            }
        };
    }
    
    public static final int TITLE_HEIGHT = GuiUtilities.scaleSizeForText(19);
    
    private final EWindow window;
    private final JLabel titleLabel;
    private final JPanel buttonsPanel;
    private final ECloseButton closeButton;
    
    private ESwitchButton switchButton;
    private ESwitchToTestButton switchToTestButton;
    
    private boolean isActive;
    
    public ETitleBar(String title, EWindow window) {
        super(new BorderLayout());
        
        this.window = window;
        
        this.titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont(GuiUtilities.isWindows() ? "InternalFrame.titleFont" : "TableHeader.font"));
        titleLabel.setOpaque(false);
        
        if (GuiUtilities.isGtk()) {
            add(Box.createHorizontalStrut(2), BorderLayout.WEST);
        }
        add(titleLabel, BorderLayout.CENTER);
        
        // We use the BorderLayout within the buttonsPanel so as to have control over the order.
        // The close button must always be rightmost, and the 'open header' and 'open test' should
        // also be in a consistent order. However, BorderLayout only allows us to go up to three
        // buttons in this way. If we want to add a further button, we're going to need to switch
        // to GridLayout or some such.
        this.buttonsPanel = new JPanel(new BorderLayout());
        buttonsPanel.setOpaque(false);
        add(buttonsPanel, BorderLayout.EAST);
        
        this.closeButton = new ECloseButton(window);
        buttonsPanel.add(closeButton, BorderLayout.EAST);
        
        setActive(false);
        initListener();
    }
    
    private void initListener() {
        addMouseListener(new MouseAdapter() {
            /**
             * Gives a window focus when its title-bar is selected.
             */
            public void mousePressed(MouseEvent e) {
                window.requestFocus();
            }
            
            /**
             * Expands this window to maximum size on a double-click.
             */
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    window.expand();
                }
            }
        });
    }
    
    public void setShowSwitchButton(boolean shouldShow) {
        if (switchButton == null && shouldShow) {
            this.switchButton = new ESwitchButton((ETextWindow) window);
            buttonsPanel.add(switchButton, BorderLayout.CENTER);
            forceRepaint();
        } else if (switchButton != null && shouldShow == false) {
            buttonsPanel.remove(switchButton);
            this.switchButton = null;
            forceRepaint();
        }
    }
    
    public void setShowSwitchTestButton(boolean shouldShow) {
        if (switchToTestButton == null && shouldShow) {
            this.switchToTestButton = new ESwitchToTestButton((ETextWindow) window);
            buttonsPanel.add(switchToTestButton, BorderLayout.WEST);
            forceRepaint();
        } else if (switchToTestButton != null && shouldShow == false) {
            buttonsPanel.remove(switchToTestButton);
            this.switchToTestButton = null;
            forceRepaint();
        }
    }
    
    private void forceRepaint() {
        invalidate();
        validate();
        repaint();
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean isActive) {
        this.isActive = isActive;
        updateColors();
    }
    
    // FIXME: can we arrange to get this called whenever the system theme changes?
    private void updateColors() {
        Color activeForeground;
        Color activeBackground;
        Color inactiveForeground;
        Color inactiveBackground;
        if (GuiUtilities.isGtk()) {
            // The Java 6 GTK+ LAF doesn't offer useful InternalFrame colors.
            // The TabbedPane colors might seem more sensible, but somehow that
            // ends up looking the wrong way round; dark seems more "focused".
            // The TextArea colors seem reasonable, at least for the Solaris 10
            // default theme. I don't know how much this is likely to be
            // affected by other themes.
            activeBackground = UIManager.getColor("TextArea.selectionBackground");
            activeForeground = UIManager.getColor("TextArea.selectionForeground");
            inactiveBackground = UIManager.getColor("TextArea.background");
            inactiveForeground = UIManager.getColor("TextArea.foreground");
        } else {
            activeBackground = UIManager.getColor("InternalFrame.activeTitleBackground");
            activeForeground = UIManager.getColor("InternalFrame.activeTitleForeground");
            inactiveBackground = UIManager.getColor("InternalFrame.inactiveTitleBackground");
            inactiveForeground = UIManager.getColor("InternalFrame.inactiveTitleForeground");
        }
        
        // Use the JInternalFrame colors to distinguish focused from unfocused windows.
        //
        // On Mac OS, although you might expect that this gives exactly the
        // result you see with JInternalFrame, it doesn't. As of 1.4.2, Apple
        // invoke apple.laf.AquaImageFactory's drawFrameTitleBackground method
        // rather than using these colors. We could use reflection to do the
        // same, but that seems unnecessarily fragile.
        // 
        // We set the title label foreground to affect the text, and the button
        // panel's foreground for the benefit of the buttons.
        if (isActive) {
            setBackground(activeBackground);
            buttonsPanel.setForeground(activeForeground);
            titleLabel.setForeground(activeForeground);
        } else {
            setBackground(inactiveBackground);
            buttonsPanel.setForeground(inactiveForeground);
            titleLabel.setForeground(inactiveForeground);
        }
    }
}
