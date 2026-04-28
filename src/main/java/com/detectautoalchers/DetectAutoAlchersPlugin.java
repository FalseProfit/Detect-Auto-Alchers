package com.detectautoalchers;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Ignore;
import net.runelite.api.MenuEntry;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Detect Auto Alchers",
    description = "Highlights nearby suspected auto-alchers for manual investigation",
    tags = {"alchemy", "alch", "bot", "report", "highlight", "hiscore"}
)
public class DetectAutoAlchersPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(DetectAutoAlchersPlugin.class);
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

    @Inject
    private ReportedPlayerStore reportedPlayerStore;

    private DetectAutoAlchersPanel panel;
    private NavigationButton navButton;
    private final Set<String> mobilePlayerNames = new HashSet<>();

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

    @Provides
    @Singleton
    ReportedPlayerStore provideReportedPlayerStore()
    {
        return ReportedPlayerStore.createDefault();
    }

    @Override
    protected void startUp()
    {
        mobilePlayerNames.clear();
        detectorService.clear();
        loadReportedPlayers();
        overlayManager.add(overlay);

        panel = new DetectAutoAlchersPanel(this::clearReportedHistory);
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
        mobilePlayerNames.clear();
        detectorService.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"detectautoalchers".equals(event.getGroup()) || !"ignoreMobilePlayers".equals(event.getKey()))
        {
            return;
        }

        if (config.ignoreMobilePlayers())
        {
            detectorService.suppressNames(mobilePlayerNames);
        }
        else
        {
            detectorService.unsuppressNames(getMobileOnlyNames());
        }
        refreshPanel(System.currentTimeMillis());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            detectorService.clearEvidence();
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
        if (isObservedMobilePlayer(actor.getName(), snapshot))
        {
            detectorService.suppressName(actor.getName());
            return;
        }
        if (isIgnoredPlayer(actor.getName()))
        {
            detectorService.suppressName(actor.getName());
            return;
        }

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

            if (isIgnoredPlayer(player.getName()))
            {
                detectorService.suppressName(player.getName());
                continue;
            }
            if (isObservedMobilePlayer(player.getName(), snapshot))
            {
                detectorService.suppressName(player.getName());
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

        detectorService.suppressNames(getIgnoredNames());
        if (snapshot.isIgnoreMobilePlayers())
        {
            detectorService.suppressNames(mobilePlayerNames);
        }
        detectorService.pruneStale(nowMillis, snapshot.getObservationWindowMillis());
        detectorService.recompute(snapshot, nowMillis);
        refreshPanel(nowMillis);
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        DetectorConfigSnapshot snapshot = DetectorConfigSnapshot.from(config);
        if (snapshot.isIgnoreMobilePlayers() && observeMobilePlayers(event.getMenuEntries()))
        {
            refreshPanel(System.currentTimeMillis());
        }

        if (!snapshot.isColorMenuEntries())
        {
            return;
        }

        MenuHighlighter.highlight(event.getMenuEntries(), detectorService.getSuspiciousConfidenceByName());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!"report".equalsIgnoreCase(MenuHighlighter.cleanText(event.getMenuOption())))
        {
            return;
        }

        if (config.ignoreMobilePlayers() && MenuHighlighter.hasMobileClientIcon(event.getMenuTarget()))
        {
            suppressMobilePlayer(getReportedPlayerName(event));
            refreshPanel(System.currentTimeMillis());
            return;
        }

        String displayName = getReportedPlayerName(event);
        String normalizedName = DetectorService.normalizeName(displayName);
        if (!normalizedName.isEmpty())
        {
            recordReportedPlayer(normalizedName, displayName);
            detectorService.suppressName(normalizedName);
            refreshPanel(System.currentTimeMillis());
        }
    }

    private String getReportedPlayerName(MenuOptionClicked event)
    {
        MenuEntry menuEntry = event.getMenuEntry();
        if (menuEntry != null && menuEntry.getPlayer() != null && menuEntry.getPlayer().getName() != null)
        {
            return menuEntry.getPlayer().getName();
        }

        return MenuHighlighter.extractPlayerNameFromTarget(event.getMenuTarget());
    }

    private boolean shouldTrack(Player player, Player localPlayer, WorldPoint localLocation, int radius)
    {
        if (player == null || player == localPlayer || player.getName() == null || player.getWorldLocation() == null)
        {
            return false;
        }

        return player.getWorldLocation().distanceTo(localLocation) <= radius;
    }

    private boolean observeMobilePlayers(MenuEntry[] menuEntries)
    {
        if (menuEntries == null)
        {
            return false;
        }

        boolean changed = false;
        for (MenuEntry entry : menuEntries)
        {
            if (entry != null && MenuHighlighter.hasMobileClientIcon(entry.getTarget()))
            {
                changed |= suppressMobilePlayer(getPlayerName(entry));
            }
        }

        return changed;
    }

    private String getPlayerName(MenuEntry menuEntry)
    {
        if (menuEntry != null && menuEntry.getPlayer() != null && menuEntry.getPlayer().getName() != null)
        {
            return menuEntry.getPlayer().getName();
        }

        return menuEntry == null ? "" : MenuHighlighter.extractPlayerNameFromTarget(menuEntry.getTarget());
    }

    private boolean suppressMobilePlayer(String displayName)
    {
        String normalizedName = DetectorService.normalizeName(displayName);
        if (normalizedName.isEmpty())
        {
            return false;
        }

        boolean added = mobilePlayerNames.add(normalizedName);
        detectorService.suppressName(normalizedName);
        return added;
    }

    private boolean isObservedMobilePlayer(String displayName, DetectorConfigSnapshot snapshot)
    {
        return snapshot.isIgnoreMobilePlayers() && mobilePlayerNames.contains(DetectorService.normalizeName(displayName));
    }

    private Set<String> getMobileOnlyNames()
    {
        Set<String> mobileOnlyNames = new HashSet<>(mobilePlayerNames);
        mobileOnlyNames.removeAll(reportedPlayerStore.getNormalizedNames());
        mobileOnlyNames.removeAll(getIgnoredNames());
        return mobileOnlyNames;
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

    private boolean isIgnoredPlayer(String name)
    {
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        return ignoreContainer != null && ignoreContainer.findByName(name) != null;
    }

    private Set<String> getIgnoredNames()
    {
        Set<String> ignoredNames = new HashSet<>();
        NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
        if (ignoreContainer == null)
        {
            return ignoredNames;
        }

        for (Ignore ignore : ignoreContainer.getMembers())
        {
            if (ignore != null)
            {
                ignoredNames.add(DetectorService.normalizeName(ignore.getName()));
            }
        }

        return ignoredNames;
    }

    private void loadReportedPlayers()
    {
        if (!config.persistReportedPlayers())
        {
            return;
        }

        try
        {
            reportedPlayerStore.load();
            detectorService.suppressNames(reportedPlayerStore.getNormalizedNames());
        }
        catch (IOException ex)
        {
            log.warn("Unable to load reported player history from {}", reportedPlayerStore.getPath(), ex);
        }
    }

    private void recordReportedPlayer(String normalizedName, String displayName)
    {
        if (!config.persistReportedPlayers())
        {
            return;
        }

        try
        {
            reportedPlayerStore.record(normalizedName, displayName, Instant.now());
        }
        catch (IOException ex)
        {
            log.warn("Unable to save reported player history to {}", reportedPlayerStore.getPath(), ex);
        }
    }

    private void clearReportedHistory()
    {
        Set<String> reportedNames = reportedPlayerStore.getNormalizedNames();
        try
        {
            reportedPlayerStore.clear();
            detectorService.unsuppressNames(reportedNames);
            if (config.ignoreMobilePlayers())
            {
                detectorService.suppressNames(mobilePlayerNames);
            }
            refreshPanel(System.currentTimeMillis());
        }
        catch (IOException ex)
        {
            log.warn("Unable to clear reported player history at {}", reportedPlayerStore.getPath(), ex);
        }
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
