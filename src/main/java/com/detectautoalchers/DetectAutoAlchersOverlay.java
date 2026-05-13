package com.detectautoalchers;

import com.google.inject.name.Named;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

final class DetectAutoAlchersOverlay extends Overlay
{
    private static final Color HIGH_CONFIDENCE_OUTLINE_COLOR = new Color(255, 48, 48);
    private static final Color MODERATE_CONFIDENCE_OUTLINE_COLOR = new Color(255, 220, 64);
    private static final Color WATCHLIST_OUTLINE_COLOR = new Color(120, 190, 255);

    private final Client client;
    private final DetectorService detectorService;
    private final ReportedPlayerStore reportedPlayerStore;
    private final ReportedPlayerStore watchlistStore;
    private final DetectAutoAlchersConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    DetectAutoAlchersOverlay(
        Client client,
        DetectorService detectorService,
        ReportedPlayerStore reportedPlayerStore,
        @Named("watchlist") ReportedPlayerStore watchlistStore,
        DetectAutoAlchersConfig config,
        ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.detectorService = detectorService;
        this.reportedPlayerStore = reportedPlayerStore;
        this.watchlistStore = watchlistStore;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

        for (Player player : worldView.players())
        {
            if (player == null || player.getName() == null)
            {
                continue;
            }

            if (config.highlightReportedPlayers() && reportedPlayerStore.contains(player.getName()))
            {
                drawPlayer(graphics, player, config.reportedPlayerHighlightColor());
            }
            else if (watchlistStore.contains(player.getName()))
            {
                drawPlayer(graphics, player, WATCHLIST_OUTLINE_COLOR);
            }
            else
            {
                Color confidenceColor = confidenceColor(detectorService.getConfidence(player.getName()));
                if (confidenceColor != null)
                {
                    drawPlayer(graphics, player, confidenceColor);
                }
            }
        }

        return null;
    }

    private Color confidenceColor(DetectionConfidence confidence)
    {
        if (confidence == DetectionConfidence.HIGH)
        {
            return HIGH_CONFIDENCE_OUTLINE_COLOR;
        }
        if (confidence == DetectionConfidence.MODERATE)
        {
            return MODERATE_CONFIDENCE_OUTLINE_COLOR;
        }
        return null;
    }

    private void drawPlayer(Graphics2D graphics, Player player, Color color)
    {
        try
        {
            modelOutlineRenderer.drawOutline(player, 2, color, 3);
            return;
        }
        catch (RuntimeException ignored)
        {
            // Fall through to a simple hull outline if model outlines cannot render this frame.
        }

        Shape hull = player.getConvexHull();
        if (hull == null)
        {
            hull = player.getCanvasTilePoly();
        }

        if (hull != null)
        {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(hull);
        }
    }

}
