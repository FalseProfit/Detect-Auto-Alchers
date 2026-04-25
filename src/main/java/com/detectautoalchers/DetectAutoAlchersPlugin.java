package com.detectautoalchers;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Detect Auto Alchers",
    description = "Highlights nearby suspected auto-alchers for manual investigation",
    tags = {"alchemy", "alch", "bot", "report", "highlight", "hiscore"}
)
public class DetectAutoAlchersPlugin extends Plugin
{
    private static final String ANIMATION_SOURCE = "animation";
    private static final String SPOT_ANIMATION_SOURCE = "spotanim";

    @Inject
    private Client client;

    @Inject
    private DetectAutoAlchersConfig config;

    @Inject
    private DetectAutoAlchersOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private HiscoreClient hiscoreClient;

    @Inject
    private DetectorService detectorService;

    private DetectAutoAlchersPanel panel;
    private NavigationButton navButton;

    @Provides
    DetectAutoAlchersConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DetectAutoAlchersConfig.class);
    }

    @Provides
    @Singleton
    DetectorService provideDetectorService()
    {
        return new DetectorService();
    }

    @Override
    protected void startUp()
    {
        detectorService.clear();
        overlayManager.add(overlay);

        panel = new DetectAutoAlchersPanel();
        navButton = NavigationButton.builder()
            .tooltip("Detect Auto Alchers")
            .icon(createIcon())
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        panel = null;
        detectorService.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            detectorService.clear();
            refreshPanel(System.currentTimeMillis());
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        Actor actor = event.getActor();
        if (!(actor instanceof Player) || actor.getName() == null)
        {
            return;
        }

        DetectorConfigSnapshot snapshot = DetectorConfigSnapshot.from(config);
        if (!snapshot.isAlchemyAnimation(actor.getAnimation()) || !isInRadius((Player) actor, snapshot.getRadius()))
        {
            return;
        }

        detectorService.recordAlchObservation(
            actor.getName(),
            ANIMATION_SOURCE,
            actor.getAnimation(),
            client.getTickCount(),
            System.currentTimeMillis(),
            snapshot
        );
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        long nowMillis = System.currentTimeMillis();
        DetectorConfigSnapshot snapshot = DetectorConfigSnapshot.from(config);
        WorldView worldView = client.getTopLevelWorldView();
        Player localPlayer = client.getLocalPlayer();
        if (worldView == null || localPlayer == null)
        {
            return;
        }

        WorldPoint localLocation = localPlayer.getWorldLocation();
        if (localLocation == null)
        {
            return;
        }

        for (Player player : worldView.players())
        {
            if (!shouldTrack(player, localPlayer, localLocation, snapshot.getRadius()))
            {
                continue;
            }

            int distance = player.getWorldLocation().distanceTo(localLocation);
            String normalizedName = detectorService.updatePlayer(
                player.getName(),
                client.getWorld(),
                distance,
                getWeaponId(player),
                nowMillis
            );

            scanSpotAnimations(player, snapshot, nowMillis);
            requestHiscoreIfNeeded(player.getName(), normalizedName, snapshot, nowMillis);
        }

        detectorService.pruneStale(nowMillis, snapshot.getObservationWindowMillis());
        detectorService.recompute(snapshot, nowMillis);
        refreshPanel(nowMillis);
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        DetectorConfigSnapshot snapshot = DetectorConfigSnapshot.from(config);
        if (!snapshot.isColorMenuEntries())
        {
            return;
        }

        MenuHighlighter.highlight(event.getMenuEntries(), detectorService.getSuspiciousNames());
    }

    private boolean shouldTrack(Player player, Player localPlayer, WorldPoint localLocation, int radius)
    {
        if (player == null || player == localPlayer || player.getName() == null || player.getWorldLocation() == null)
        {
            return false;
        }

        return player.getWorldLocation().distanceTo(localLocation) <= radius;
    }

    private boolean isInRadius(Player player, int radius)
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || player.getWorldLocation() == null || localPlayer.getWorldLocation() == null)
        {
            return false;
        }

        return player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= radius;
    }

    private int getWeaponId(Player player)
    {
        PlayerComposition composition = player.getPlayerComposition();
        return composition == null ? -1 : composition.getEquipmentId(KitType.WEAPON);
    }

    private void scanSpotAnimations(Player player, DetectorConfigSnapshot snapshot, long nowMillis)
    {
        for (int spotAnimationId : snapshot.getAlchemySpotAnimationIds())
        {
            if (player.hasSpotAnim(spotAnimationId))
            {
                detectorService.recordAlchObservation(
                    player.getName(),
                    SPOT_ANIMATION_SOURCE,
                    spotAnimationId,
                    client.getTickCount(),
                    nowMillis,
                    snapshot
                );
            }
        }
    }

    private void requestHiscoreIfNeeded(
        String displayName,
        String normalizedName,
        DetectorConfigSnapshot snapshot,
        long nowMillis)
    {
        if (normalizedName.isEmpty()
            || !detectorService.markHiscoreLookupIfNeeded(normalizedName, snapshot, nowMillis))
        {
            return;
        }

        hiscoreClient.lookupAsync(displayName, HiscoreEndpoint.NORMAL)
            .whenComplete((result, throwable) ->
            {
                if (throwable != null)
                {
                    detectorService.applyHiscore(normalizedName, HiscoreProfile.error());
                    return;
                }

                detectorService.applyHiscore(
                    normalizedName,
                    HiscoreAnalyzer.analyze(
                        result,
                        snapshot.getMagicLevelThreshold(),
                        snapshot.getNonMagicSkillThreshold(),
                        snapshot.getAllowedNonMagicSkillsAboveThreshold()
                    )
                );
            });
    }

    private void refreshPanel(long nowMillis)
    {
        if (panel == null)
        {
            return;
        }

        List<SuspicionResult> suspects = detectorService.getSuspiciousResults();
        panel.refresh(suspects, nowMillis);
    }

    private BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(240, 78, 78));
        graphics.fillOval(2, 2, 12, 12);
        graphics.setColor(new Color(255, 196, 64));
        graphics.setStroke(new BasicStroke(2));
        graphics.drawLine(8, 4, 8, 11);
        graphics.drawLine(5, 8, 11, 8);
        graphics.dispose();
        return image;
    }
}
