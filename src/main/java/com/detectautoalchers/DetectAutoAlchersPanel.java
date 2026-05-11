package com.detectautoalchers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

final class DetectAutoAlchersPanel extends PluginPanel
{
    interface Actions
    {
        void clearReportedHistory();

        void importReportedHistory();

        void exportReportedHistory();

        void watch(String normalizedName, String displayName);

        void override(String normalizedName, String displayName);

        void removeWatch(String normalizedName);

        void removeOverride(String normalizedName);

        void applyPreset(DetectionPreset preset);
    }

    private static final Color BACKGROUND = new Color(35, 35, 35);
    private static final Color ROW_BACKGROUND = new Color(45, 45, 45);
    private static final Color HIGH_CONFIDENCE_ALERT = new Color(255, 83, 83);
    private static final Color MODERATE_CONFIDENCE_ALERT = new Color(255, 220, 64);
    private static final Color WATCH_ALERT = new Color(120, 190, 255);
    private static final Color SCORE_INCREASE = new Color(255, 120, 120);
    private static final Color SCORE_DECREASE = new Color(120, 220, 140);
    private static final Color TEXT = new Color(230, 230, 230);
    private static final Color MUTED = new Color(170, 170, 170);
    private static final int PANEL_CONTENT_WIDTH = 220;

    private final JPanel content = new JPanel();
    private final Actions actions;

    DetectAutoAlchersPanel()
    {
        this(null);
    }

    DetectAutoAlchersPanel(Actions actions)
    {
        super(false);
        this.actions = actions;
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
        refresh(suspects, nowMillis, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), null, false);
    }

    void refresh(
        List<SuspicionResult> suspects,
        long nowMillis,
        Collection<ReportedPlayer> watchedPlayers,
        Collection<ReportedPlayer> overridePlayers,
        Map<String, SuspicionResult> watchedResults,
        SuspicionResult examinedResult,
        boolean compact)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> refresh(
                suspects,
                nowMillis,
                watchedPlayers,
                overridePlayers,
                watchedResults,
                examinedResult,
                compact
            ));
            return;
        }

        content.removeAll();
        addHeader("Suspected auto-alchers");
        addHistoryButtons();
        addPresetButtons();
        addExaminedPlayer(examinedResult, nowMillis);
        addSection(content, "Suspects");
        if (suspects.isEmpty())
        {
            addMuted("No suspects in range.");
        }
        else
        {
            for (SuspicionResult suspect : suspects)
            {
                addSuspect(suspect, nowMillis, compact);
                content.add(Box.createRigidArea(new Dimension(0, 6)));
            }
        }

        addPlayerList("Watchlist", watchedPlayers, watchedResults, nowMillis, true);
        addPlayerList("Override list", overridePlayers, Collections.emptyMap(), nowMillis, false);

        content.revalidate();
        content.repaint();
    }

    private void refreshEmpty()
    {
        content.removeAll();
        addHeader("Suspected auto-alchers");
        addHistoryButtons();
        addPresetButtons();
        addExaminedPlayer(null, System.currentTimeMillis());
        addSection(content, "Suspects");
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

    private void addHistoryButtons()
    {
        if (actions == null)
        {
            return;
        }

        JPanel row = buttonRow();
        addButton(row, "Import", actions::importReportedHistory);
        addButton(row, "Export", actions::exportReportedHistory);
        addButtonRow(row);

        JPanel clearRow = buttonRow();
        addButton(clearRow, "Clear", actions::clearReportedHistory);
        addButtonRow(clearRow);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addPresetButtons()
    {
        if (actions == null)
        {
            return;
        }

        addSection(content, "Detection Presets");
        JPanel row = buttonRow();
        addButton(row, "Conservative", () -> actions.applyPreset(DetectionPreset.CONSERVATIVE));
        addButton(row, "Balanced", () -> actions.applyPreset(DetectionPreset.BALANCED));
        addButtonRow(row);

        JPanel aggroRow = buttonRow();
        addButton(aggroRow, "Aggressive", () -> actions.applyPreset(DetectionPreset.AGGRESSIVE));
        addButtonRow(aggroRow);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private JPanel buttonRow()
    {
        JPanel row = new JPanel();
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void addButtonRow(JPanel row)
    {
        row.setMaximumSize(new Dimension(PANEL_CONTENT_WIDTH, row.getPreferredSize().height));
        content.add(row);
    }

    private void addButton(JPanel row, String text, Runnable callback)
    {
        JButton button = new JButton(text);
        button.addActionListener(event -> callback.run());
        row.add(button);
    }

    private void addMuted(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
    }

    private void addExaminedPlayer(SuspicionResult examinedResult, long nowMillis)
    {
        addSection(content, "<html>Examine Player Score<br>(Right Click Player Menu)</html>");
        if (examinedResult == null)
        {
            addMuted("No examined player.");
            content.add(Box.createRigidArea(new Dimension(0, 8)));
            return;
        }

        addSuspect(examinedResult, nowMillis, false);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void addSuspect(SuspicionResult suspect, long nowMillis, boolean compact)
    {
        Color alert = alertColor(suspect);
        JPanel row = createRow(alert);

        JLabel name = new JLabel(suspect.getDisplayName());
        name.setForeground(alert);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(name);
        row.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (compact)
        {
            addCompactDetails(details, suspect, nowMillis);
        }
        else
        {
            addFullDetails(details, suspect, nowMillis);
        }

        row.add(details);
        addSuspectButtons(row, suspect);
        row.setMaximumSize(new Dimension(PANEL_CONTENT_WIDTH, row.getPreferredSize().height));
        content.add(row);
    }

    private Color alertColor(SuspicionResult suspect)
    {
        if (suspect.isHighConfidence())
        {
            return HIGH_CONFIDENCE_ALERT;
        }
        if (suspect.getConfidence() == DetectionConfidence.MODERATE)
        {
            return MODERATE_CONFIDENCE_ALERT;
        }
        return MUTED;
    }

    private JPanel createRow(Color alert)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ROW_BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(alert.darker()),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void addSuspectButtons(JPanel row, SuspicionResult suspect)
    {
        if (actions == null)
        {
            return;
        }

        JPanel buttons = buttonRow();
        addButton(buttons, "Watch", () -> actions.watch(suspect.getNormalizedName(), suspect.getDisplayName()));
        addButton(buttons, "Override", () -> actions.override(suspect.getNormalizedName(), suspect.getDisplayName()));
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        row.add(buttons);
    }

    private void addCompactDetails(JPanel details, SuspicionResult suspect, long nowMillis)
    {
        addDetail(details, "confidence: " + suspect.getConfidenceLabel());
        addDetail(details, "score: " + suspect.getScore() + "  casts: " + suspect.getCastCount());
        addDetail(details, "seen: " + secondsSince(suspect.getLastSeenMillis(), nowMillis) + "s");
        addDetail(details, "magic: " + formatLevel(suspect.getMagicLevel()) + "  hiscore: " + suspect.getHiscoreStatus());
    }

    private void addFullDetails(JPanel details, SuspicionResult suspect, long nowMillis)
    {
        ScoreBreakdown breakdown = suspect.getScoreBreakdown();

        addSection(details, "Status");
        addDetail(details, "confidence: " + suspect.getConfidenceLabel());
        addDetail(details, "seen: " + secondsSince(suspect.getLastSeenMillis(), nowMillis) + "s");

        addSection(details, "Score");
        for (String label : breakdown.getScoreLabels())
        {
            addScoreDetail(details, label);
        }
        addDetail(details, "positive total: " + breakdown.getPositiveTotal(), SCORE_INCREASE);
        addDetail(details, "penalties: -" + breakdown.getPenaltyTotal(), SCORE_DECREASE);
        addDetail(details, "total: " + suspect.getScore());

        addSection(details, "Evidence");
        addDetail(details, "casts: " + suspect.getCastCount());
        addDetail(details, "magic: " + formatLevel(suspect.getMagicLevel()));
        addDetail(details, "staff: " + yesNo(suspect.isStaffMatch()));
        addDetail(details, "cadence: " + yesNo(suspect.isConsistentCadence()));
        addDetail(details, "cast gate: " + passedFailed(breakdown.isCastGatePassed()));
        addDetail(details, "staff gate: " + passedFailed(breakdown.isStaffGatePassed()));
        addDetail(details, "detection gate: " + passedFailed(breakdown.isDetectionGatePassed()));

        addSection(details, "Hiscores");
        addDetail(details, "hiscore status: " + suspect.getHiscoreStatus());
        addDetail(details, "magic profile: " + yesNo(suspect.isMagicDominant()));
        addDetail(details, "high magic: " + yesNo(suspect.isHighMagic()));
        addDetail(details, "other skills: " + formatCount(suspect.getNonMagicSkillsAboveThreshold()));
        addDetail(details, "non-magic total level: " + formatCount(suspect.getNonMagicTotalLevel()));
        addDetail(details, "clues + collection log: " + formatCount(suspect.getClueAndCollectionLogTotal()));

        addSection(details, "Reductions");
        addDetail(details, "non-magic reduction: " + yesNo(suspect.isMatureAccountSuppressed()));
        addDetail(details, "played activity: " + yesNo(suspect.isClueCollectionActivitySuppressed()));
        details.setMaximumSize(new Dimension(PANEL_CONTENT_WIDTH, details.getPreferredSize().height));
    }

    private void addPlayerList(
        String title,
        Collection<ReportedPlayer> players,
        Map<String, SuspicionResult> currentResults,
        long nowMillis,
        boolean watchlist)
    {
        addSection(content, title);
        if (players.isEmpty())
        {
            addMuted("No players.");
            return;
        }

        for (ReportedPlayer player : players)
        {
            JPanel row = createRow(watchlist ? WATCH_ALERT : MUTED);
            JLabel name = new JLabel(player.getDisplayName());
            name.setForeground(watchlist ? WATCH_ALERT : TEXT);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(name);

            SuspicionResult result = currentResults.get(player.getNormalizedName());
            if (result != null)
            {
                addDetail(row, "score: " + result.getScore() + "  casts: " + result.getCastCount());
                addDetail(row, "seen: " + secondsSince(result.getLastSeenMillis(), nowMillis) + "s");
            }
            else if (watchlist)
            {
                addDetail(row, "not currently seen");
            }

            if (actions != null)
            {
                JPanel buttons = buttonRow();
                if (watchlist)
                {
                    addButton(buttons, "Remove", () -> actions.removeWatch(player.getNormalizedName()));
                }
                else
                {
                    addButton(buttons, "Remove", () -> actions.removeOverride(player.getNormalizedName()));
                }
                buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
                row.add(buttons);
            }

            row.setMaximumSize(new Dimension(PANEL_CONTENT_WIDTH, row.getPreferredSize().height));
            content.add(row);
            content.add(Box.createRigidArea(new Dimension(0, 6)));
        }
    }

    private void addDetail(JPanel panel, String text)
    {
        addDetail(panel, text, MUTED);
    }

    private void addScoreDetail(JPanel panel, String text)
    {
        addDetail(panel, text, scoreLineColor(text));
    }

    private void addDetail(JPanel panel, String text, Color color)
    {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
    }

    private Color scoreLineColor(String text)
    {
        if (text.contains(" -"))
        {
            return SCORE_DECREASE;
        }

        if (text.contains(" +"))
        {
            return SCORE_INCREASE;
        }

        return MUTED;
    }

    private void addSection(JPanel panel, String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        panel.add(label);
    }

    private String yesNo(boolean value)
    {
        return value ? "yes" : "no";
    }

    private String passedFailed(boolean value)
    {
        return value ? "passed" : "failed";
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
