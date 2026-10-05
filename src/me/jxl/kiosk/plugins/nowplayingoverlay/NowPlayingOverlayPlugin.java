// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.nowplayingoverlay;

import android.app.Application;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Bundle;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import io.nayuki.qrcodegen.QrCode;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Native Android overlays shown while Fotoo is active. DreamService mode\n * is detected exactly; ordinary-app mode uses the Kiosk Activity leaving\n * the foreground because this AOSP build does not report Fotoo through\n * UsageStats.
 *
 * The public KS SDK does not currently expose an Android Context. This plugin
 * therefore obtains the application Context with a small reflection fallback.
 * Everything that depends on that workaround is isolated in applicationContext().
 */
public final class NowPlayingOverlayPlugin implements KioskPlugin {
    private PluginHost host;
    private Map<String, Object> settings;
    private Context context;
    private WindowManager windowManager;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService io;
    private static final String FOTOO_PACKAGE = "com.bo.fotoo";
    private BroadcastReceiver dreamReceiver;
    private Application application;
    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private Activity currentActivity;
    private boolean dreaming;
    private boolean manualFotoo;
    private boolean forceOverlayPreview;

    private final Set<String> subscribedEntities = new HashSet<>();
    private String nowPlayingEntity = "";
    private String playlistEntity = "";
    private String nextTrackEntity = "";
    private String doorbellEntity = "";
    private String doorbellEntity2 = "";
    private String doorbellCameraEntity = "";
    private boolean showPaused = true;
    private String nowPlayingPosition = "Bottom";
    private int nowPlayingOffset = 34;
    private int nowPlayingWidthPercent = 90;
    private int nowPlayingOpacity = 90;
    private boolean showProgress = true;
    private String timeLabels = "Elapsed / remaining";
    private boolean showPlaylist = false;
    private boolean showNextTrack = false;
    private int doorbellSeconds = 20;
    private int cameraOpacity = 100;
    private int cameraSizePercent = 90;
    private String cameraPosition = "Center";
    private boolean cameraTestMode = false;
    private boolean showOnKioskScreensaver = true;
    private boolean showOnFotoo = true;
    private String visibilityEntity = "";
    private String visibilityCondition = "Always";
    private String visibilityValue = "";
    private String visibilityState = "";
    private boolean visibilityPollPending;
    private boolean kioskScreensaverActive = false;
    private String kioskScreensaverView = "";

    private String haBaseUrl;
    private String mediaState;
    private Map<?, ?> mediaAttributes = Collections.emptyMap();
    private String mediaIdentity = "";
    private String preferredHaArtwork = "";
    private double mediaPositionAnchor = 0;
    private double lastMediaPositionAttr = Double.NaN;
    private long mediaPositionAnchorRealtime = SystemClock.elapsedRealtime();
    private boolean mediaPollPending;
    private boolean cameraStatePollPending;
    private boolean trigger1PollPending;
    private boolean trigger2PollPending;
    private boolean playlistPollPending;
    private boolean nextTrackPollPending;
    private String maBaseUrl = "";
    private String maToken = "";
    private String maPlayerId = "";
    private boolean maQueuePollPending;
    private long maLastSuccessRealtime;
    private String playlistState = "";
    private String nextTrackState = "";
    private Map<?, ?> cameraAttributes = Collections.emptyMap();
    private String lastDoorbellState;
    private boolean doorbellInitialSeen;
    private String lastDoorbellState2;
    private boolean doorbellInitialSeen2;

    private View nowPlayingView;
    private ImageView mediaImage;
    private TextView mediaTitle;
    private TextView mediaArtist;
    private TextView mediaAlbum;
    private TextView mediaPlaylist;
    private TextView mediaNext;
    private ProgressBar mediaProgress;
    private TextView mediaTime;
    private String loadedMediaPicture = "";
    private String renderedMediaPicture = "";
    private String renderedMediaPictureKey = "";
    private long mediaImageRequestSerial;
    private boolean mediaFetchPending;
    private final LruCache<String, Bitmap> mediaImageCache = new LruCache<>(8);
    private static final String PARTY_PREFS = "now_playing_presentation";
    private static final String PARTY_EVENT = "me.jxl.kiosk.plugins.PARTY_PRESENTATION_CHANGED";
    private BroadcastReceiver partyAudioReceiver;
    private String partyEffect = "off";
    private boolean partyGuestsFollow = true;
    private boolean partyQueueVisible = true;
    private boolean partyGuestPending;
    private boolean partyGuestChangePending;
    private long partyGuestLastPoll;
    private String partyGuestUrl = "";
    private String partyGuestStatus = "";
    private String partyGuestText = "Scan og tilføj musik til køen";
    private Bitmap partyQr;
    private long partyGuestLastSuccess;
    private volatile long partyGuestGeneration;
    private boolean partyCompact;
    private boolean partyFullscreen;
    private FrameLayout partyRoot;
    private PartyView partyView;
    private Activity partyActivity;
    private boolean partyPollPending;
    private long partyLastPoll;
    private long partyLastSuccess;
    private long partyGeneration;
    private long partyLastPostpone;
    private long partyLastRenew;
    private String partyTarget = "";
    private PartyQueueModel partyModel;
    private final Map<String, Bitmap> partyArtwork = new HashMap<>();
    private final Set<String> partyArtworkPending = new HashSet<>();

    private View doorbellView;
    private ImageView doorbellImage;
    private TextView doorbellLabel;
    private boolean cameraFetchPending;
    private boolean doorbellHeldByTest;

    private final Runnable liveStatePollTask = new Runnable() {
        @Override public void run() {
            if ((!overlayActive() && !partyFullscreen) || host == null) return;
            // The explicitly selected Home Assistant media_player is the
            // authoritative playback state. Direct Music Assistant data may
            // enrich title/artwork/queue metadata, but must never replace the
            // selected speaker's playing/paused state with the kiosk's own MA
            // player state.
            pollMediaEntity();
            if (partyCompact || partyFullscreen) {
                updateParty();
                pollPartyQueue();
            } else if (directMusicAssistantAvailable()) pollMusicAssistantQueue();
            if (partyFullscreen) {
                pollPartyGuests();
                if (SystemClock.elapsedRealtime() - partyLastRenew > 10000) {
                    partyLastRenew = SystemClock.elapsedRealtime();
                    context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit()
                            .putLong("party_until_ms", System.currentTimeMillis() + 30000).apply();
                }
                if (activeKioskActivity() != null && SystemClock.elapsedRealtime() - partyLastPostpone > 15000) {
                    partyLastPostpone = SystemClock.elapsedRealtime();
                    partyHostCommand("postponeScreensaver");
                }
                main.postDelayed(this, 1000);
                return;
            }
            if (doorbellView != null || cameraTestMode) pollCameraEntity();
            pollDoorbellTrigger(1);
            pollDoorbellTrigger(2);
            if (showPlaylist && !playlistEntity.isEmpty()) pollSimpleTextEntity(playlistEntity, true);
            if (showNextTrack && !nextTrackEntity.isEmpty()) pollSimpleTextEntity(nextTrackEntity, false);
            main.postDelayed(this, 1000);
        }
    };

    private final Runnable forcePreviewTimeoutTask = new Runnable() {
        @Override public void run() {
            if (!forceOverlayPreview) return;
            forceOverlayPreview = false;
            updatePresentation();
        }
    };

    private final Runnable clearMediaImageTask = new Runnable() {
        @Override public void run() {
            if (mediaImage == null) return;
            String current = attr(mediaAttributes, "entity_picture", "");
            if (!current.isEmpty()) return;
            mediaImage.setImageDrawable(null);
            renderedMediaPicture = "";
            renderedMediaPictureKey = "";
        }
    };

    private final Runnable progressTickTask = new Runnable() {
        @Override public void run() {
            if (nowPlayingView == null) return;
            updateProgress();
            main.postDelayed(this, 1000);
        }
    };

    private final Runnable hideDoorbellTask = new Runnable() {
        @Override public void run() {
            hideDoorbell();
            updateNowPlaying();
        }
    };

    private final Runnable cameraRefreshTask = new Runnable() {
        @Override public void run() {
            if (doorbellView == null || !overlayActive()) return;
            refreshDoorbellImage();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        this.settings = settings;
        this.context = applicationContext(host);
        if (context == null) {
            host.status("Could not obtain Android application context.", true);
            return;
        }
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        SharedPreferences presentation = context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE);
        partyCompact = presentation.getBoolean("compact", false);
        partyEffect = PartySignal.effect(presentation.getString("effect", "off"));
        partyGuestsFollow = presentation.getBoolean("guests_follow", true);
        partyQueueVisible = presentation.getBoolean("queue_visible", true);
        presentation.edit().putBoolean("party_fullscreen", false).apply();
        context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
            host.status("Grant Display over other apps to Kiosk Satellite.", true);
            return;
        }

        io = Executors.newFixedThreadPool(3, task -> {
            Thread thread = new Thread(task, "now-playing-overlay-io");
            thread.setDaemon(true);
            return thread;
        });

        registerDreamReceiver();
        registerPartyAudioReceiver();
        registerActivityLifecycle();
        currentActivity = findResumedActivity();
        main.post(() -> {
            cleanupStaleInAppViews();
            cleanupStaleOverlayWindows();
        });
        host.subscribe("screensaver.state");
        host.subscribe("screensaver.view");
        applySettings(settings);
        readKioskMusicAssistantConfig();
        readHomeAssistantBaseUrl();
        readInitialScreensaverState();
        host.status("Ready. Overlays follow Kiosk Satellite screensavers and Fotoo.", false);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        this.settings = settings;
        if (host != null) applySettings(settings);
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("openFotoo".equals(command)) {
            openFotoo();
        } else if ("attachNow".equals(command)) {
            forceOverlayPreview = true;
            main.removeCallbacks(forcePreviewTimeoutTask);
            main.postDelayed(forcePreviewTimeoutTask, 120000);
            main.post(this::updatePresentation);
        } else if ("partyCompact".equals(command)) {
            main.post(() -> {
                closePartyFullscreen();
                partyCompact = true;
                context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putBoolean("compact", true).apply();
                forceOverlayPreview = true;
                main.removeCallbacks(forcePreviewTimeoutTask);
                main.postDelayed(forcePreviewTimeoutTask, 120000);
                hideNowPlaying();
                updatePresentation();
            });
        } else if (command.startsWith("partyEffect")) {
            final String effect = command.substring("partyEffect".length()).toLowerCase(java.util.Locale.ROOT);
            if (!effect.equals(PartySignal.effect(effect))) throw new IllegalArgumentException("Unknown effect");
            main.post(() -> setPartyEffect(effect));
        } else if ("partyGuestsFollow".equals(command) || "partyGuestsHide".equals(command)) {
            main.post(() -> setPartyGuests("partyGuestsFollow".equals(command)));
        } else if ("partyGuestEnable".equals(command) || "partyGuestDisable".equals(command)) {
            main.post(() -> changePartyGuestAccess("partyGuestEnable".equals(command)));
        } else if ("partyQueueShow".equals(command) || "partyQueueHide".equals(command)) {
            main.post(() -> setPartyQueue("partyQueueShow".equals(command)));
        } else if ("partyFullscreen".equals(command)) {
            main.post(this::openPartyFullscreen);
        } else if ("partyOff".equals(command)) {
            main.post(() -> {
                closePartyFullscreen();
                partyCompact = false;
                context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putBoolean("compact", false).apply();
                removePartyView();
                updatePresentation();
            });
        } else if ("test".equals(command)) {
            main.post(this::showTestOverlay);
        } else if ("showCameraTest".equals(command)) {
            cameraTestMode = true;
            main.post(() -> {
                closePartyFullscreen();
                removePartyView();
                if (overlayActive()) showDoorbell(true);
                updatePresentation();
            });
        } else if ("hideCameraTest".equals(command)) {
            cameraTestMode = false;
            doorbellHeldByTest = false;
            main.post(() -> {
                hideDoorbell();
                updatePresentation();
            });
        } else if ("hide".equals(command)) {
            main.post(() -> {
                closePartyFullscreen();
                removePartyView();
                forceOverlayPreview = false;
                manualFotoo = false;
                main.removeCallbacks(forcePreviewTimeoutTask);
                main.removeCallbacks(liveStatePollTask);
                hideDoorbell();
                hideNowPlaying();
            });
        } else {
            throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("ks.screensaver.state".equals(event)) {
            kioskScreensaverActive = Boolean.TRUE.equals(payload.get("active"));
            if (!kioskScreensaverActive) kioskScreensaverView = "";
            main.post(this::updatePresentation);
            return;
        }
        if ("ks.screensaver.view".equals(event)) {
            Object value = payload.get("view");
            kioskScreensaverView = value == null ? "" : String.valueOf(value);
            main.post(this::updatePresentation);
            return;
        }
        if (!event.startsWith("ks.ha.entity.")) return;
        Object idValue = payload.get("entityId");
        String entityId = idValue == null ? event.substring("ks.ha.entity.".length()) : String.valueOf(idValue);
        String state = payload.get("state") == null ? null : String.valueOf(payload.get("state"));

        if (!visibilityEntity.isEmpty() && entityId.equals(visibilityEntity)) {
            visibilityState = state == null ? "" : state;
            main.post(this::updatePresentation);
        }
        Object attrsValue = payload.get("attributes");
        Map<?, ?> attrs = attrsValue instanceof Map ? (Map<?, ?>) attrsValue : Collections.emptyMap();

        if (entityId.equals(nowPlayingEntity)) {
            preferredHaArtwork = attr(attrs, "entity_picture", "");
            applyMediaSnapshot(state, attrs);
        }

        if (!playlistEntity.isEmpty() && entityId.equals(playlistEntity)) {
            playlistState = state == null ? "" : state;
            main.post(this::updateNowPlaying);
        }

        if (!nextTrackEntity.isEmpty() && entityId.equals(nextTrackEntity)) {
            nextTrackState = state == null ? "" : state;
            main.post(this::updateNowPlaying);
        }

        if (entityId.equals(doorbellCameraEntity)) {
            cameraAttributes = attrs;
            if (doorbellView != null) main.post(this::refreshDoorbellImage);
        }

        if (entityId.equals(doorbellEntity)) {
            handleDoorbellState(1, state, false);
        }

        if (entityId.equals(doorbellEntity2)) {
            handleDoorbellState(2, state, false);
        }
    }

    @Override
    public synchronized void stop() {
        if (context != null && partyAudioReceiver != null) {
            try { context.unregisterReceiver(partyAudioReceiver); } catch (Throwable ignored) {}
        }
        partyAudioReceiver = null;
        if (context != null && dreamReceiver != null) {
            try { context.unregisterReceiver(dreamReceiver); } catch (Throwable ignored) {}
        }
        dreamReceiver = null;
        if (application != null && lifecycleCallbacks != null) {
            try { application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks); } catch (Throwable ignored) {}
        }
        lifecycleCallbacks = null;
        application = null;

        // PluginBridge calls stop() on the plugin worker thread. WindowManager
        // views were previously removed from that worker thread, Android
        // rejected the removal, and the exception was swallowed. That left
        // orphan TYPE_APPLICATION_OVERLAY windows visible even after disabling
        // plugins or updating. Cleanup is now performed synchronously on main.
        cleanupUiSynchronously();
        currentActivity = null;

        if (io != null) {
            io.shutdownNow();
            io = null;
        }
        subscribedEntities.clear();
        kioskScreensaverActive = false;
        kioskScreensaverView = "";
        mediaPollPending = false;
        cameraStatePollPending = false;
        trigger1PollPending = false;
        trigger2PollPending = false;
        playlistPollPending = false;
        nextTrackPollPending = false;
        maQueuePollPending = false;
        host = null;
        settings = null;
        context = null;
        windowManager = null;
    }

    private void cleanupUiSynchronously() {
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable cleanup = () -> {
            try {
                main.removeCallbacksAndMessages(null);
                forceOverlayPreview = false;
                manualFotoo = false;
                dreaming = false;
                closePartyFullscreen();
                removePartyView();
                hideDoorbellImmediate();
                hideNowPlayingImmediate();
                cleanupStaleOverlayWindows();
            } finally {
                latch.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
            return;
        }
        main.post(cleanup);
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerDreamReceiver() {
        dreamReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                    // An exact DreamService start supersedes any temporary
                    // attach/inference state left from an update or preview.
                    dreaming = true;
                    forceOverlayPreview = false;
                    main.removeCallbacks(forcePreviewTimeoutTask);
                    updatePresentation();
                } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                    // This is the authoritative signal that Fotoo's Android
                    // screensaver has gone away. Clear every fallback mode
                    // that could otherwise keep TYPE_APPLICATION_OVERLAY
                    // windows alive over the Kiosk Satellite dashboard.
                    dreaming = false;
                    forceOverlayPreview = false;
                    manualFotoo = false;
                    main.removeCallbacks(forcePreviewTimeoutTask);
                    updatePresentation();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(dreamReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(dreamReceiver, filter);
        }
    }

    private void registerActivityLifecycle() {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        application = (Application) appContext;
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                if (!activity.getPackageName().equals(context.getPackageName())) return;
                currentActivity = activity;
                boolean changed = manualFotoo || forceOverlayPreview;
                manualFotoo = false;
                forceOverlayPreview = false;
                main.removeCallbacks(forcePreviewTimeoutTask);
                if (changed || partyFullscreen) updatePresentation();
            }
            @Override public void onActivityPaused(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
                if (partyFullscreen && partyActivity == activity && !activity.isChangingConfigurations()) {
                    main.post(() -> { closePartyFullscreen(); updatePresentation(); });
                }
            }
            @Override public void onActivityStopped(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
        };
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    private void openFotoo() {
        if (context == null) return;
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(FOTOO_PACKAGE);
            if (launch == null) {
                host.status("Fotoo is not installed or has no launchable activity.", true);
                return;
            }
            manualFotoo = true;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            main.postDelayed(this::updatePresentation, 500);
        } catch (Throwable error) {
            manualFotoo = false;
            host.status("Could not open Fotoo: " + safeMessage(error), true);
        }
    }

    private void applySettings(Map<String, Object> values) {
        String nextNow = stringSetting(values, "nowPlayingEntity");
        String nextPlaylistEntity = stringSetting(values, "playlistEntity");
        String nextNextTrackEntity = stringSetting(values, "nextTrackEntity");
        String nextDoorbell = stringSetting(values, "doorbellEntity");
        String nextDoorbell2 = stringSetting(values, "doorbellEntity2");
        String nextCamera = stringSetting(values, "doorbellCameraEntity");
        String nextVisibility = stringSetting(values, "visibilityEntity");
        String nextVisibilityCondition = stringSetting(values, "visibilityCondition");
        String nextVisibilityValue = stringSetting(values, "visibilityValue");

        Set<String> wanted = new HashSet<>();
        if (!nextNow.isEmpty()) wanted.add(nextNow);
        if (!nextPlaylistEntity.isEmpty()) wanted.add(nextPlaylistEntity);
        if (!nextNextTrackEntity.isEmpty()) wanted.add(nextNextTrackEntity);
        if (!nextDoorbell.isEmpty()) wanted.add(nextDoorbell);
        if (!nextDoorbell2.isEmpty()) wanted.add(nextDoorbell2);
        if (!nextCamera.isEmpty()) wanted.add(nextCamera);
        if (!nextVisibility.isEmpty() &&
                !"Always".equals(nextVisibilityCondition) &&
                !"Time between".equals(nextVisibilityCondition)) {
            wanted.add(nextVisibility);
        }

        for (String old : new HashSet<>(subscribedEntities)) {
            if (!wanted.contains(old)) {
                try { host.unsubscribe("ha.entity." + old); } catch (Throwable ignored) {}
                subscribedEntities.remove(old);
            }
        }
        for (String entity : wanted) {
            if (subscribedEntities.add(entity)) host.subscribe("ha.entity." + entity);
        }

        nowPlayingEntity = nextNow;
        playlistEntity = nextPlaylistEntity;
        nextTrackEntity = nextNextTrackEntity;
        doorbellEntity = nextDoorbell;
        doorbellEntity2 = nextDoorbell2;
        doorbellCameraEntity = nextCamera;
        visibilityEntity = nextVisibility;
        visibilityCondition = nextVisibilityCondition.isEmpty()
                ? "Always" : nextVisibilityCondition;
        visibilityValue = nextVisibilityValue;
        visibilityState = "";
        if (!visibilityEntity.isEmpty() &&
                !"Always".equals(visibilityCondition) &&
                !"Time between".equals(visibilityCondition)) {
            pollVisibilityEntity();
        }
        showPaused = Boolean.TRUE.equals(values.get("showPaused"));
        String npPosition = stringSetting(values, "nowPlayingPosition");
        nowPlayingPosition = "Top".equals(npPosition) || "Center".equals(npPosition)
                ? npPosition : "Bottom";
        Object offset = values.get("nowPlayingOffset");
        nowPlayingOffset = offset instanceof Number ? Math.max(0, Math.min(500, ((Number) offset).intValue())) : 34;
        Object npWidth = values.get("nowPlayingWidthPercent");
        nowPlayingWidthPercent = npWidth instanceof Number
                ? Math.max(30, Math.min(100, ((Number) npWidth).intValue()))
                : 90;
        Object npOpacity = values.get("nowPlayingOpacity");
        nowPlayingOpacity = npOpacity instanceof Number ? Math.max(10, Math.min(100, ((Number) npOpacity).intValue())) : 90;
        showProgress = values.get("showProgress") == null || Boolean.TRUE.equals(values.get("showProgress"));
        String labels = stringSetting(values, "timeLabels");
        timeLabels = labels.isEmpty() ? "Elapsed / remaining" : labels;
        showPlaylist = Boolean.TRUE.equals(values.get("showPlaylist"));
        showNextTrack = Boolean.TRUE.equals(values.get("showNextTrack"));
        Object seconds = values.get("doorbellSeconds");
        doorbellSeconds = seconds instanceof Number ? Math.max(5, Math.min(60, ((Number) seconds).intValue())) : 20;
        Object camOpacity = values.get("cameraOpacity");
        cameraOpacity = camOpacity instanceof Number ? Math.max(10, Math.min(100, ((Number) camOpacity).intValue())) : 100;
        Object camSize = values.get("cameraSizePercent");
        cameraSizePercent = camSize instanceof Number ? Math.max(30, Math.min(100, ((Number) camSize).intValue())) : 90;
        String camPosition = stringSetting(values, "cameraPosition");
        cameraPosition = "Top".equals(camPosition) || "Bottom".equals(camPosition) ? camPosition : "Center";
        String overlayTarget = stringSetting(values, "overlayTarget");
        if ("Kiosk Satellite only".equals(overlayTarget)) {
            showOnKioskScreensaver = true;
            showOnFotoo = false;
        } else if ("Fotoo only".equals(overlayTarget)) {
            showOnKioskScreensaver = false;
            showOnFotoo = true;
        } else {
            showOnKioskScreensaver = true;
            showOnFotoo = true;
        }

        doorbellInitialSeen = false;
        lastDoorbellState = null;
        doorbellInitialSeen2 = false;
        lastDoorbellState2 = null;
        loadedMediaPicture = "";
        renderedMediaPicture = "";
        renderedMediaPictureKey = "";
        preferredHaArtwork = "";
        mediaIdentity = "";
        mediaImageRequestSerial++;
        main.removeCallbacks(clearMediaImageTask);
        lastMediaPositionAttr = Double.NaN;
        mediaPositionAnchor = 0;
        mediaPositionAnchorRealtime = SystemClock.elapsedRealtime();
        main.post(() -> {
            // Recreate the camera window on every saved configuration so
            // opacity, size, position and test-mode changes are guaranteed
            // to apply to the actual WindowManager window.
            if (doorbellView != null) hideDoorbell();
            // Width/position are WindowManager layout parameters, so rebuild
            // the Now Playing window whenever settings are saved.
            if (nowPlayingView != null) hideNowPlaying();
            partyGeneration++;
            clearPartyGuests();
            partyModel = null;
            partyTarget = "";
            partyLastSuccess = 0;
            removePartyView();
            updatePresentation();
        });
    }

    private void readHomeAssistantBaseUrl() {
        host.executeCommand("getDashboardState", Collections.emptyMap(), (ok, data, error) -> {
            if (!ok || !(data instanceof Map)) return;
            Object value = ((Map<?, ?>) data).get("homeAssistantUrl");
            if (value != null) haBaseUrl = String.valueOf(value);
            main.post(this::updateNowPlaying);
        });
    }

    private boolean fotooActive() {
        return dreaming || manualFotoo || forceOverlayPreview;
    }

    private boolean kioskScreensaverActiveForOverlay() {
        if (!showOnKioskScreensaver || !kioskScreensaverActive) return false;
        return !"black".equals(kioskScreensaverView)
                && !"blank".equals(kioskScreensaverView);
    }

    private boolean overlayActive() {
        boolean surface = (showOnFotoo && fotooActive()) || kioskScreensaverActiveForOverlay();
        return surface && (forceOverlayPreview || visibilityAllowed());
    }

    private boolean visibilityAllowed() {
        String condition = visibilityCondition == null ? "Always" : visibilityCondition;
        if (condition.isEmpty() || "Always".equals(condition)) return true;
        String value = visibilityValue == null ? "" : visibilityValue.trim();
        if ("Time between".equals(condition)) return timeBetween(value);
        if (visibilityEntity == null || visibilityEntity.isEmpty()) return true;

        String state = visibilityState == null ? "" : visibilityState.trim();
        if ("Active".equals(condition)) return activeState(state);
        if ("Inactive".equals(condition)) return !activeState(state);
        if ("State equals".equals(condition)) return state.equalsIgnoreCase(value);
        if ("State not equals".equals(condition)) return !state.equalsIgnoreCase(value);

        Double number = parseVisibilityNumber(state);
        if (number == null) return false;
        if ("Numeric above".equals(condition)) {
            Double threshold = parseVisibilityNumber(value);
            return threshold != null && number > threshold;
        }
        if ("Numeric below".equals(condition)) {
            Double threshold = parseVisibilityNumber(value);
            return threshold != null && number < threshold;
        }
        if ("Numeric between".equals(condition)) {
            double[] bounds = parseVisibilityRange(value);
            return bounds != null && number >= Math.min(bounds[0], bounds[1]) &&
                    number <= Math.max(bounds[0], bounds[1]);
        }
        return true;
    }

    private static boolean activeState(String state) {
        String s = state == null ? "" : state.trim().toLowerCase(java.util.Locale.ROOT);
        return "on".equals(s) || "true".equals(s) || "home".equals(s) ||
                "playing".equals(s) || "open".equals(s) || "detected".equals(s) ||
                "occupied".equals(s) || "present".equals(s);
    }

    private static Double parseVisibilityNumber(String value) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double[] parseVisibilityRange(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split("\\.\\.");
        if (parts.length != 2) return null;
        Double a = parseVisibilityNumber(parts[0]);
        Double b = parseVisibilityNumber(parts[1]);
        return a == null || b == null ? null : new double[] {a, b};
    }

    private static boolean timeBetween(String value) {
        if (value == null) return true;
        String[] parts = value.trim().split("\\s*-\\s*");
        if (parts.length != 2) return true;
        try {
            LocalTime from = LocalTime.parse(parts[0].trim());
            LocalTime until = LocalTime.parse(parts[1].trim());
            LocalTime now = LocalTime.now();
            if (from.equals(until)) return true;
            if (from.isBefore(until)) return !now.isBefore(from) && now.isBefore(until);
            return !now.isBefore(from) || now.isBefore(until);
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private void pollVisibilityEntity() {
        if (host == null || visibilityEntity.isEmpty() || visibilityPollPending) return;
        visibilityPollPending = true;
        String entity = visibilityEntity;
        Map<String, Object> args = new HashMap<>();
        args.put("entityId", entity);
        host.executeCommand("getHaEntityState", args, (ok, data, error) -> {
            visibilityPollPending = false;
            if (!ok || !(data instanceof Map) || !entity.equals(visibilityEntity)) return;
            Object state = ((Map<?, ?>) data).get("state");
            visibilityState = state == null ? "" : String.valueOf(state);
            main.post(this::updatePresentation);
        });
    }

    /**
     * KS's own Home Assistant Media screensaver can use Android hybrid
     * composition. On some Raspberry Pi Android builds a TYPE_APPLICATION_OVERLAY
     * surface is then composited below the WebView surface. When KS itself owns
     * the foreground Activity, attach our view to its decor hierarchy instead.
     * Fotoo remains an external app/DreamService and keeps using the system
     * overlay path that has always worked there.
     */
    private boolean preferInAppOverlay() {
        if (partyFullscreen) return true;
        if (kioskScreensaverActiveForOverlay()) return true;
        return forceOverlayPreview && activeKioskActivity() != null;
    }

    private Activity activeKioskActivity() {
        Activity activity = currentActivity;
        if (activity != null && !activity.isFinishing() &&
                (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed())) {
            return activity;
        }
        activity = findResumedActivity();
        if (activity != null) currentActivity = activity;
        return activity;
    }

    private boolean addOverlayView(
            View view,
            int width,
            int height,
            int gravity,
            int yOffset,
            boolean camera) {
        if (view == null) return false;

        if (preferInAppOverlay()) {
            Activity activity = activeKioskActivity();
            if (activity != null) {
                View content = activity.findViewById(android.R.id.content);
                if (content instanceof FrameLayout) {
                    FrameLayout root = (FrameLayout) content;
                    FrameLayout.LayoutParams params =
                            new FrameLayout.LayoutParams(width, height, gravity);
                    if ((gravity & Gravity.TOP) == Gravity.TOP) {
                        params.topMargin = yOffset;
                    } else if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                        params.bottomMargin = yOffset;
                    }
                    root.addView(view, params);
                    if (Build.VERSION.SDK_INT >= 21) view.setZ(100000f);
                    view.bringToFront();
                    root.invalidate();
                    return true;
                }
            }
        }

        if (windowManager == null) return false;
        WindowManager.LayoutParams params = camera
                ? cameraOverlayParams(width, height)
                : overlayParams(width, height);
        params.gravity = gravity;
        params.y = yOffset;
        windowManager.addView(view, params);
        return true;
    }

    private void removeOverlayView(View view) {
        if (view == null) return;
        try {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
                return;
            }
        } catch (Throwable ignored) {}
        if (windowManager != null) {
            try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) {}
        }
    }

    private void cleanupStaleInAppViews() {
        Activity activity = activeKioskActivity();
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        removeTaggedChildren((ViewGroup) content);
    }

    private void removeTaggedChildren(ViewGroup group) {
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && ((String) tag).startsWith("now-playing-overlay:")) {
                group.removeViewAt(i);
                continue;
            }
            if (child instanceof ViewGroup) removeTaggedChildren((ViewGroup) child);
        }
    }

    private void readInitialScreensaverState() {
        if (host == null) return;
        host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, error) -> {
            if (!ok || !(data instanceof Boolean)) return;
            kioskScreensaverActive = (Boolean) data;
            main.post(this::updatePresentation);
        });
    }

    private void updatePresentation() {
        main.removeCallbacks(liveStatePollTask);
        if (partyFullscreen) {
            hideNowPlaying();
            hideDoorbell();
            ensurePartyView();
            main.post(liveStatePollTask);
            return;
        }
        if (!overlayActive()) {
            hideDoorbell();
            hideNowPlaying();
            removePartyView();
            return;
        }

        // Do not depend only on subscription callbacks here. Fotoo is an
        // external DreamService and some Android builds can delay those
        // callbacks while the Kiosk Activity is backgrounded. Polling the
        // selected HA entities keeps track changes, progress and door events
        // live while Fotoo owns the screen.
        main.post(liveStatePollTask);

        if (cameraTestMode && !doorbellCameraEntity.isEmpty()) {
            showDoorbell(true);
            pollCameraEntity();
        }
        pollMediaEntity();
        if (!partyCompact && directMusicAssistantAvailable()) pollMusicAssistantQueue();
        updateNowPlaying();
    }

    private void updateNowPlaying() {
        if (partyFullscreen || (partyCompact && overlayActive())) {
            hideNowPlaying();
            updateParty();
            return;
        }
        if (!overlayActive() || nowPlayingEntity.isEmpty() || !mediaVisible()) {
            hideNowPlaying();
            return;
        }
        ensureNowPlayingView();

        String title = attr(mediaAttributes, "media_title", "Now Playing");
        String artist = attr(mediaAttributes, "media_artist", "");
        String album = attr(mediaAttributes, "media_album_name", attr(mediaAttributes, "media_album", ""));

        mediaTitle.setText(title);
        mediaArtist.setText(artist);
        mediaArtist.setVisibility(artist.isEmpty() ? View.GONE : View.VISIBLE);
        mediaAlbum.setText(album);
        mediaAlbum.setVisibility(album.isEmpty() ? View.GONE : View.VISIBLE);

        String playlist = playlistState;
        if (playlist.isEmpty() || "unknown".equalsIgnoreCase(playlist) || "unavailable".equalsIgnoreCase(playlist)) {
            playlist = firstAttr(mediaAttributes,
                    "media_playlist", "playlist_name", "playlist", "source");
        }
        mediaPlaylist.setText(playlist.isEmpty() ? "" : "Playlist: " + playlist);
        mediaPlaylist.setVisibility(showPlaylist && !playlist.isEmpty() ? View.VISIBLE : View.GONE);

        String next = nextTrackState;
        if (next.isEmpty() || "unknown".equalsIgnoreCase(next) || "unavailable".equalsIgnoreCase(next)) {
            next = firstAttr(mediaAttributes,
                    "next_track", "next_title", "media_next_track", "queue_next");
        }
        mediaNext.setText(next.isEmpty() ? "" : "Næste: " + next);
        mediaNext.setVisibility(showNextTrack && !next.isEmpty() ? View.VISIBLE : View.GONE);

        updateProgress();

        String picture = attr(mediaAttributes, "entity_picture", "").trim();
        loadedMediaPicture = picture;

        if (picture.isEmpty()) {
            // Metadata can briefly arrive without artwork during a track
            // change. Keep the previous bitmap visible instead of flashing
            // blank, and only clear it if artwork is still absent later.
            main.removeCallbacks(clearMediaImageTask);
            main.postDelayed(clearMediaImageTask, 2500);
            return;
        }

        main.removeCallbacks(clearMediaImageTask);
        String pictureKey = mediaPictureKey(picture);
        Bitmap cached = mediaImageCache.get(pictureKey);
        if (cached != null) {
            if (!pictureKey.equals(renderedMediaPictureKey)) {
                mediaImage.setImageBitmap(cached);
            }
            renderedMediaPicture = picture;
            renderedMediaPictureKey = pictureKey;
            return;
        }

        // HA proxy tokens may change while the underlying cover does not.
        // Compare a stable key rather than the complete signed URL.
        if (pictureKey.equals(renderedMediaPictureKey)) {
            renderedMediaPicture = picture;
            return;
        }

        if (!mediaFetchPending) fetchMediaImage(picture);
    }

    private void openPartyFullscreen() {
        if (context == null || host == null) return;
        removePartyView();
        partyFullscreen = true;
        partyGuestLastPoll = 0;
        clearPartyGuests();
        forceOverlayPreview = false;
        main.removeCallbacks(forcePreviewTimeoutTask);
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("party_fullscreen", true)
                .putLong("party_until_ms", System.currentTimeMillis() + 30000).apply();
        context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
        partyHostCommand("stopScreensaver");
        partyHostCommand("hideNowPlaying");
        partyHostCommand("hideOverlayPage");
        if (activeKioskActivity() == null) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                try { context.startActivity(launch); } catch (Throwable ignored) {}
            }
        }
        updatePresentation();
    }

    private void registerPartyAudioReceiver() {
        partyAudioReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!partyFullscreen || partyView == null || "off".equals(partyEffect)) return;
                long age = SystemClock.elapsedRealtime() - intent.getLongExtra("at", 0);
                if (age < 0 || age > 1000) return;
                partyView.acceptAudio(intent.getFloatArrayExtra("bands"), intent.getFloatArrayExtra("waveform"),
                        intent.getIntExtra("fps", 20), intent.getBooleanExtra("demo", false));
            }
        };
        IntentFilter filter = new IntentFilter("me.jxl.kiosk.plugins.PARTY_AUDIO_FRAME");
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(partyAudioReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(partyAudioReceiver, filter);
    }

    private void setPartyEffect(String effect) {
        partyEffect = PartySignal.effect(effect);
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("effect", partyEffect).apply();
        if (partyView != null) partyView.setPresentation(partyEffect, partyQueueVisible);
        context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
    }

    private void setPartyGuests(boolean follow) {
        partyGuestsFollow = follow;
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putBoolean("guests_follow", follow).apply();
        clearPartyGuests(); partyGuestLastPoll = 0;
        updateParty();
        if (follow && partyFullscreen) pollPartyGuests();
    }

    private void setPartyQueue(boolean visible) {
        partyQueueVisible = visible;
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putBoolean("queue_visible", visible).apply();
        if (partyView != null) partyView.setPresentation(partyEffect, partyQueueVisible);
    }

    private void showPartyMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(anchor.getContext(), anchor);
        String[] ids = {"off", "spectrum", "mirror", "radial", "wave", "particles", "tunnel"};
        String[] names = {"Ingen visualisering", "Neon Spectrum", "Mirror Spectrum", "Radial Pulse", "Waveform", "Star Particles", "Neon Tunnel"};
        for (int i = 0; i < ids.length; i++) {
            final String effect = ids[i];
            menu.getMenu().add(1, i + 1, i, names[i]).setCheckable(true).setChecked(effect.equals(partyEffect))
                    .setOnMenuItemClickListener(item -> { setPartyEffect(effect); return true; });
        }
        menu.getMenu().setGroupCheckable(1, true, true);
        menu.getMenu().add("Vis gæste-QR fra Music Assistant").setCheckable(true).setChecked(partyGuestsFollow)
                .setOnMenuItemClickListener(item -> { setPartyGuests(!partyGuestsFollow); return true; });
        menu.getMenu().add("Aktivér gæsteadgang i Music Assistant")
                .setOnMenuItemClickListener(item -> { changePartyGuestAccess(true); return true; });
        menu.getMenu().add("Deaktivér gæsteadgang i Music Assistant")
                .setOnMenuItemClickListener(item -> { changePartyGuestAccess(false); return true; });
        menu.getMenu().add("Vis hele køen").setCheckable(true).setChecked(partyQueueVisible)
                .setOnMenuItemClickListener(item -> { setPartyQueue(!partyQueueVisible); return true; });
        menu.show();
    }

    private void clearPartyGuests() {
        final long generation = ++partyGuestGeneration;
        partyGuestUrl = ""; partyQr = null; partyGuestLastSuccess = 0;
        partyGuestStatus = "";
        Runnable clear = () -> {
            if (generation == partyGuestGeneration && partyView != null) partyView.setGuests(null, partyGuestText, "");
        };
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
        else main.post(clear);
    }

    private void changePartyGuestAccess(boolean enabled) {
        if (partyGuestChangePending || io == null || context == null) return;
        readKioskMusicAssistantConfig();
        final String queue = attr(mediaAttributes, "active_queue", "");
        if (queue.isEmpty() || maBaseUrl.isEmpty() || maToken.isEmpty()) {
            host.status("Vælg MA-højttalergruppe og tilslut Music Assistant først.", true); return;
        }
        final String base = maBaseUrl.trim().replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:").replaceAll("/+$", "");
        final String token = maToken;
        final long generation = partyGuestGeneration;
        partyGuestChangePending = true;
        partyGuestStatus = "Opdaterer gæsteadgang…";
        updateParty();
        io.execute(() -> {
            boolean changed = false;
            String message = "Kunne ikke ændre gæsteadgang. MA-tokenet skal have adgang til Party-indstillinger.";
            try {
                JSONObject filter = new JSONObject().put("provider_domain", "party").put("include_values", true);
                Object response = partyRequest(base, token, "config/providers", filter);
                if (!(response instanceof JSONArray)) throw new java.io.IOException("Party settings unavailable");
                String instance = PartyGuestConfig.matchingInstance(response instanceof JSONArray ? (JSONArray) response : null, queue);
                if (instance.isEmpty()) {
                    message = "Vælg den samme eksplicitte højttalergruppe som Party Player i Music Assistant først.";
                } else if (generation == partyGuestGeneration && queue.equals(attr(mediaAttributes, "active_queue", ""))) {
                    JSONObject args = new JSONObject().put("provider_domain", "party").put("instance_id", instance)
                            .put("values", new JSONObject().put("enable_guest_access", enabled));
                    Object saved = partyRequest(base, token, "config/providers/save", args);
                    changed = saved instanceof JSONObject && instance.equals(((JSONObject) saved).optString("instance_id", ""));
                }
            } catch (Throwable ignored) {}
            final boolean success = changed;
            final String error = message;
            main.post(() -> {
                partyGuestChangePending = false;
                if (host == null) return;
                if (success) {
                    clearPartyGuests(); partyGuestLastPoll = 0;
                    if (enabled) {
                        partyGuestsFollow = true;
                        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putBoolean("guests_follow", true).apply();
                    }
                    host.status(enabled ? "MA-gæsteadgang aktiveret." : "MA-gæsteadgang deaktiveret.", false);
                    pollPartyGuests();
                } else { partyGuestStatus = error; host.status(error, true); }
                updateParty();
            });
        });
    }

    private void pollPartyGuests() {
        if (!partyFullscreen || !partyGuestsFollow || io == null || context == null) return;
        long now = SystemClock.elapsedRealtime();
        if (partyGuestLastSuccess > 0 && now - partyGuestLastSuccess > 12000) {
            partyQr = null; partyGuestUrl = "";
            partyGuestStatus = "Gæsteadgang kunne ikke bekræftes";
            updateParty();
        }
        if (partyGuestPending || now - partyGuestLastPoll < 5000) return;
        partyGuestLastPoll = now;
        readKioskMusicAssistantConfig();
        final String queue = attr(mediaAttributes, "active_queue", "");
        if (queue.isEmpty() || maBaseUrl.isEmpty() || maToken.isEmpty()) return;
        final String base = maBaseUrl.trim().replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:").replaceAll("/+$", "");
        final String token = maToken;
        final long generation = partyGuestGeneration;
        final String previousUrl = partyGuestUrl;
        final Bitmap previousQr = partyQr;
        partyGuestPending = true;
        io.execute(() -> {
            String url = "", message = "Aktivér Party-plugin og gæsteadgang i Music Assistant";
            String caption = "Scan og tilføj musik til køen";
            Bitmap qr = null;
            try {
                Object player = partyRequest(base, token, "party/player", new JSONObject());
                if (PartyGuestLink.matches(queue, player)) {
                    Object link = partyRequest(base, token, "party/url", new JSONObject());
                    url = link instanceof String ? PartyGuestLink.validated((String) link, base) : "";
                    if (!url.isEmpty()) {
                        try {
                            Object config = partyRequest(base, token, "party/config", new JSONObject());
                            if (config instanceof JSONObject) {
                                String value = ((JSONObject) config).optString("qr_text", "");
                                if (!value.isEmpty() && !"null".equals(value)) caption = value.substring(0, Math.min(120, value.length()));
                            }
                        } catch (Throwable ignored) {}
                        qr = url.equals(previousUrl) && previousQr != null ? previousQr : partyQrBitmap(url);
                        message = qr == null ? "Gæste-QR kunne ikke dannes" : "";
                    }
                } else if (player instanceof String && !((String) player).isEmpty()) {
                    message = "Vælg samme højttalergruppe som Party Player i Music Assistant";
                }
            } catch (Throwable ignored) {}
            final String join = url, status = message, text = caption;
            final Bitmap symbol = qr;
            main.post(() -> {
                partyGuestPending = false;
                if (host == null || !partyFullscreen || !partyGuestsFollow || generation != partyGuestGeneration ||
                        !queue.equals(attr(mediaAttributes, "active_queue", ""))) return;
                partyGuestUrl = join; partyQr = symbol; partyGuestStatus = status; partyGuestText = text;
                partyGuestLastSuccess = SystemClock.elapsedRealtime();
                updateParty();
            });
        });
    }

    private Bitmap partyQrBitmap(String url) {
        try {
            QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
            int scale = 6, side = (qr.size + 8) * scale;
            int[] pixels = new int[side * side];
            for (int y = 0; y < side; y++) for (int x = 0; x < side; x++)
                pixels[y * side + x] = qr.getModule(x / scale - 4, y / scale - 4) ? Color.BLACK : Color.WHITE;
            return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888);
        } catch (Throwable ignored) { return null; }
    }

    private void closePartyFullscreen() {
        boolean wasFullscreen = partyFullscreen;
        partyFullscreen = false;
        if (context != null) {
            context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean("party_fullscreen", false).putLong("party_until_ms", 0).apply();
            if (wasFullscreen) context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
        }
        if (wasFullscreen) removePartyView();
        clearPartyGuests();
    }

    private void partyHostCommand(String command) {
        if (host == null) return;
        try { host.executeCommand(command, Collections.emptyMap(), (ok, data, error) -> {}); }
        catch (Throwable ignored) {}
    }

    private void ensurePartyView() {
        if (context == null || (!partyFullscreen && !overlayActive())) return;
        Activity activity = activeKioskActivity();
        if (partyFullscreen && activity == null) return;
        if (partyRoot != null && partyFullscreen && partyActivity != activity) removePartyView();
        if (partyRoot != null) return;
        FrameLayout root = new FrameLayout(context);
        root.setTag("now-playing-overlay:party");
        root.setClickable(partyFullscreen);
        root.setKeepScreenOn(partyFullscreen);
        partyView = new PartyView(context, partyFullscreen);
        partyView.setPresentation(partyEffect, partyQueueVisible);
        root.addView(partyView, new FrameLayout.LayoutParams(-1, -1));
        int width, height, gravity, offset;
        if (partyFullscreen) {
            TextView close = textView(24, true, Color.WHITE);
            close.setText("×"); close.setGravity(Gravity.CENTER);
            close.setContentDescription("Afslut Party Mode");
            close.setBackground(cardBackground(0x99353539, 24));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
            cp.topMargin = dp(10); cp.rightMargin = dp(10);
            root.addView(close, cp);
            close.setOnClickListener(v -> { closePartyFullscreen(); updatePresentation(); });
            TextView menu = textView(24, true, Color.WHITE);
            menu.setText("⋯"); menu.setGravity(Gravity.CENTER);
            menu.setContentDescription("Party-indstillinger");
            menu.setBackground(cardBackground(0x99353539, 24));
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.RIGHT);
            mp.bottomMargin = dp(10); mp.rightMargin = dp(10);
            root.addView(menu, mp);
            menu.setOnClickListener(v -> showPartyMenu(menu));
            width = -1; height = -1; gravity = Gravity.TOP | Gravity.LEFT; offset = 0;
        } else {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            width = Math.min(screenWidth - dp(16), Math.max(dp(260), screenWidth * nowPlayingWidthPercent / 100));
            height = dp(216);
            gravity = "Top".equals(nowPlayingPosition) ? Gravity.TOP | Gravity.CENTER_HORIZONTAL :
                    "Center".equals(nowPlayingPosition) ? Gravity.CENTER : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            offset = "Center".equals(nowPlayingPosition) ? 0 : dp(nowPlayingOffset);
            root.setAlpha(nowPlayingOpacity / 100f);
        }
        try {
            if (!addOverlayView(root, width, height, gravity, offset, false)) throw new IllegalStateException("No kiosk view");
            partyRoot = root;
            partyActivity = partyFullscreen ? activity : null;
        } catch (Throwable error) {
            partyView = null;
            if (host != null) host.status("Party view could not be shown.", true);
        }
    }

    private void removePartyView() {
        removeOverlayView(partyRoot);
        partyRoot = null; partyView = null; partyActivity = null;
    }

    private void updateParty() {
        if (!partyFullscreen && (!partyCompact || !overlayActive() || (!mediaVisible() && !forceOverlayPreview))) {
            removePartyView(); return;
        }
        ensurePartyView();
        if (partyView == null) return;
        partyView.setGuests(partyFullscreen && partyGuestsFollow ? partyQr : null,
                partyGuestText, partyFullscreen && partyGuestsFollow ? partyGuestStatus : "");
        if (partyModel == null || SystemClock.elapsedRealtime() - partyLastSuccess > 8000) {
            String title = attr(mediaAttributes, "media_title", "");
            java.util.List<PartyQueueModel.Track> fallback = new java.util.ArrayList<>();
            if (!title.isEmpty()) fallback.add(new PartyQueueModel.Track(mediaIdentity, title,
                    attr(mediaAttributes, "media_artist", ""), attr(mediaAttributes, "entity_picture", ""), true));
            PartyQueueModel model = new PartyQueueModel(fallback, estimatedMediaPosition(),
                    numberAttr(mediaAttributes, "media_duration", 0), mediaState == null ? "" : mediaState);
            partyView.setQueue(model, new HashMap<>(partyArtwork), "playing".equalsIgnoreCase(mediaState));
            fetchPartyArtwork(model);
        } else {
            partyView.setQueue(partyModel, new HashMap<>(partyArtwork), "playing".equalsIgnoreCase(mediaState));
        }
        if (nowPlayingEntity.isEmpty()) partyView.setMessage("Vælg højttaler under Now Playing entity");
        else if (maBaseUrl.isEmpty() || maToken.isEmpty()) partyView.setMessage("Tilslut Music Assistant i Kiosk for at vise hele køen");
        else if (attr(mediaAttributes, "active_queue", "").isEmpty()) partyView.setMessage("Venter på Music Assistant-kø fra den valgte højttaler");
    }

    private void pollPartyQueue() {
        if (partyView == null || partyPollPending || io == null || context == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - partyLastPoll < 2000) return;
        partyLastPoll = now;
        readKioskMusicAssistantConfig();
        // HA's MA entity exposes the active queue, including grouped playback.
        // Never substitute the kiosk Sendspin player or an unrelated active queue.
        final String queueId = attr(mediaAttributes, "active_queue", "").trim();
        if (queueId.isEmpty() || maBaseUrl.trim().isEmpty() || maToken.trim().isEmpty()) return;
        if (!queueId.equals(partyTarget)) {
            partyTarget = queueId; partyModel = null; partyLastSuccess = 0; partyGeneration++;
        }
        final long generation = partyGeneration;
        final String entity = nowPlayingEntity;
        final String base = maBaseUrl.trim().replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:").replaceAll("/+$", "");
        final String token = maToken;
        partyPollPending = true;
        io.execute(() -> {
            PartyQueueModel model = null;
            try {
                JSONObject args = new JSONObject(); args.put("queue_id", queueId);
                Object result = partyRequest(base, token, "player_queues/get", args);
                if (result instanceof JSONObject && queueId.equals(((JSONObject) result).optString("queue_id", ""))) {
                    JSONObject queue = (JSONObject) result;
                    JSONArray items = null;
                    try {
                        JSONObject itemArgs = new JSONObject();
                        itemArgs.put("queue_id", queueId); itemArgs.put("offset", PartyQueueModel.offset(queue)); itemArgs.put("limit", 5);
                        Object response = partyRequest(base, token, "player_queues/items", itemArgs);
                        if (response instanceof JSONArray) items = (JSONArray) response;
                    } catch (Throwable ignored) {}
                    model = PartyQueueModel.parse(queue, items, base);
                }
            } catch (Throwable ignored) {}
            final PartyQueueModel snapshot = model;
            main.post(() -> {
                partyPollPending = false;
                if (host == null || generation != partyGeneration || !entity.equals(nowPlayingEntity) ||
                        !queueId.equals(attr(mediaAttributes, "active_queue", ""))) return;
                if (snapshot != null) {
                    partyModel = snapshot; partyLastSuccess = SystemClock.elapsedRealtime();
                    fetchPartyArtwork(snapshot);
                }
                updateParty();
            });
        });
    }

    private Object partyRequest(String base, String token, String command, JSONObject args) throws Exception {
        URL url = new URL(base + "/api");
        if (!("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) || url.getUserInfo() != null) return null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2500); connection.setReadTimeout(3500);
        connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setUseCaches(false);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        JSONObject request = new JSONObject(); request.put("message_id", "now-playing-party");
        request.put("command", command); request.put("args", args);
        byte[] body = request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            if (connection.getResponseCode() != 200) return null;
            try (InputStream stream = connection.getInputStream()) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int count;
                while ((count = stream.read(chunk)) >= 0) {
                    if (bytes.size() + count > 512 * 1024) throw new java.io.IOException("Queue response too large");
                    bytes.write(chunk, 0, count);
                }
                Object response = new JSONTokener(bytes.toString("UTF-8")).nextValue();
                return response instanceof JSONObject && ((JSONObject) response).has("result")
                        ? ((JSONObject) response).opt("result") : response;
            }
        } finally { connection.disconnect(); }
    }

    private void fetchPartyArtwork(PartyQueueModel model) {
        if (io == null) return;
        for (PartyQueueModel.Track track : model.tracks) {
            String path = track.artwork;
            if (path.isEmpty() || partyArtwork.containsKey(path) || !partyArtworkPending.add(path)) continue;
            final String resolved = resolveHaUrl(path);
            final long generation = partyGeneration;
            io.execute(() -> {
                Bitmap cover = resolved == null ? null : fetchPartyBitmap(resolved);
                main.post(() -> {
                    partyArtworkPending.remove(path);
                    if (host == null || generation != partyGeneration || cover == null) return;
                    if (partyArtwork.size() >= 12) partyArtwork.clear();
                    partyArtwork.put(path, cover);
                    updateParty();
                });
            });
        }
    }

    private Bitmap fetchPartyBitmap(String source) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(source);
            if (!("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))) return null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2500); connection.setReadTimeout(3500);
            try (InputStream stream = connection.getInputStream()) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int count;
                while ((count = stream.read(chunk)) >= 0) {
                    if (bytes.size() + count > 4 * 1024 * 1024) return null;
                    bytes.write(chunk, 0, count);
                }
                byte[] data = bytes.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / decode.inSampleSize > 512) decode.inSampleSize *= 2;
                return BitmapFactory.decodeByteArray(data, 0, data.length, decode);
            }
        } catch (Throwable ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void updateProgress() {
        if (mediaProgress == null || mediaTime == null) return;

        double duration = numberAttr(mediaAttributes, "media_duration", 0);
        double position = estimatedMediaPosition();

        if (duration > 0) position = Math.max(0, Math.min(duration, position));
        boolean haveTimeline = duration > 0;
        mediaProgress.setVisibility(showProgress && haveTimeline ? View.VISIBLE : View.GONE);
        if (haveTimeline) {
            mediaProgress.setProgress((int) Math.round((position / duration) * 1000.0));
        }

        String label = "";
        if (!"Off".equals(timeLabels) && haveTimeline) {
            long elapsed = Math.max(0, Math.round(position));
            long total = Math.max(0, Math.round(duration));
            long remaining = Math.max(0, total - elapsed);
            if ("Elapsed / total".equals(timeLabels)) {
                label = formatTime(elapsed) + " / " + formatTime(total);
            } else if ("Remaining only".equals(timeLabels)) {
                label = "-" + formatTime(remaining);
            } else {
                label = formatTime(elapsed) + " / -" + formatTime(remaining);
            }
        }
        mediaTime.setText(label);
        mediaTime.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private double estimatedMediaPosition() {
        double position = mediaPositionAnchor;
        if ("playing".equalsIgnoreCase(mediaState)) {
            position += Math.max(0, SystemClock.elapsedRealtime() - mediaPositionAnchorRealtime) / 1000.0;
        }
        return Math.max(0, position);
    }

    private void applyMediaSnapshot(String state, Map<?, ?> attrs) {
        String previousState = mediaState;
        String identity = firstAttr(attrs, "media_content_id", "media_title");
        double positionAttr = numberAttr(attrs, "media_position", 0);

        boolean identityChanged = !Objects.equals(mediaIdentity, identity);
        boolean positionChanged = Double.isNaN(lastMediaPositionAttr) ||
                Math.abs(positionAttr - lastMediaPositionAttr) >= 0.5;
        boolean stateChanged = !Objects.equals(previousState, state);

        // Keep a monotonic local anchor. HA/MA does not publish media_position
        // every second; if we reset to the same stale attribute on each poll,
        // the progress bar appears frozen.
        if (identityChanged || positionChanged || stateChanged) {
            if (stateChanged && "paused".equalsIgnoreCase(state) &&
                    !identityChanged && !positionChanged) {
                mediaPositionAnchor = estimatedMediaPosition();
            } else {
                mediaPositionAnchor = Math.max(0, positionAttr);
            }
            mediaPositionAnchorRealtime = SystemClock.elapsedRealtime();
        }

        mediaIdentity = identity;
        lastMediaPositionAttr = positionAttr;
        mediaState = state;
        if (!Objects.equals(attr(mediaAttributes, "active_queue", ""), attr(attrs, "active_queue", ""))) {
            partyModel = null; partyLastSuccess = 0; partyTarget = ""; partyGeneration++;
            clearPartyGuests(); partyGuestLastPoll = 0;
        }
        mediaAttributes = attrs == null ? Collections.emptyMap() : attrs;
        main.post(this::updateNowPlaying);
    }

    private static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, s);
    }

    private void readKioskMusicAssistantConfig() {
        if (context == null) return;
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
            maBaseUrl = prefs.getString("flutter.ks.sendspin.ma_url", "");
            maToken = prefs.getString("flutter.ks.sendspin.ma_token", "");
            String source = prefs.getString("flutter.ks.sendspin.player_source", "");
            String player = prefs.getString("flutter.ks.sendspin.player", "");
            if (player == null) player = "";
            if (player.startsWith("ma:")) player = player.substring(3);
            maPlayerId = "ma".equals(source) ? player.trim() : "";
        } catch (Throwable ignored) {
            maBaseUrl = "";
            maToken = "";
            maPlayerId = "";
        }
    }

    private boolean directMusicAssistantAvailable() {
        return !maBaseUrl.trim().isEmpty() &&
                !maToken.trim().isEmpty() &&
                !maPlayerId.trim().isEmpty();
    }

    private void pollMusicAssistantQueue() {
        if (!directMusicAssistantAvailable() || maQueuePollPending || io == null) return;
        maQueuePollPending = true;
        final String base = maBaseUrl.trim().replaceAll("/+$", "");
        final String token = maToken;
        final String playerId = maPlayerId;
        io.execute(() -> {
            JSONObject queue = null;
            HttpURLConnection connection = null;
            InputStream stream = null;
            try {
                URL url = new URL(base + "/api");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(3500);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject args = new JSONObject();
                args.put("player_id", playerId);
                JSONObject request = new JSONObject();
                request.put("message_id", "now-playing-overlay");
                request.put("command", "player_queues/get_active_queue");
                request.put("args", args);
                byte[] body = request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }

                if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                    stream = connection.getInputStream();
                    String json = readText(stream);
                    JSONObject response = new JSONObject(json);
                    // Music Assistant's HTTP JSON-RPC /api serializes the
                    // command result directly. Older/test endpoints may wrap
                    // it under "result", so accept both shapes.
                    if (response.has("current_item") || response.has("queue_id")) {
                        queue = response;
                    } else {
                        Object result = response.opt("result");
                        if (result instanceof JSONObject) queue = (JSONObject) result;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                try { if (stream != null) stream.close(); } catch (Throwable ignored) {}
                if (connection != null) connection.disconnect();
            }

            final JSONObject resultQueue = queue;
            main.post(() -> {
                maQueuePollPending = false;
                if (resultQueue != null) {
                    maLastSuccessRealtime = SystemClock.elapsedRealtime();
                    applyMusicAssistantQueue(resultQueue, base);
                } else {
                    // Never let a direct MA connection failure blank the
                    // overlay; the configured HA media_player remains a
                    // fallback source.
                    pollMediaEntity();
                }
            });
        });
    }

    private void applyMusicAssistantQueue(JSONObject queue, String base) {
        JSONObject item = queue.optJSONObject("current_item");
        if (item == null) return;
        JSONObject media = item.optJSONObject("media_item");
        if (media == null) media = new JSONObject();
        JSONObject details = item.optJSONObject("streamdetails");
        JSONObject live = details == null ? null : details.optJSONObject("stream_metadata");

        String title = jsonText(live, "title");
        if (title.isEmpty()) title = jsonText(media, "name");
        if (title.isEmpty()) title = jsonText(item, "name");
        if (title.isEmpty()) return;

        String artist = jsonText(live, "artist");
        if (artist.isEmpty()) {
            JSONArray artists = media.optJSONArray("artists");
            if (artists != null) {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < artists.length(); i++) {
                    JSONObject a = artists.optJSONObject(i);
                    String name = jsonText(a, "name");
                    if (name.isEmpty()) continue;
                    if (joined.length() > 0) joined.append("/");
                    joined.append(name);
                }
                artist = joined.toString();
            }
        }

        String album = jsonText(live, "album");
        if (album.isEmpty()) {
            JSONObject albumObject = media.optJSONObject("album");
            album = jsonText(albumObject, "name");
        }

        double duration = jsonNumber(live, "duration", 0);
        if (duration <= 0) duration = jsonNumber(item, "duration", 0);
        if (duration <= 0) duration = jsonNumber(media, "duration", 0);
        double elapsed = jsonNumber(queue, "elapsed_time", 0);
        String state = jsonText(queue, "state");
        String queueItemId = jsonText(item, "queue_item_id");
        if (queueItemId.isEmpty()) queueItemId = jsonText(media, "uri");

        String artwork = jsonText(live, "image_url");
        if (artwork.isEmpty()) artwork = musicAssistantArtwork(item.opt("image"), base);
        // Some queue snapshots expose the track immediately but add artwork a
        // moment later. Do not blank a cover already learned from HA or the
        // previous snapshot while waiting for MA's image metadata.
        if (artwork.isEmpty()) artwork = attr(mediaAttributes, "entity_picture", "");

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("media_title", title);
        if (!artist.isEmpty()) attrs.put("media_artist", artist);
        if (!album.isEmpty()) attrs.put("media_album_name", album);
        if (duration > 0) attrs.put("media_duration", duration);
        attrs.put("media_position", elapsed);
        attrs.put("media_content_id", queueItemId);
        if (!artwork.isEmpty()) attrs.put("entity_picture", artwork);

        String queueName = jsonText(queue, "display_name");
        if (!queueName.isEmpty()) attrs.put("source", queueName);

        JSONObject nextItem = queue.optJSONObject("next_item");
        String nextTitle = musicAssistantItemTitle(nextItem);
        if (!nextTitle.isEmpty()) nextTrackState = nextTitle;

        // Music Assistant is metadata enrichment only. The configured HA
        // media_player remains authoritative for playing/paused/idle state.
        // This is important when the kiosk's own Sendspin/MA player is not the
        // same speaker selected in the plugin.
        if (mediaState == null || mediaState.trim().isEmpty()) return;
        Map<String, Object> merged = new HashMap<>();
        for (Map.Entry<?, ?> entry : mediaAttributes.entrySet()) {
            if (entry.getKey() instanceof String) {
                merged.put((String) entry.getKey(), entry.getValue());
            }
        }
        merged.putAll(attrs);
        // Never oscillate the cover between MA's queue image proxy and the
        // explicitly selected HA media_player's entity_picture. HA is the
        // stable primary artwork source; MA only fills the gap when HA has
        // none.
        if (!preferredHaArtwork.isEmpty()) {
            merged.put("entity_picture", preferredHaArtwork);
        }
        applyMediaSnapshot(mediaState, merged);
    }

    private static String musicAssistantItemTitle(JSONObject item) {
        if (item == null) return "";
        JSONObject media = item.optJSONObject("media_item");
        String title = jsonText(media, "name");
        if (title.isEmpty()) title = jsonText(item, "name");
        return title;
    }

    private static String musicAssistantArtwork(Object image, String base) {
        JSONObject obj = image instanceof JSONObject ? (JSONObject) image : null;
        if (obj == null && image instanceof JSONArray) {
            JSONArray arr = (JSONArray) image;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject candidate = arr.optJSONObject(i);
                if (candidate == null) continue;
                if ("thumb".equals(jsonText(candidate, "type"))) {
                    obj = candidate;
                    break;
                }
                if (obj == null) obj = candidate;
            }
        }
        if (obj == null) return "";
        String path = jsonText(obj, "path");
        if (obj.optBoolean("remotely_accessible", false) &&
                (path.startsWith("http://") || path.startsWith("https://"))) {
            return path;
        }
        String proxy = jsonText(obj, "proxy_id");
        return proxy.isEmpty() ? "" : base + "/imageproxy/" + proxy + "?size=512&fmt=jpg";
    }

    private static String jsonText(JSONObject object, String key) {
        if (object == null) return "";
        Object value = object.opt(key);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static double jsonNumber(JSONObject object, String key, double fallback) {
        if (object == null) return fallback;
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private static String readText(InputStream stream) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = stream.read(chunk)) >= 0) buffer.write(chunk, 0, count);
        return buffer.toString("UTF-8");
    }

    private void pollMediaEntity() {
        if (host == null || nowPlayingEntity.isEmpty() || mediaPollPending) return;
        mediaPollPending = true;
        Map<String, Object> args = new HashMap<>();
        args.put("entityId", nowPlayingEntity);
        host.executeCommand("getHaEntityState", args, (ok, data, error) -> {
            mediaPollPending = false;
            if (!ok || !(data instanceof Map)) return;
            Map<?, ?> snapshot = (Map<?, ?>) data;
            String state = snapshot.get("state") == null ? null : String.valueOf(snapshot.get("state"));
            Object attrsValue = snapshot.get("attributes");
            Map<?, ?> attrs = attrsValue instanceof Map ? (Map<?, ?>) attrsValue : Collections.emptyMap();
            preferredHaArtwork = attr(attrs, "entity_picture", "");
            applyMediaSnapshot(state, attrs);
        });
    }

    private void pollCameraEntity() {
        if (host == null || doorbellCameraEntity.isEmpty() || cameraStatePollPending) return;
        cameraStatePollPending = true;
        Map<String, Object> args = new HashMap<>();
        args.put("entityId", doorbellCameraEntity);
        host.executeCommand("getHaEntityState", args, (ok, data, error) -> {
            cameraStatePollPending = false;
            if (!ok || !(data instanceof Map)) return;
            Object attrsValue = ((Map<?, ?>) data).get("attributes");
            if (attrsValue instanceof Map) {
                cameraAttributes = (Map<?, ?>) attrsValue;
                if (doorbellView != null) main.post(this::refreshDoorbellImage);
            }
        });
    }

    private void pollDoorbellTrigger(int which) {
        String entity = which == 1 ? doorbellEntity : doorbellEntity2;
        if (host == null || entity.isEmpty()) return;
        if (which == 1 ? trigger1PollPending : trigger2PollPending) return;
        if (which == 1) trigger1PollPending = true; else trigger2PollPending = true;

        Map<String, Object> args = new HashMap<>();
        args.put("entityId", entity);
        host.executeCommand("getHaEntityState", args, (ok, data, error) -> {
            if (which == 1) trigger1PollPending = false; else trigger2PollPending = false;
            if (!ok || !(data instanceof Map)) return;
            Object value = ((Map<?, ?>) data).get("state");
            String state = value == null ? null : String.valueOf(value);
            handleDoorbellState(which, state, true);
        });
    }

    private void handleDoorbellState(int which, String state, boolean fromPoll) {
        String entity = which == 1 ? doorbellEntity : doorbellEntity2;
        if (entity.isEmpty()) return;

        boolean initialSeen = which == 1 ? doorbellInitialSeen : doorbellInitialSeen2;
        String lastState = which == 1 ? lastDoorbellState : lastDoorbellState2;
        boolean trigger = false;

        if (!initialSeen) {
            // When polling starts while a person is already present, showing
            // the camera is useful; subscription bootstrap events stay quiet.
            trigger = fromPoll && "on".equalsIgnoreCase(state);
            initialSeen = true;
        } else if (entity.startsWith("event.")) {
            trigger = !Objects.equals(lastState, state);
        } else {
            trigger = "on".equalsIgnoreCase(state) && !"on".equalsIgnoreCase(lastState);
        }

        if (which == 1) {
            doorbellInitialSeen = initialSeen;
            lastDoorbellState = state;
        } else {
            doorbellInitialSeen2 = initialSeen;
            lastDoorbellState2 = state;
        }

        if (trigger && overlayActive()) main.post(() -> showDoorbell(false));
    }

    private void pollSimpleTextEntity(String entity, boolean playlist) {
        if (host == null || entity.isEmpty()) return;
        if (playlist ? playlistPollPending : nextTrackPollPending) return;
        if (playlist) playlistPollPending = true; else nextTrackPollPending = true;

        Map<String, Object> args = new HashMap<>();
        args.put("entityId", entity);
        host.executeCommand("getHaEntityState", args, (ok, data, error) -> {
            if (playlist) playlistPollPending = false; else nextTrackPollPending = false;
            if (!ok || !(data instanceof Map)) return;
            Object value = ((Map<?, ?>) data).get("state");
            String state = value == null ? "" : String.valueOf(value);
            if (playlist) playlistState = state; else nextTrackState = state;
            main.post(this::updateNowPlaying);
        });
    }

    private boolean mediaVisible() {
        if ("playing".equalsIgnoreCase(mediaState)) return true;
        return showPaused && "paused".equalsIgnoreCase(mediaState);
    }

    private void ensureNowPlayingView() {
        if (nowPlayingView != null || context == null || windowManager == null) return;

        LinearLayout card = new LinearLayout(context);
        card.setTag("now-playing-overlay:nowplaying");
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(cardBackground(0xFF212226, 20));
        card.setAlpha(nowPlayingOpacity / 100f);

        mediaImage = new ImageView(context);
        mediaImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(118), dp(118));
        imageParams.rightMargin = dp(16);
        card.addView(mediaImage, imageParams);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        mediaTitle = textView(21, true, Color.WHITE);
        mediaArtist = textView(17, false, 0xFFE7E7E7);
        mediaAlbum = textView(14, false, 0xFFBDBDBD);
        mediaPlaylist = textView(13, false, 0xFFBDBDBD);
        mediaNext = textView(13, false, 0xFFD8D8D8);
        mediaProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mediaProgress.setMax(1000);
        mediaTime = textView(12, false, 0xFFBDBDBD);

        text.addView(mediaTitle);
        text.addView(mediaArtist);
        text.addView(mediaAlbum);
        text.addView(mediaPlaylist);
        text.addView(mediaNext);

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        progressParams.topMargin = dp(8);
        text.addView(mediaProgress, progressParams);
        text.addView(mediaTime);

        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int availableWidth = Math.max(dp(260), screenWidth - dp(24));
        int requestedWidth = screenWidth * nowPlayingWidthPercent / 100;
        int width = Math.min(availableWidth, Math.max(dp(260), requestedWidth));
        int gravity;
        int yOffset;
        if ("Top".equals(nowPlayingPosition)) {
            gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            yOffset = dp(nowPlayingOffset);
        } else if ("Center".equals(nowPlayingPosition)) {
            gravity = Gravity.CENTER;
            yOffset = 0;
        } else {
            gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            yOffset = dp(nowPlayingOffset);
        }

        try {
            if (!addOverlayView(
                    card,
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    gravity,
                    yOffset,
                    false)) {
                throw new IllegalStateException("No overlay host is available");
            }
            nowPlayingView = card;
            main.removeCallbacks(progressTickTask);
            main.post(progressTickTask);
        } catch (Throwable error) {
            host.status("Now Playing overlay failed: " + safeMessage(error), true);
        }
    }

    private void fetchMediaImage(String path) {
        if (mediaFetchPending || io == null || path == null || path.isEmpty()) return;
        final String key = mediaPictureKey(path);
        Bitmap cached = mediaImageCache.get(key);
        if (cached != null) {
            if (mediaImage != null) mediaImage.setImageBitmap(cached);
            renderedMediaPicture = path;
            renderedMediaPictureKey = key;
            return;
        }

        String resolved = resolveHaUrl(path);
        if (resolved == null) return;

        mediaFetchPending = true;
        final long requestSerial = ++mediaImageRequestSerial;
        io.execute(() -> {
            Bitmap bitmap = fetchBitmap(resolved, false);
            main.post(() -> {
                mediaFetchPending = false;
                if (mediaImage == null) return;

                String target = loadedMediaPicture == null ? "" : loadedMediaPicture;
                String targetKey = mediaPictureKey(target);

                if (bitmap != null) {
                    mediaImageCache.put(key, bitmap);
                    if (requestSerial == mediaImageRequestSerial &&
                            key.equals(targetKey)) {
                        // Atomic swap: the previous artwork remains visible
                        // until the new bitmap has decoded successfully.
                        mediaImage.setImageBitmap(bitmap);
                        renderedMediaPicture = target;
                        renderedMediaPictureKey = key;
                        return;
                    }
                }

                // A newer target may have appeared while this request was in
                // flight, or the HA/MA image proxy may need a moment after a
                // track change. Keep the old bitmap and retry the current
                // target without ever flashing the ImageView blank.
                if (!target.isEmpty() &&
                        !targetKey.equals(renderedMediaPictureKey)) {
                    main.postDelayed(() -> fetchMediaImage(target), 1200);
                }
            });
        });
    }

    private static String mediaPictureKey(String path) {
        if (path == null || path.isEmpty()) return "";
        int query = path.indexOf('?');
        if (query < 0) return path;

        String base = path.substring(0, query);
        String[] parts = path.substring(query + 1).split("&");
        StringBuilder kept = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int equals = part.indexOf('=');
            String name = (equals < 0 ? part : part.substring(0, equals))
                    .toLowerCase(java.util.Locale.ROOT);
            if ("token".equals(name) ||
                    "access_token".equals(name) ||
                    "authsig".equals(name) ||
                    "_ks".equals(name)) {
                continue;
            }
            if (kept.length() > 0) kept.append('&');
            kept.append(part);
        }
        return kept.length() == 0 ? base : base + "?" + kept;
    }

    private void showDoorbell(boolean testHold) {
        if (!overlayActive() || context == null || windowManager == null) return;
        main.removeCallbacks(hideDoorbellTask);
        main.removeCallbacks(cameraRefreshTask);
        if (testHold) doorbellHeldByTest = true;

        if (doorbellView == null) {
            FrameLayout frame = new FrameLayout(context);
            frame.setTag("now-playing-overlay:doorbell");
            frame.setBackground(cardBackground(0xFF000000, 22));
            frame.setAlpha(1f);
            if (cameraOpacity >= 100) {
                frame.setClickable(true);
                frame.setOnClickListener(v -> { /* consume touch inside camera; outside remains available to the screensaver */ });
            }

            doorbellImage = new ImageView(context);
            doorbellImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            doorbellImage.setAlpha(1f);
            frame.addView(doorbellImage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            doorbellLabel = textView(18, true, Color.WHITE);
            doorbellLabel.setText(doorbellHeldByTest ? "Dørklokke – TEST" : "Dørklokke");
            TextView label = doorbellLabel;
            label.setPadding(dp(14), dp(10), dp(14), dp(10));
            label.setBackground(cardBackground(0xE6000000, 14));
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.gravity = Gravity.TOP | Gravity.START;
            labelParams.leftMargin = dp(14);
            labelParams.topMargin = dp(14);
            frame.addView(label, labelParams);

            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int width = Math.max(dp(240), screenWidth * cameraSizePercent / 100);
            width = Math.min(screenWidth, width);
            int height = Math.max(dp(135), Math.round(width * 9f / 16f));
            if (height > screenHeight) {
                height = screenHeight;
                width = Math.min(screenWidth, Math.round(height * 16f / 9f));
            }
            int gravity;
            int yOffset;
            if ("Top".equals(cameraPosition)) {
                gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                yOffset = dp(18);
            } else if ("Bottom".equals(cameraPosition)) {
                gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                yOffset = dp(18);
            } else {
                gravity = Gravity.CENTER;
                yOffset = 0;
            }

            try {
                if (!addOverlayView(frame, width, height, gravity, yOffset, true)) {
                    throw new IllegalStateException("No overlay host is available");
                }
                doorbellView = frame;
                applyCameraVisibility();
            } catch (Throwable error) {
                host.status("Doorbell overlay failed: " + safeMessage(error), true);
                return;
            }
        }

        if (doorbellLabel != null) {
            doorbellLabel.setText(doorbellHeldByTest ? "Dørklokke – TEST" : "Dørklokke");
        }
        pollCameraEntity();
        refreshDoorbellImage();
        main.post(cameraRefreshTask);
        if (!doorbellHeldByTest) {
            main.postDelayed(hideDoorbellTask, doorbellSeconds * 1000L);
        }
    }

    private void applyCameraVisibility() {
        if (doorbellView != null) {
            // Keep the window itself fully opaque. Applying alpha to the parent
            // lets Android composite the entire overlay with Fotoo underneath,
            // which is exactly what looked washed out even at the high end.
            doorbellView.setAlpha(1f);
            doorbellView.setBackground(cardBackground(0xFF000000, 22));
        }
        if (doorbellImage != null) {
            if (cameraOpacity >= 100) {
                // Hard solid mode: no alpha at all on the camera image.
                doorbellImage.setAlpha(1f);
                doorbellImage.setImageAlpha(255);
            } else {
                float alpha = Math.max(0.1f, Math.min(0.99f, cameraOpacity / 100f));
                doorbellImage.setAlpha(alpha);
                doorbellImage.setImageAlpha(255);
            }
        }
    }

    private void refreshDoorbellImage() {
        if (cameraFetchPending || doorbellView == null || io == null) return;
        String picture = attr(cameraAttributes, "entity_picture", "");
        String resolved = resolveHaUrl(picture);
        if (resolved == null) return;

        cameraFetchPending = true;
        io.execute(() -> {
            Bitmap bitmap = fetchBitmap(resolved, true);
            main.post(() -> {
                cameraFetchPending = false;
                if (bitmap != null && doorbellImage != null && doorbellView != null) {
                    Bitmap shown = cameraOpacity >= 100 ? opaqueBitmap(bitmap) : bitmap;
                    doorbellImage.setImageBitmap(shown);
                    applyCameraAspectRatio(bitmap.getWidth(), bitmap.getHeight());
                    applyCameraVisibility();
                }
            });
        });
    }

    private void applyCameraAspectRatio(int sourceWidth, int sourceHeight) {
        if (doorbellView == null || sourceWidth <= 0 || sourceHeight <= 0) return;

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int width = Math.max(dp(240), screenWidth * cameraSizePercent / 100);
        width = Math.min(screenWidth, width);

        float aspect = (float) sourceWidth / (float) sourceHeight;
        int height = Math.max(dp(120), Math.round(width / aspect));
        if (height > screenHeight) {
            height = screenHeight;
            width = Math.min(screenWidth, Math.round(height * aspect));
        }

        try {
            ViewGroup.LayoutParams params = doorbellView.getLayoutParams();
            if (params == null || (params.width == width && params.height == height)) return;
            params.width = width;
            params.height = height;

            ViewParent parent = doorbellView.getParent();
            if (parent instanceof ViewGroup) {
                doorbellView.setLayoutParams(params);
                ((ViewGroup) parent).requestLayout();
            } else if (params instanceof WindowManager.LayoutParams && windowManager != null) {
                windowManager.updateViewLayout(doorbellView, params);
            } else {
                doorbellView.setLayoutParams(params);
            }
        } catch (Throwable ignored) {
            // A refresh frame may land while the overlay is closing.
        }
    }

    private void hideDoorbell() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::hideDoorbell);
            return;
        }
        main.removeCallbacks(hideDoorbellTask);
        main.removeCallbacks(cameraRefreshTask);
        hideDoorbellImmediate();
    }

    private void hideDoorbellImmediate() {
        View view = doorbellView;
        doorbellView = null;
        doorbellImage = null;
        doorbellLabel = null;
        cameraFetchPending = false;
        doorbellHeldByTest = false;
        removeOverlayView(view);
    }

    private void hideNowPlaying() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::hideNowPlaying);
            return;
        }
        hideNowPlayingImmediate();
    }

    private void hideNowPlayingImmediate() {
        main.removeCallbacks(progressTickTask);
        View view = nowPlayingView;
        nowPlayingView = null;
        mediaImage = null;
        mediaTitle = null;
        mediaArtist = null;
        mediaAlbum = null;
        mediaPlaylist = null;
        mediaNext = null;
        mediaProgress = null;
        mediaTime = null;
        mediaFetchPending = false;
        renderedMediaPicture = "";
        renderedMediaPictureKey = "";
        main.removeCallbacks(clearMediaImageTask);
        removeOverlayView(view);
    }

    private void showTestOverlay() {
        if (context == null || windowManager == null) return;
        TextView test = textView(18, true, Color.WHITE);
        test.setTag("now-playing-overlay:test");
        test.setText("Now Playing Overlay 0.1.0 test");
        test.setPadding(dp(18), dp(16), dp(18), dp(16));
        test.setBackground(cardBackground(0xE6212226, 18));
        int width = Math.min(
                dp(520),
                context.getResources().getDisplayMetrics().widthPixels - dp(32));
        try {
            if (!addOverlayView(
                    test,
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                    0,
                    false)) {
                throw new IllegalStateException("No overlay host is available");
            }
            main.postDelayed(() -> removeOverlayView(test), 8000);
        } catch (Throwable error) {
            host.status("Test overlay failed: " + safeMessage(error), true);
        }
    }

    private Bitmap opaqueBitmap(Bitmap source) {
        if (source == null) return null;
        try {
            Bitmap opaque = Bitmap.createBitmap(
                    source.getWidth(), source.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(source, 0, 0, null);
            return opaque;
        } catch (Throwable ignored) {
            return source;
        }
    }

    private WindowManager.LayoutParams cameraOverlayParams(int width, int height) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (cameraOpacity < 100) {
            // A translucent camera is deliberately pass-through.
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                cameraOpacity >= 100 ? PixelFormat.RGB_565 : PixelFormat.TRANSLUCENT
        );
        params.alpha = 1f;
        return params;
    }

    private void cleanupStaleOverlayWindows() {
        if (Looper.myLooper() != Looper.getMainLooper() || windowManager == null) return;
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = globalClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            Field viewsField = globalClass.getDeclaredField("mViews");
            Field paramsField = globalClass.getDeclaredField("mParams");
            viewsField.setAccessible(true);
            paramsField.setAccessible(true);
            Object viewsObject = viewsField.get(global);
            Object paramsObject = paramsField.get(global);
            if (!(viewsObject instanceof java.util.List) ||
                    !(paramsObject instanceof java.util.List)) return;
            java.util.List<?> views = (java.util.List<?>) viewsObject;
            java.util.List<?> params = (java.util.List<?>) paramsObject;
            java.util.List<View> stale = new java.util.ArrayList<>();
            int count = Math.min(views.size(), params.size());
            for (int i = 0; i < count; i++) {
                Object v = views.get(i);
                Object p = params.get(i);
                if (!(v instanceof View) || !(p instanceof WindowManager.LayoutParams)) continue;
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) p;
                boolean overlayType = lp.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY ||
                        lp.type == WindowManager.LayoutParams.TYPE_PHONE;
                if (!overlayType) continue;
                View view = (View) v;
                Object tag = view.getTag();
                if (tag instanceof String && ((String) tag).startsWith("now-playing-overlay:")) {
                    stale.add(view);
                    continue;
                }
                // Versions before 0.8.5 did not tag the root. The camera
                // overlay is still uniquely identifiable by its own label.
                if (containsDoorbellOverlayLabel(view)) stale.add(view);
            }
            for (View view : stale) {
                try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Hidden WindowManager internals are best-effort only. A normal
            // app/process restart also clears any window from an older session.
        }
    }

    private boolean containsDoorbellOverlayLabel(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().startsWith("Dørklokke")) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsDoorbellOverlayLabel(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        return new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    private TextView textView(float size, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable cardBackground(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private Bitmap fetchBitmap(String source, boolean cacheBust) {
        URLConnection connection = null;
        InputStream stream = null;
        try {
            String address = cacheBust
                    ? source + (source.contains("?") ? "&" : "?") + "_ks=" + System.currentTimeMillis()
                    : source;
            connection = new URL(address).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).setInstanceFollowRedirects(true);
            }
            stream = connection.getInputStream();
            return BitmapFactory.decodeStream(stream);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (stream != null) stream.close(); } catch (Throwable ignored) {}
            if (connection instanceof HttpURLConnection) ((HttpURLConnection) connection).disconnect();
        }
    }

    private String resolveHaUrl(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (haBaseUrl == null || haBaseUrl.isEmpty()) return null;
        String base = haBaseUrl.endsWith("/") ? haBaseUrl.substring(0, haBaseUrl.length() - 1) : haBaseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String attr(Map<?, ?> attributes, String key, String fallback) {
        Object value = attributes == null ? null : attributes.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return "null".equals(text) ? fallback : text;
    }

    private static String firstAttr(Map<?, ?> attributes, String... keys) {
        for (String key : keys) {
            String value = attr(attributes, key, "");
            if (!value.isEmpty() && !"unknown".equalsIgnoreCase(value) &&
                    !"unavailable".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private static double numberAttr(Map<?, ?> attributes, String key, double fallback) {
        Object value = attributes == null ? null : attributes.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); }
            catch (Throwable ignored) {}
        }
        return fallback;
    }

    private static String stringSetting(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private Activity findResumedActivity() {
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = threadClass.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread == null) return null;

            Field activitiesField = threadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObject = activitiesField.get(thread);
            if (!(activitiesObject instanceof Map)) return null;

            Activity fallback = null;
            for (Object record : ((Map<?, ?>) activitiesObject).values()) {
                if (record == null) continue;
                Class<?> recordClass = record.getClass();

                Field activityField = recordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activityValue = activityField.get(record);
                if (!(activityValue instanceof Activity)) continue;
                Activity activity = (Activity) activityValue;
                if (!activity.getPackageName().equals(context.getPackageName()) ||
                        activity.isFinishing() ||
                        (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    continue;
                }

                if (activity.hasWindowFocus()) return activity;

                boolean paused = false;
                boolean stopped = false;
                try {
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    paused = pausedField.getBoolean(record);
                } catch (Throwable ignored) {}
                try {
                    Field stoppedField = recordClass.getDeclaredField("stopped");
                    stoppedField.setAccessible(true);
                    stopped = stoppedField.getBoolean(record);
                } catch (Throwable ignored) {}

                if (!paused && !stopped) fallback = activity;
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context applicationContext(PluginHost host) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object value = currentApplication.invoke(null);
            if (value instanceof Application) return ((Application) value).getApplicationContext();
            if (value instanceof Context) return ((Context) value).getApplicationContext();
        } catch (Throwable ignored) {}

        Object current = host;
        for (int depth = 0; current != null && depth < 5; depth++) {
            Class<?> type = current.getClass();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    for (Field field : c.getDeclaredFields()) {
                        field.setAccessible(true);
                        Object value = field.get(current);
                        if (value instanceof Context) return ((Context) value).getApplicationContext();
                    }
                } catch (Throwable ignored) {}
            }

            Object enclosing = null;
            for (Class<?> c = type; c != null && enclosing == null; c = c.getSuperclass()) {
                try {
                    for (Field field : c.getDeclaredFields()) {
                        if (!field.getName().startsWith("this$")) continue;
                        field.setAccessible(true);
                        Object value = field.get(current);
                        if (value != null && value.getClass().getName().startsWith("me.jxl.")) {
                            enclosing = value;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            current = enclosing;
        }
        return null;
    }
}
