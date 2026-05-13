package com.detectautoalchers;

import com.google.inject.Provides;
import com.google.inject.name.Named;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Ignore;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.BeforeMenuRender;
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
import net.runelite.client.hiscore.HiscoreResult;
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
    private static final String CONFIG_GROUP = "detectautoalchers";
    private static final String ANIMATION_SOURCE = "animation";
    private static final String SPOT_ANIMATION_SOURCE = "spotanim";
    private static final String EXAMINE_OPTION = "Examine Alch Bot";
    private static final long PANEL_REFRESH_INTERVAL_MILLIS = 5_000L;
    private static final long HISCORE_LOOKUP_TIMEOUT_MILLIS = 10_000L;
    private static final long HISCORE_LOOKUP_RETRY_DELAY_MILLIS = 1_500L;
    private static final int HISCORE_LOOKUP_MAX_ATTEMPTS = 2;
    private static final long HISCORE_LOOKUP_STALE_MILLIS =
        (HISCORE_LOOKUP_TIMEOUT_MILLIS * HISCORE_LOOKUP_MAX_ATTEMPTS)
            + (HISCORE_LOOKUP_RETRY_DELAY_MILLIS * (HISCORE_LOOKUP_MAX_ATTEMPTS - 1))
            + 5_000L;

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
    private ConfigManager configManager;

    @Inject
    private DetectorService detectorService;

    @Inject
    private ReportedPlayerStore reportedPlayerStore;

    @Inject
    @Named("watchlist")
    private ReportedPlayerStore watchlistStore;

    @Inject
    @Named("overrideList")
    private ReportedPlayerStore overrideListStore;

    private DetectAutoAlchersPanel panel;
    private NavigationButton navButton;
    private ExecutorService ioExecutor;
    private DetectorConfigSnapshot configSnapshot;
    private final Set<String> mobilePlayerNames = new HashSet<>();
    private final PanelRefreshGate panelRefreshGate = new PanelRefreshGate(PANEL_REFRESH_INTERVAL_MILLIS);

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

    @Provides
    @Singleton
    @Named("watchlist")
    ReportedPlayerStore provideWatchlistStore()
    {
        return ReportedPlayerStore.createWatchlist();
    }

    @Provides
    @Singleton
    @Named("overrideList")
    ReportedPlayerStore provideOverrideListStore()
    {
        return ReportedPlayerStore.createOverrideList();
    }

    @Override
    protected void startUp()
    {
        ioExecutor = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "detect-auto-alchers-io");
            thread.setDaemon(true);
            return thread;
        });
        mobilePlayerNames.clear();
        detectorService.clear();
        configSnapshot = DetectorConfigSnapshot.from(config);

        panel = new DetectAutoAlchersPanel(new PanelActions());
        navButton = NavigationButton.builder()
            .tooltip("Detect Auto Alchers")
            .icon(createIcon())
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);
        loadPlayerLists();
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
        configSnapshot = null;
        panelRefreshGate.reset();
        mobilePlayerNames.clear();
        detectorService.clear();
        if (ioExecutor != null)
        {
            ioExecutor.shutdownNow();
            try
            {
                ioExecutor.awaitTermination(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
            ioExecutor = null;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        if ("ignoreMobilePlayers".equals(event.getKey()))
        {
            if (config.ignoreMobilePlayers())
            {
                detectorService.suppressNames(mobilePlayerNames, SuppressionReason.MOBILE);
            }
            else
            {
                clearMobileSuppressions();
            }
        }
        else if ("persistReportedPlayers".equals(event.getKey()))
        {
            if (config.persistReportedPlayers())
            {
                detectorService.suppressNames(reportedPlayerStore.getNormalizedNames(), SuppressionReason.REPORTED);
            }
            else
            {
                detectorService.unsuppressNames(reportedPlayerStore.getNormalizedNames(), SuppressionReason.REPORTED);
            }
        }

        long nowMillis = System.currentTimeMillis();
        configSnapshot = DetectorConfigSnapshot.from(config);
        detectorService.recompute(snapshot(), nowMillis);
        refreshPanel(nowMillis, true);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            clearMobileSuppressions();
            detectorService.clearEvidence();
            refreshPanel(System.currentTimeMillis(), true);
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
        DetectorConfigSnapshot snapshot = snapshot();
        if (isObservedMobilePlayer(actor.getName(), snapshot))
        {
            detectorService.suppressName(actor.getName(), SuppressionReason.MOBILE);
            return;
        }
        if (isIgnoredPlayer(actor.getName()))
        {
            detectorService.suppressName(actor.getName(), SuppressionReason.RUNELITE_IGNORE);
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
        DetectorConfigSnapshot snapshot = snapshot();
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
                detectorService.suppressName(player.getName(), SuppressionReason.RUNELITE_IGNORE);
                continue;
            }
            if (isObservedMobilePlayer(player.getName(), snapshot))
            {
                detectorService.suppressName(player.getName(), SuppressionReason.MOBILE);
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

        detectorService.syncSuppressionReason(getIgnoredNames(), SuppressionReason.RUNELITE_IGNORE);
        if (snapshot.isIgnoreMobilePlayers())
        {
            detectorService.suppressNames(mobilePlayerNames, SuppressionReason.MOBILE);
        }
        detectorService.pruneStale(nowMillis, snapshot.getObservationWindowMillis());
        detectorService.expireStaleHiscoreLookups(nowMillis, HISCORE_LOOKUP_STALE_MILLIS);
        detectorService.recompute(snapshot, nowMillis);
        refreshPanel(nowMillis);
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        MenuEntry[] menuEntries = event.getMenuEntries();
        DetectorConfigSnapshot snapshot = snapshot();
        if (config.rightClickExaminePlayers())
        {
            addExamineMenuEntries(menuEntries, snapshot);
        }
        if (snapshot.isIgnoreMobilePlayers() && observeMobilePlayers(menuEntries))
        {
            refreshPanel(System.currentTimeMillis(), true);
        }

        decorateMenuEntries(menuEntries, snapshot);
    }

    private void decorateMenuEntries(MenuEntry[] menuEntries, DetectorConfigSnapshot snapshot)
    {
        if (config.showMenuDetectionScores())
        {
            MenuHighlighter.appendScores(
                menuEntries,
                detectorService.getScoresByName(),
                snapshot.getSuspicionThreshold(),
                snapshot.getHighConfidenceThreshold()
            );
        }

        if (!snapshot.isColorMenuEntries())
        {
            return;
        }

        MenuHighlighter.highlight(
            menuEntries,
            detectorService.getSuspiciousConfidenceByName(),
            reportedPlayerStore.getNormalizedNames(),
            config.reportedPlayerHighlightColor()
        );
    }

    @Subscribe
    public void onBeforeMenuRender(BeforeMenuRender event)
    {
        if (!config.sortMenuEntriesByConfidence())
        {
            return;
        }

        MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
        MenuHighlighter.sortByConfidence(menuEntries, detectorService.getSuspiciousConfidenceByName());
        client.getMenu().setMenuEntries(menuEntries);
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
            String displayName = getReportedPlayerName(event);
            suppressMobilePlayer(displayName);
            String normalizedName = DetectorService.normalizeName(displayName);
            if (!normalizedName.isEmpty())
            {
                detectorService.suppressName(normalizedName, SuppressionReason.REPORTED);
                recordReportedPlayer(normalizedName, displayName);
            }
            refreshPanel(System.currentTimeMillis(), true);
            return;
        }

        String displayName = getReportedPlayerName(event);
        String normalizedName = DetectorService.normalizeName(displayName);
        if (!normalizedName.isEmpty())
        {
            recordReportedPlayer(normalizedName, displayName);
            detectorService.suppressName(normalizedName, SuppressionReason.REPORTED);
            refreshPanel(System.currentTimeMillis(), true);
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

    private DetectorConfigSnapshot snapshot()
    {
        if (configSnapshot == null)
        {
            configSnapshot = DetectorConfigSnapshot.from(config);
        }
        return configSnapshot;
    }

    private boolean suppressMobilePlayer(String displayName)
    {
        String normalizedName = DetectorService.normalizeName(displayName);
        if (normalizedName.isEmpty())
        {
            return false;
        }

        boolean added = mobilePlayerNames.add(normalizedName);
        detectorService.suppressName(normalizedName, SuppressionReason.MOBILE);
        return added;
    }

    private void clearMobileSuppressions()
    {
        detectorService.unsuppressNames(mobilePlayerNames, SuppressionReason.MOBILE);
        mobilePlayerNames.clear();
    }

    private boolean isObservedMobilePlayer(String displayName, DetectorConfigSnapshot snapshot)
    {
        return snapshot.isIgnoreMobilePlayers() && mobilePlayerNames.contains(DetectorService.normalizeName(displayName));
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

    private void loadPlayerLists()
    {
        runIo(() ->
        {
            try
            {
                reportedPlayerStore.load();
                watchlistStore.load();
                overrideListStore.load();
                if (config.persistReportedPlayers())
                {
                    detectorService.suppressNames(reportedPlayerStore.getNormalizedNames(), SuppressionReason.REPORTED);
                }
                detectorService.suppressNames(overrideListStore.getNormalizedNames(), SuppressionReason.OVERRIDE);
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to load Detect Auto Alchers player lists", ex);
            }
        });
    }

    private void recordReportedPlayer(String normalizedName, String displayName)
    {
        if (!config.persistReportedPlayers())
        {
            return;
        }

        runIo(() ->
        {
            try
            {
                reportedPlayerStore.record(normalizedName, displayName, Instant.now());
                detectorService.suppressName(normalizedName, SuppressionReason.REPORTED);
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to save reported player history to {}", reportedPlayerStore.getPath(), ex);
            }
        });
    }

    private void clearReportedHistory()
    {
        runIo(() ->
        {
            try
            {
                Set<String> reportedNames = reportedPlayerStore.getNormalizedNames();
                reportedPlayerStore.clear();
                detectorService.unsuppressNames(reportedNames, SuppressionReason.REPORTED);
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to clear reported player history at {}", reportedPlayerStore.getPath(), ex);
            }
        });
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

        refreshPanel(nowMillis, true);
        lookupHiscoreWithRetry(displayName)
            .whenComplete((result, throwable) ->
            {
                long completedAtMillis = System.currentTimeMillis();
                if (throwable != null)
                {
                    detectorService.applyHiscore(normalizedName, HiscoreProfile.error());
                    detectorService.recompute(snapshot, completedAtMillis);
                    refreshPanel(completedAtMillis, true);
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
                detectorService.recompute(snapshot, completedAtMillis);
                refreshPanel(completedAtMillis, true);
            });
    }

    CompletableFuture<HiscoreResult> lookupHiscoreAsync(String displayName)
    {
        return hiscoreClient.lookupAsync(displayName, HiscoreEndpoint.NORMAL);
    }

    private CompletableFuture<HiscoreResult> lookupHiscoreWithRetry(String displayName)
    {
        return lookupHiscoreAttempt(displayName, 1);
    }

    private CompletableFuture<HiscoreResult> lookupHiscoreAttempt(String displayName, int attempt)
    {
        CompletableFuture<HiscoreResult> attemptFuture;
        try
        {
            attemptFuture = lookupHiscoreAsync(displayName);
        }
        catch (RuntimeException ex)
        {
            attemptFuture = CompletableFuture.failedFuture(ex);
        }

        return attemptFuture
            .orTimeout(hiscoreLookupTimeoutMillis(), TimeUnit.MILLISECONDS)
            .handle((result, throwable) ->
            {
                if (throwable == null)
                {
                    return CompletableFuture.completedFuture(result);
                }

                if (attempt >= HISCORE_LOOKUP_MAX_ATTEMPTS || ioExecutor == null || ioExecutor.isShutdown())
                {
                    CompletableFuture<HiscoreResult> failed = new CompletableFuture<>();
                    failed.completeExceptionally(throwable);
                    return failed;
                }

                log.debug("Hiscore lookup attempt {} failed for {}", attempt, displayName, throwable);
                return CompletableFuture
                    .supplyAsync(
                        () -> null,
                        CompletableFuture.delayedExecutor(
                            hiscoreLookupRetryDelayMillis(),
                            TimeUnit.MILLISECONDS,
                            ioExecutor
                        )
                    )
                    .thenCompose(ignored -> lookupHiscoreAttempt(displayName, attempt + 1));
            })
            .thenCompose(future -> future);
    }

    long hiscoreLookupTimeoutMillis()
    {
        return HISCORE_LOOKUP_TIMEOUT_MILLIS;
    }

    long hiscoreLookupRetryDelayMillis()
    {
        return HISCORE_LOOKUP_RETRY_DELAY_MILLIS;
    }

    private void refreshPanel(long nowMillis)
    {
        refreshPanel(nowMillis, false);
    }

    private void refreshPanel(long nowMillis, boolean force)
    {
        if (panel == null)
        {
            return;
        }

        List<SuspicionResult> suspects = detectorService.getSuspiciousResults();
        SuspicionResult examinedResult = detectorService.getExaminedResult();
        if (!panelRefreshGate.shouldRefresh(suspects, examinedResult, nowMillis, force))
        {
            return;
        }

        panel.refresh(
            suspects,
            nowMillis,
            watchlistStore.getReportedPlayers(),
            overrideListStore.getReportedPlayers(),
            detectorService.getResultsByName(watchlistStore.getNormalizedNames()),
            examinedResult,
            config.compactPanelMode()
        );
    }

    private void addExamineMenuEntries(MenuEntry[] menuEntries, DetectorConfigSnapshot snapshot)
    {
        if (menuEntries == null)
        {
            return;
        }

        Set<String> addedNames = new HashSet<>();
        int insertedEntries = 0;
        for (int index = 0; index < menuEntries.length; index++)
        {
            MenuEntry entry = menuEntries[index];
            if (entry == null || !MenuHighlighter.isPlayerMenuEntry(entry))
            {
                continue;
            }
            if (!"report".equalsIgnoreCase(MenuHighlighter.cleanText(entry.getOption())))
            {
                continue;
            }

            String displayName = getPlayerName(entry);
            String normalizedName = DetectorService.normalizeName(displayName);
            if (normalizedName.isEmpty() || !addedNames.add(normalizedName))
            {
                continue;
            }

            Player player = entry.getPlayer();
            String target = entry.getTarget();
            MenuEntry examineEntry = client.createMenuEntry(index + insertedEntries)
                .setOption(EXAMINE_OPTION)
                .setTarget(target == null || target.isEmpty() ? displayName : target)
                .setType(MenuAction.RUNELITE_PLAYER)
                .onClick(clicked -> examinePlayer(displayName, player));
            decorateMenuEntries(new MenuEntry[]{examineEntry}, snapshot);
            insertedEntries++;
        }
    }

    private void examinePlayer(String displayName, Player menuPlayer)
    {
        long nowMillis = System.currentTimeMillis();
        DetectorConfigSnapshot snapshot = snapshot();
        Player player = menuPlayer == null ? findPlayer(displayName) : menuPlayer;
        String normalizedName = detectorService.examinePlayer(
            displayName,
            client.getWorld(),
            distanceToLocalPlayer(player),
            player == null ? -1 : getWeaponId(player),
            snapshot,
            nowMillis
        );
        refreshPanel(nowMillis, true);
        openPanel();
        requestExaminedHiscoreIfNeeded(displayName, normalizedName, snapshot, nowMillis);
    }

    private void openPanel()
    {
        if (navButton != null)
        {
            SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
        }
    }

    private void requestExaminedHiscoreIfNeeded(
        String displayName,
        String normalizedName,
        DetectorConfigSnapshot snapshot,
        long nowMillis)
    {
        if (normalizedName.isEmpty()
            || !detectorService.markExaminedHiscoreLookupIfNeeded(normalizedName, snapshot, nowMillis))
        {
            return;
        }

        refreshPanel(nowMillis, true);
        lookupHiscoreWithRetry(displayName)
            .whenComplete((result, throwable) ->
            {
                long completedAtMillis = System.currentTimeMillis();
                if (throwable != null)
                {
                    detectorService.applyExaminedHiscore(
                        normalizedName,
                        HiscoreProfile.error(),
                        snapshot,
                        completedAtMillis
                    );
                    refreshPanel(completedAtMillis, true);
                    return;
                }

                detectorService.applyExaminedHiscore(
                    normalizedName,
                    HiscoreAnalyzer.analyze(
                        result,
                        snapshot.getMagicLevelThreshold(),
                        snapshot.getNonMagicSkillThreshold(),
                        snapshot.getAllowedNonMagicSkillsAboveThreshold()
                    ),
                    snapshot,
                    completedAtMillis
                );
                refreshPanel(completedAtMillis, true);
            });
    }

    private Player findPlayer(String displayName)
    {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

        String normalizedName = DetectorService.normalizeName(displayName);
        for (Player player : worldView.players())
        {
            if (player != null && normalizedName.equals(DetectorService.normalizeName(player.getName())))
            {
                return player;
            }
        }
        return null;
    }

    private int distanceToLocalPlayer(Player player)
    {
        Player localPlayer = client.getLocalPlayer();
        if (player == null
            || localPlayer == null
            || player.getWorldLocation() == null
            || localPlayer.getWorldLocation() == null)
        {
            return -1;
        }

        return player.getWorldLocation().distanceTo(localPlayer.getWorldLocation());
    }

    private void recordListEntry(
        ReportedPlayerStore store,
        String normalizedName,
        String displayName,
        SuppressionReason suppressionReason)
    {
        runIo(() ->
        {
            try
            {
                store.record(normalizedName, displayName, Instant.now());
                if (suppressionReason != null)
                {
                    detectorService.suppressName(normalizedName, suppressionReason);
                }
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to save player list at {}", store.getPath(), ex);
            }
        });
    }

    private void removeListEntry(ReportedPlayerStore store, String normalizedName, SuppressionReason suppressionReason)
    {
        runIo(() ->
        {
            try
            {
                store.remove(normalizedName);
                if (suppressionReason != null)
                {
                    detectorService.unsuppressNames(Set.of(normalizedName), suppressionReason);
                }
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to update player list at {}", store.getPath(), ex);
            }
        });
    }

    private void importReportedHistory()
    {
        Path path = chooseFile("Import reported history", JFileChooser.OPEN_DIALOG);
        if (path == null)
        {
            return;
        }

        int choice = JOptionPane.showOptionDialog(
            panel,
            "Import reported history by merging with existing entries or replacing them?",
            "Import reported history",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new Object[]{"Merge", "Replace", "Cancel"},
            "Merge"
        );
        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION)
        {
            return;
        }

        ImportMode mode = choice == 1 ? ImportMode.REPLACE : ImportMode.MERGE;
        runIo(() ->
        {
            try
            {
                Set<String> oldNames = reportedPlayerStore.getNormalizedNames();
                reportedPlayerStore.importFrom(path, mode);
                if (mode == ImportMode.REPLACE)
                {
                    detectorService.unsuppressNames(oldNames, SuppressionReason.REPORTED);
                }
                if (config.persistReportedPlayers())
                {
                    detectorService.suppressNames(reportedPlayerStore.getNormalizedNames(), SuppressionReason.REPORTED);
                }
                refreshPanel(System.currentTimeMillis(), true);
            }
            catch (IOException ex)
            {
                log.warn("Unable to import reported player history from {}", path, ex);
            }
        });
    }

    private void exportReportedHistory()
    {
        Path path = chooseFile("Export reported history", JFileChooser.SAVE_DIALOG);
        if (path == null)
        {
            return;
        }

        runIo(() ->
        {
            try
            {
                reportedPlayerStore.exportTo(path);
            }
            catch (IOException ex)
            {
                log.warn("Unable to export reported player history to {}", path, ex);
            }
        });
    }

    private Path chooseFile(String title, int dialogType)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        int result = dialogType == JFileChooser.SAVE_DIALOG
            ? chooser.showSaveDialog(panel)
            : chooser.showOpenDialog(panel);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null)
        {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private void applyPreset(DetectionPreset preset)
    {
        configManager.setConfiguration(CONFIG_GROUP, "castThreshold", preset.getCastThreshold());
        configManager.setConfiguration(CONFIG_GROUP, "suspicionThreshold", preset.getSuspicionThreshold());
        configManager.setConfiguration(CONFIG_GROUP, "highConfidenceMargin", preset.getHighConfidenceMargin());
        configManager.setConfiguration(CONFIG_GROUP, "requireFireStaff", preset.isRequireFireStaff());
        configManager.setConfiguration(CONFIG_GROUP, "includeFireRuneStaves", preset.isIncludeFireRuneStaves());
        configManager.setConfiguration(CONFIG_GROUP, "magicLevelThreshold", preset.getMagicLevelThreshold());
        configManager.setConfiguration(CONFIG_GROUP, "nonMagicSkillThreshold", preset.getNonMagicSkillThreshold());
        configManager.setConfiguration(
            CONFIG_GROUP,
            "allowedNonMagicSkillsAboveThreshold",
            preset.getAllowedNonMagicSkillsAboveThreshold()
        );
        configManager.setConfiguration(
            CONFIG_GROUP,
            "nonMagicTotalLevelSuppressionThreshold",
            preset.getNonMagicTotalLevelSuppressionThreshold()
        );
        configManager.setConfiguration(CONFIG_GROUP, "matureAccountScorePenalty", preset.getMatureAccountScorePenalty());
        configManager.setConfiguration(
            CONFIG_GROUP,
            "clueCollectionActivityThreshold",
            preset.getClueCollectionActivityThreshold()
        );
        configManager.setConfiguration(
            CONFIG_GROUP,
            "clueCollectionActivityScorePenalty",
            preset.getClueCollectionActivityScorePenalty()
        );
        refreshPanel(System.currentTimeMillis(), true);
    }

    private void runIo(Runnable runnable)
    {
        ExecutorService executor = ioExecutor;
        if (executor == null || executor.isShutdown())
        {
            runnable.run();
            return;
        }
        executor.execute(runnable);
    }

    private final class PanelActions implements DetectAutoAlchersPanel.Actions
    {
        @Override
        public void clearReportedHistory()
        {
            DetectAutoAlchersPlugin.this.clearReportedHistory();
        }

        @Override
        public void importReportedHistory()
        {
            DetectAutoAlchersPlugin.this.importReportedHistory();
        }

        @Override
        public void exportReportedHistory()
        {
            DetectAutoAlchersPlugin.this.exportReportedHistory();
        }

        @Override
        public void watch(String normalizedName, String displayName)
        {
            recordListEntry(watchlistStore, normalizedName, displayName, null);
        }

        @Override
        public void override(String normalizedName, String displayName)
        {
            recordListEntry(overrideListStore, normalizedName, displayName, SuppressionReason.OVERRIDE);
        }

        @Override
        public void removeWatch(String normalizedName)
        {
            removeListEntry(watchlistStore, normalizedName, null);
        }

        @Override
        public void removeOverride(String normalizedName)
        {
            removeListEntry(overrideListStore, normalizedName, SuppressionReason.OVERRIDE);
        }

        @Override
        public void applyPreset(DetectionPreset preset)
        {
            DetectAutoAlchersPlugin.this.applyPreset(preset);
        }
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
