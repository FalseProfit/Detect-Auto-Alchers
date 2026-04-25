package com.detectautoalchers;

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
    private static final Color OUTLINE_COLOR = new Color(255, 48, 48);

    private final Client client;
    private final DetectorService detectorService;
    private final DetectAutoAlchersConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    DetectAutoAlchersOverlay(
        Client client,
        DetectorService detectorService,
        DetectAutoAlchersConfig config,
        ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.detectorService = detectorService;
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
            if (player == null || player.getName() == null || !detectorService.isSuspicious(player.getName()))
            {
                continue;
            }

            drawPlayer(graphics, player);
        }

        return null;
    }

    private void drawPlayer(Graphics2D graphics, Player player)
    {
        try
        {
            modelOutlineRenderer.drawOutline(player, 2, OUTLINE_COLOR, 3);
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
            graphics.setColor(OUTLINE_COLOR);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(hull);
        }
    }
}
