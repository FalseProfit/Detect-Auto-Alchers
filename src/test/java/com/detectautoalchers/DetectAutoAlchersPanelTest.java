package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import javax.swing.JLabel;
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
        assertTrue(containsLabel(panel, "score: 120"));
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
}
