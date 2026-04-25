package com.detectautoalchers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Duration;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

final class DetectAutoAlchersPanel extends PluginPanel
{
    private static final Color BACKGROUND = new Color(35, 35, 35);
    private static final Color ROW_BACKGROUND = new Color(45, 45, 45);
    private static final Color ALERT = new Color(255, 83, 83);
    private static final Color TEXT = new Color(230, 230, 230);
    private static final Color MUTED = new Color(170, 170, 170);

    private final JPanel content = new JPanel();

    DetectAutoAlchersPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        refreshEmpty();
    }

    void refresh(List<SuspicionResult> suspects, long nowMillis)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> refresh(suspects, nowMillis));
            return;
        }

        content.removeAll();
        addHeader("Suspected auto-alchers");
        if (suspects.isEmpty())
        {
            addMuted("No suspects in range.");
        }
        else
        {
            for (SuspicionResult suspect : suspects)
            {
                addSuspect(suspect, nowMillis);
                content.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }

        content.revalidate();
        content.repaint();
    }

    private void refreshEmpty()
    {
        content.removeAll();
        addHeader("Suspected auto-alchers");
        addMuted("No suspects in range.");
    }

    private void addHeader(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        content.add(label);
    }

    private void addMuted(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
    }

    private void addSuspect(SuspicionResult suspect, long nowMillis)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ROW_BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ALERT.darker()),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(suspect.getDisplayName() + "  [" + suspect.getScore() + "]");
        name.setForeground(ALERT);
        row.add(name);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 2));
        details.setOpaque(false);
        addDetail(details, "casts: " + suspect.getCastCount());
        addDetail(details, "staff: " + yesNo(suspect.isStaffMatch()) + "  weapon: " + suspect.getWeaponId());
        addDetail(details, "magic: " + formatLevel(suspect.getMagicLevel())
            + "  other>threshold: " + formatCount(suspect.getNonMagicSkillsAboveThreshold()));
        addDetail(details, "hiscore: " + suspect.getHiscoreStatus()
            + "  cadence: " + yesNo(suspect.isConsistentCadence()));
        addDetail(details, "world: " + suspect.getWorld()
            + "  distance: " + suspect.getDistance()
            + "  seen: " + secondsSince(suspect.getLastSeenMillis(), nowMillis) + "s");
        row.add(details);

        content.add(row);
    }

    private void addDetail(JPanel panel, String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        panel.add(label);
    }

    private String yesNo(boolean value)
    {
        return value ? "yes" : "no";
    }

    private String formatLevel(int level)
    {
        return level < 0 ? "?" : Integer.toString(level);
    }

    private String formatCount(int count)
    {
        return count < 0 ? "?" : Integer.toString(count);
    }

    private long secondsSince(long thenMillis, long nowMillis)
    {
        return Math.max(0, Duration.ofMillis(nowMillis - thenMillis).getSeconds());
    }
}
