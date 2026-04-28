package com.detectautoalchers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
            1_000L
        );

        SwingUtilities.invokeAndWait(() -> panel.refresh(Collections.singletonList(result), 2_000L));

        assertTrue(containsLabel(panel, "magic: 89"));
        assertTrue(containsLabel(panel, "non-magic total: 40"));
        assertTrue(containsLabel(panel, "confidence: high"));
        assertTrue(containsLabel(panel, "score: 120"));
        assertFalse(containsLabel(panel, "magic: 89  other"));
    }

    private boolean containsLabel(Component component, String text)
    {
        if (component instanceof JLabel && ((JLabel) component).getText().contains(text))
        {
            return true;
        }

        if (component instanceof JScrollPane)
        {
            return containsLabel(((JScrollPane) component).getViewport(), text);
        }

        if (component instanceof JViewport)
        {
            return containsLabel(((JViewport) component).getView(), text);
        }

        if (component instanceof JPanel)
        {
            for (Component child : ((JPanel) component).getComponents())
            {
                if (containsLabel(child, text))
                {
                    return true;
                }
            }
        }

        return false;
    }
}
