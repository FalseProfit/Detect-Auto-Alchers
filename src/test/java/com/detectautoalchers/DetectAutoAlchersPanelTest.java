package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class DetectAutoAlchersPanelTest
{
    @Test
    public void suspectDetailsUseCompactPreferredHeight() throws InvocationTargetException, InterruptedException
    {
        DetectAutoAlchersPanel panel = new DetectAutoAlchersPanel();
        SuspicionResult result = new SuspicionResult(
            "compact alcher",
            "Compact Alcher",
            120,
            DetectionConfidence.HIGH,
            12,
            true,
            true,
            true,
            false,
            true,
            89,
            0,
            40,
            false,
            1,
            2,
            3,
            false,
            "found",
            431,
            2,
            StaffClassifier.STAFF_OF_FIRE,
            1_000L,
            new ScoreBreakdown(
                30,
                50,
                30,
                0,
                10,
                100,
                0,
                true,
                true,
                true
            )
        );

        SwingUtilities.invokeAndWait(() -> panel.refresh(Collections.singletonList(result), 2_000L));

        assertTrue(containsLabel(panel, "magic: 89"));
        assertTrue(containsLabel(panel, "non-magic total level: 40"));
        assertTrue(containsLabel(panel, "confidence: high"));
        assertTrue(containsLabel(panel, "total: 120"));
        assertTrue(containsLabel(panel, "staff +30"));
        assertTrue(containsLabel(panel, "casts +50"));
        assertTrue(containsLabel(panel, "cadence +10"));
        assertTrue(containsLabel(panel, "magic profile +30"));
        assertTrue(containsLabel(panel, "non-magic total level -100"));
        assertEquals(new Color(255, 120, 120), labelForeground(panel, "staff +30"));
        assertEquals(new Color(120, 220, 140), labelForeground(panel, "non-magic total level -100"));
        assertEquals(new Color(255, 120, 120), labelForeground(panel, "positive total: 120"));
        assertEquals(new Color(120, 220, 140), labelForeground(panel, "penalties: -100"));
        assertTrue(containsLabel(panel, "cast gate: passed"));
        assertTrue(containsLabel(panel, "staff gate: passed"));
        assertTrue(containsLabel(panel, "hiscore status: found"));
        assertFalse(containsLabel(panel, "world:"));
        assertFalse(containsLabel(panel, "distance:"));
        assertFalse(containsLabel(panel, "magic: 89  other"));
    }

    @Test
    public void compactModeShowsShortSuspectRows() throws InvocationTargetException, InterruptedException
    {
        DetectAutoAlchersPanel panel = new DetectAutoAlchersPanel();
        SuspicionResult result = result();

        SwingUtilities.invokeAndWait(() -> panel.refresh(
            Collections.singletonList(result),
            2_000L,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            true
        ));

        assertTrue(containsLabel(panel, "score: 120  casts: 12"));
        assertTrue(containsLabel(panel, "magic: 89  hiscore: found"));
        assertFalse(containsLabel(panel, "positive total: 120"));
        assertFalse(containsLabel(panel, "detection gate: passed"));
    }

    @Test
    public void showsWatchlistAndOverrideListSections() throws InvocationTargetException, InterruptedException
    {
        DetectAutoAlchersPanel panel = new DetectAutoAlchersPanel();
        ReportedPlayer watched = new ReportedPlayer("watched bot", "Watched Bot", Instant.EPOCH);
        ReportedPlayer override = new ReportedPlayer("override bot", "Override Bot", Instant.EPOCH);

        SwingUtilities.invokeAndWait(() -> panel.refresh(
            Collections.emptyList(),
            2_000L,
            List.of(watched),
            List.of(override),
            Map.of("watched bot", result()),
            null,
            false
        ));

        assertTrue(containsLabel(panel, "Watchlist"));
        assertTrue(containsLabel(panel, "Watched Bot"));
        assertTrue(containsLabel(panel, "score: 120  casts: 12"));
        assertTrue(containsLabel(panel, "Override list"));
        assertTrue(containsLabel(panel, "Override Bot"));
    }

    @Test
    public void topControlRowsFitPanelWidth() throws InvocationTargetException, InterruptedException
    {
        DetectAutoAlchersPanel panel = new DetectAutoAlchersPanel(new NoopActions());

        SwingUtilities.invokeAndWait(() -> panel.refresh(Collections.emptyList(), 2_000L));

        assertTrue(maxButtonRowWidth(panel) <= 220);
        assertTrue(containsButton(panel, "Clear"));
        assertFalse(containsButton(panel, "Clear reported"));
        assertTrue(containsLabel(panel, "Detection Presets"));
        assertTrue(containsButton(panel, "Conservative"));
        assertTrue(containsButton(panel, "Aggressive"));
    }

    @Test
    public void showsExaminedPlayerSectionBeforeSuspectsWithFullDetails()
        throws InvocationTargetException, InterruptedException
    {
        DetectAutoAlchersPanel panel = new DetectAutoAlchersPanel(new NoopActions());

        SwingUtilities.invokeAndWait(() -> panel.refresh(
            Collections.emptyList(),
            2_000L,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            result(),
            true
        ));

        assertTrue(containsLabel(panel, "Examine Player Score<br>(Right Click Player Menu)"));
        assertTrue(containsLabel(panel, "Suspects"));
        assertTrue(containsLabel(panel, "Compact Alcher"));
        assertTrue(containsLabel(panel, "positive total: 120"));
        assertTrue(containsLabel(panel, "detection gate: passed"));
        assertTrue(containsLabel(panel, "No suspects in range."));
        assertTrue(containsButton(panel, "Watch"));
        assertTrue(containsButton(panel, "Override"));

        List<String> labels = labelTexts(panel);
        assertTrue(labels.indexOf("Detection Presets") < labels.indexOf("<html>Examine Player Score<br>(Right Click Player Menu)</html>"));
        assertTrue(labels.indexOf("<html>Examine Player Score<br>(Right Click Player Menu)</html>") < labels.indexOf("Suspects"));
    }

    private SuspicionResult result()
    {
        return new SuspicionResult(
            "compact alcher",
            "Compact Alcher",
            120,
            DetectionConfidence.HIGH,
            12,
            true,
            true,
            true,
            false,
            true,
            89,
            0,
            40,
            false,
            1,
            2,
            3,
            false,
            "found",
            431,
            2,
            StaffClassifier.STAFF_OF_FIRE,
            1_000L,
            new ScoreBreakdown(
                30,
                50,
                30,
                0,
                10,
                100,
                0,
                true,
                true,
                true
            )
        );
    }

    private Color labelForeground(Component component, String text)
    {
        JLabel label = findLabel(component, text);
        return label == null ? null : label.getForeground();
    }

    private JLabel findLabel(Component component, String text)
    {
        if (component instanceof JLabel && ((JLabel) component).getText().contains(text))
        {
            return (JLabel) component;
        }

        if (component instanceof JScrollPane)
        {
            return findLabel(((JScrollPane) component).getViewport(), text);
        }

        if (component instanceof JViewport)
        {
            return findLabel(((JViewport) component).getView(), text);
        }

        if (component instanceof JPanel)
        {
            for (Component child : ((JPanel) component).getComponents())
            {
                JLabel label = findLabel(child, text);
                if (label != null)
                {
                    return label;
                }
            }
        }

        return null;
    }

    private boolean containsLabel(Component component, String text)
    {
        return findLabel(component, text) != null;
    }

    private List<String> labelTexts(Component component)
    {
        List<String> labels = new ArrayList<>();
        collectLabelTexts(component, labels);
        return labels;
    }

    private void collectLabelTexts(Component component, List<String> labels)
    {
        if (component instanceof JLabel)
        {
            labels.add(((JLabel) component).getText());
            return;
        }

        if (component instanceof JScrollPane)
        {
            collectLabelTexts(((JScrollPane) component).getViewport(), labels);
            return;
        }

        if (component instanceof JViewport)
        {
            collectLabelTexts(((JViewport) component).getView(), labels);
            return;
        }

        if (component instanceof JPanel)
        {
            for (Component child : ((JPanel) component).getComponents())
            {
                collectLabelTexts(child, labels);
            }
        }
    }

    private int maxButtonRowWidth(Component component)
    {
        int width = 0;
        if (component instanceof JPanel)
        {
            JPanel panel = (JPanel) component;
            boolean hasButton = false;
            for (Component child : panel.getComponents())
            {
                hasButton |= child instanceof JButton;
                width = Math.max(width, maxButtonRowWidth(child));
            }
            if (hasButton)
            {
                width = Math.max(width, panel.getMaximumSize().width);
            }
        }
        else if (component instanceof JScrollPane)
        {
            width = maxButtonRowWidth(((JScrollPane) component).getViewport());
        }
        else if (component instanceof JViewport)
        {
            width = maxButtonRowWidth(((JViewport) component).getView());
        }
        return width;
    }

    private boolean containsButton(Component component, String text)
    {
        if (component instanceof JButton && text.equals(((JButton) component).getText()))
        {
            return true;
        }
        if (component instanceof JScrollPane)
        {
            return containsButton(((JScrollPane) component).getViewport(), text);
        }
        if (component instanceof JViewport)
        {
            return containsButton(((JViewport) component).getView(), text);
        }
        if (component instanceof JPanel)
        {
            for (Component child : ((JPanel) component).getComponents())
            {
                if (containsButton(child, text))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class NoopActions implements DetectAutoAlchersPanel.Actions
    {
        @Override
        public void clearReportedHistory()
        {
        }

        @Override
        public void importReportedHistory()
        {
        }

        @Override
        public void exportReportedHistory()
        {
        }

        @Override
        public void watch(String normalizedName, String displayName)
        {
        }

        @Override
        public void override(String normalizedName, String displayName)
        {
        }

        @Override
        public void removeWatch(String normalizedName)
        {
        }

        @Override
        public void removeOverride(String normalizedName)
        {
        }

        @Override
        public void applyPreset(DetectionPreset preset)
        {
        }
    }
}
