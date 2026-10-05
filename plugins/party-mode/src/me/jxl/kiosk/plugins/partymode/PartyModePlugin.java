// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Full-screen Party is independent of Now Playing and screensaver visibility. */
public final class PartyModePlugin implements KioskPlugin {
    private static final String PARTY_PREFS = "party_mode_presentation";
    private static final String PARTY_EVENT = "me.jxl.kiosk.plugins.PARTY_PRESENTATION_CHANGED";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PartyActivation activation = new PartyActivation();
    private PluginHost host;
    private Context context;
    private Application application;
    private Application.ActivityLifecycleCallbacks lifecycle;
    private Activity currentActivity, partyActivity;
    private ExecutorService io;
    private BroadcastReceiver partyAudioReceiver;
    private FrameLayout partyRoot;
    private PartyView partyView;
    private boolean partyFullscreen, automatic, onlyPlaying, showPaused = true;
    private String screenControls = "All controls";
    private String nowPlayingEntity = "", visibilityEntity = "", visibilityCondition = "Always", visibilityValue = "", visibilityState = "";
    private String mediaState = "", mediaIdentity = "";
    private Map<?, ?> mediaAttributes = Collections.emptyMap();
    private boolean mediaPending, visibilityPending;
    private final Set<String> subscriptions = new HashSet<>();
    private String maBaseUrl = "", maToken = "", haBaseUrl = "";
    private String partyEffect = "off";
    private boolean partyGuestsFollow = true, partyQueueVisible = true;
    private int gain = 3, fps = 20;
    private boolean partyPollPending, partyGuestPending, partyGuestChangePending;
    private long partyLastPoll, partyLastSuccess, partyGuestLastPoll, partyGuestLastSuccess, partyLastPostpone;
    private volatile long partyGeneration, partyGuestGeneration;
    private String partyTarget = "", partyGuestUrl = "", partyGuestStatus = "", partyGuestText = "Scan og tilføj musik til køen";
    private Bitmap partyQr;
    private PartyQueueModel partyModel;
    private final Map<String, Bitmap> partyArtwork = new HashMap<>();
    private final Set<String> partyArtworkPending = new HashSet<>();
    private double positionAnchor;
    private long positionAt;
    private double lastPosition = Double.NaN;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (host == null || context == null) return;
            pollMedia(); pollVisibility();
            updatePresentation();
            if (partyFullscreen) {
                updateParty(); pollPartyQueue(); pollPartyGuests();
                publishPresentation();
                if (activeKioskActivity() != null && SystemClock.elapsedRealtime() - partyLastPostpone > 15000) {
                    partyLastPostpone = SystemClock.elapsedRealtime(); partyHostCommand("postponeScreensaver");
                }
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host; context = applicationContext(host);
        if (context == null) { host.status("Android application context unavailable.", true); return; }
        io = Executors.newFixedThreadPool(3);
        main.post(() -> {
            registerLifecycle(); registerPartyAudioReceiver();
            currentActivity = findResumedActivity();
            removeStaleViews(currentActivity);
            context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("party_fullscreen", false).putLong("party_until_ms", 0).apply();
            context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
            configureOnMain(settings);
            readKioskMusicAssistantConfig();
            host.executeCommand("getDashboardState", Collections.emptyMap(), (ok, data, error) -> {
                if (ok && data instanceof Map) main.post(() -> {
                    Object url = ((Map<?, ?>) data).get("haBaseUrl");
                    if (url == null) url = ((Map<?, ?>) data).get("homeAssistantUrl");
                    if (url != null) haBaseUrl = String.valueOf(url);
                });
            });
            main.post(tick);
            host.status("Party Mode ready. Start/stop and visibility are independent of Now Playing.", false);
        });
    }

    @Override public synchronized void configure(Map<String, Object> settings) {
        main.post(() -> { if (context != null && host != null) configureOnMain(settings); });
    }
    private void configureOnMain(Map<String, Object> settings) {
        String speaker = setting(settings, "speakerEntity", "");
        if (!speaker.equals(nowPlayingEntity)) {
            partyGeneration++; partyModel = null; partyTarget = ""; partyLastSuccess = 0;
            mediaAttributes = Collections.emptyMap(); mediaState = ""; mediaIdentity = ""; lastPosition = Double.NaN;
            clearPartyGuests();
        }
        nowPlayingEntity = speaker;
        automatic = Boolean.TRUE.equals(settings.get("startAutomatically"));
        onlyPlaying = Boolean.TRUE.equals(settings.get("onlyWhilePlaying"));
        showPaused = !Boolean.FALSE.equals(settings.get("showPaused"));
        visibilityEntity = setting(settings, "visibilityEntity", "");
        visibilityCondition = setting(settings, "visibilityCondition", "Always");
        visibilityValue = setting(settings, "visibilityValue", ""); visibilityState = "";
        gain = intSetting(settings, "gain", 3, 1, 10);
        try { fps = Integer.parseInt(setting(settings, "refreshRate", "20 FPS").split(" ")[0]); } catch (Exception ignored) { fps = 20; }
        fps = Math.max(10, Math.min(30, fps));
        SharedPreferences prefs = context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE);
        partyEffect = PartySignal.effect(savedChoice(prefs, "effect", setting(settings, "effect", "off")));
        screenControls = savedChoice(prefs, "controls", setting(settings, "screenControls", "All controls"));
        partyQueueVisible = Boolean.parseBoolean(savedChoice(prefs, "queue_visible", String.valueOf(!Boolean.FALSE.equals(settings.get("showQueue")))));
        partyGuestsFollow = Boolean.parseBoolean(savedChoice(prefs, "guests_follow", String.valueOf(!Boolean.FALSE.equals(settings.get("showGuestQr")))));
        Set<String> wanted = new HashSet<>();
        if (!nowPlayingEntity.isEmpty()) wanted.add(nowPlayingEntity);
        if (!visibilityEntity.isEmpty()) wanted.add(visibilityEntity);
        for (String id : new HashSet<>(subscriptions)) if (!wanted.contains(id)) {
            host.unsubscribe("ha.entity." + id); subscriptions.remove(id);
        }
        for (String id : wanted) if (subscriptions.add(id)) host.subscribe("ha.entity." + id);
        if (partyFullscreen) { removePartyView(); publishPresentation(); }
        pollMedia(); pollVisibility(); updatePresentation();
    }
    private String savedChoice(SharedPreferences prefs, String key, String configured) {
        String previous = prefs.getString("configured_" + key, null);
        if (previous == null || !previous.equals(configured)) {
            prefs.edit().putString("configured_" + key, configured).putString(key, configured).apply();
            return configured;
        }
        return prefs.getString(key, configured);
    }

    @Override public synchronized void execute(String command, Map<String, Object> arguments) {
        main.post(() -> {
            if (host == null || context == null) return;
            if ("show".equals(command)) { activation.show(); pollMedia(); updatePresentation(); }
            else if ("hide".equals(command)) { activation.hide(); closePresentation(); }
            else if (command.startsWith("partyEffect")) setPartyEffect(command.substring(11).toLowerCase(java.util.Locale.ROOT));
            else if ("partyGuestsFollow".equals(command) || "partyGuestsHide".equals(command)) setPartyGuests("partyGuestsFollow".equals(command));
            else if ("partyGuestEnable".equals(command) || "partyGuestDisable".equals(command)) changePartyGuestAccess("partyGuestEnable".equals(command));
            else if ("partyQueueShow".equals(command) || "partyQueueHide".equals(command)) setPartyQueue("partyQueueShow".equals(command));
            else if ("controlsAll".equals(command) || "controlsClose".equals(command) || "controlsHidden".equals(command)) {
                screenControls = "controlsAll".equals(command) ? "All controls" : "controlsClose".equals(command) ? "Close only" : "Hidden";
                context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("controls", screenControls).apply();
                removePartyView(); updatePresentation();
            } else host.status("Unknown Party command: " + command, true);
        });
    }
    @Override public synchronized void onEvent(String event, Map<String, Object> payload) {
        if (!event.startsWith("ks.ha.entity.")) return;
        String id = payload.get("entityId") == null ? event.substring("ks.ha.entity.".length()) : String.valueOf(payload.get("entityId"));
        String state = payload.get("state") == null ? "" : String.valueOf(payload.get("state"));
        Object attributes = payload.get("attributes");
        Map<?, ?> attrs = attributes instanceof Map ? (Map<?, ?>) attributes : Collections.emptyMap();
        main.post(() -> {
            if (host == null) return;
            if (nowPlayingEntity.equals(id)) applyMedia(state, attrs);
            if (visibilityEntity.equals(id)) visibilityState = state;
            updatePresentation();
        });
    }
    @Override public synchronized void stop() {
        if (host != null) for (String id : subscriptions) try { host.unsubscribe("ha.entity." + id); } catch (Throwable ignored) {}
        subscriptions.clear();
        CountDownLatch done = new CountDownLatch(1);
        Runnable cleanup = () -> {
            try {
                main.removeCallbacksAndMessages(null); activation.hide(); closePresentation();
                if (context != null && partyAudioReceiver != null) context.unregisterReceiver(partyAudioReceiver);
                if (application != null && lifecycle != null) application.unregisterActivityLifecycleCallbacks(lifecycle);
                partyAudioReceiver = null; lifecycle = null;
            } catch (Throwable ignored) {} finally { done.countDown(); }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run();
        else { main.post(cleanup); try { done.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        if (io != null) io.shutdownNow(); io = null; host = null;
    }

    private void pollMedia() {
        if (host == null || nowPlayingEntity.isEmpty() || mediaPending) return;
        final String id = nowPlayingEntity; mediaPending = true;
        host.executeCommand("getHaEntityState", Collections.singletonMap("entityId", id), (ok, data, error) -> main.post(() -> {
            mediaPending = false;
            if (host == null || !nowPlayingEntity.equals(id) || !ok || !(data instanceof Map)) return;
            Map<?, ?> snapshot = (Map<?, ?>) data;
            Object attrs = snapshot.get("attributes");
            applyMedia(String.valueOf(snapshot.get("state")), attrs instanceof Map ? (Map<?, ?>) attrs : Collections.emptyMap());
            updatePresentation();
        }));
    }
    private void pollVisibility() {
        if (host == null || visibilityEntity.isEmpty() || visibilityPending) return;
        final String id = visibilityEntity; visibilityPending = true;
        host.executeCommand("getHaEntityState", Collections.singletonMap("entityId", id), (ok, data, error) -> main.post(() -> {
            visibilityPending = false;
            if (host == null || !visibilityEntity.equals(id)) return;
            visibilityState = ok && data instanceof Map ? String.valueOf(((Map<?, ?>) data).get("state")) : "unavailable";
            updatePresentation();
        }));
    }
    private void applyMedia(String state, Map<?, ?> attrs) {
        String identity = attr(attrs, "media_content_id", attr(attrs, "media_title", ""));
        double position = numberAttr(attrs, "media_position", 0);
        if (!identity.equals(mediaIdentity) || !state.equals(mediaState) || Double.isNaN(lastPosition) || Math.abs(position - lastPosition) >= 0.5) {
            positionAnchor = "paused".equals(state) && identity.equals(mediaIdentity) && position == lastPosition ? estimatedMediaPosition() : position;
            positionAt = SystemClock.elapsedRealtime();
        }
        if (!attr(attrs, "active_queue", "").equals(attr(mediaAttributes, "active_queue", ""))) {
            partyGeneration++; partyModel = null; partyTarget = ""; partyLastSuccess = 0;
            clearPartyGuests(); partyGuestLastPoll = 0;
        }
        mediaIdentity = identity; lastPosition = position; mediaState = state; mediaAttributes = attrs;
    }
    private double estimatedMediaPosition() {
        return Math.max(0, positionAnchor + ("playing".equalsIgnoreCase(mediaState) ? (SystemClock.elapsedRealtime() - positionAt) / 1000.0 : 0));
    }
    private boolean eligible() {
        return PartyVisibility.allowed(visibilityCondition, visibilityEntity, visibilityState, visibilityValue, LocalTime.now()) &&
                (!onlyPlaying || "playing".equalsIgnoreCase(mediaState) || showPaused && "paused".equalsIgnoreCase(mediaState));
    }
    private void updatePresentation() {
        if (host == null || context == null) return;
        boolean show = activation.visible(automatic, eligible());
        if (!show) { if (partyFullscreen) closePresentation(); return; }
        if (!partyFullscreen) {
            partyFullscreen = true; partyGuestLastPoll = 0; clearPartyGuests(); publishPresentation();
            partyHostCommand("stopScreensaver"); partyHostCommand("hideNowPlaying"); partyHostCommand("hideOverlayPage");
            if (activeKioskActivity() == null) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); try { context.startActivity(launch); } catch (Throwable ignored) {} }
            }
        }
        ensurePartyView(); updateParty();
    }
    private void publishPresentation() {
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("party_fullscreen", partyFullscreen).putLong("party_until_ms", partyFullscreen ? System.currentTimeMillis() + 30000 : 0)
                .putString("effect", partyEffect).putInt("gain", gain).putInt("fps", fps).apply();
        context.sendBroadcast(new Intent(PARTY_EVENT).setPackage(context.getPackageName()));
    }
    private void closePresentation() {
        boolean was = partyFullscreen; partyFullscreen = false; partyGeneration++;
        removePartyView(); clearPartyGuests();
        if (context != null && was) publishPresentation();
    }
    private void partyHostCommand(String command) {
        if (host != null) try { host.executeCommand(command, Collections.emptyMap(), (ok, data, error) -> {}); } catch (Throwable ignored) {}
    }
    private void ensurePartyView() {
        Activity activity = activeKioskActivity();
        if (!partyFullscreen || activity == null) return;
        if (partyRoot != null && partyActivity != activity) removePartyView();
        if (partyRoot != null) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) { host.status("Kiosk content view unavailable.", true); return; }
        FrameLayout root = new FrameLayout(activity); root.setTag("party-mode:view"); root.setClickable(true); root.setKeepScreenOn(true);
        root.setBackgroundColor(0xFF15171A);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        partyView = new PartyView(activity, true); partyView.setPresentation(partyEffect, partyQueueVisible);
        FrameLayout.LayoutParams body = new FrameLayout.LayoutParams(-1, -1);
        body.topMargin = "Hidden".equals(screenControls) ? 0 : dp(70);
        root.addView(partyView, body);
        if (!"Hidden".equals(screenControls)) {
            TextView close = button(activity, "×", "Afslut Party Mode");
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
            cp.topMargin = dp(10); cp.rightMargin = dp(12); root.addView(close, cp);
            close.setOnClickListener(v -> { activation.hide(); closePresentation(); });
            if ("All controls".equals(screenControls)) {
                TextView menu = button(activity, "Indstillinger", "Party-indstillinger og visualiseringer");
                FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(156), dp(48), Gravity.TOP | Gravity.RIGHT);
                mp.topMargin = dp(10); mp.rightMargin = dp(70); root.addView(menu, mp);
                menu.setOnClickListener(v -> showPartyMenu(menu));
            }
        }
        ((FrameLayout) content).addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setZ(100000f); root.bringToFront(); root.requestApplyInsets();
        partyRoot = root; partyActivity = activity;
    }
    private TextView button(Activity activity, String title, String description) {
        TextView view = new TextView(activity); view.setText(title); view.setTextColor(Color.WHITE); view.setTextSize("×".equals(title) ? 28 : 16);
        view.setGravity(Gravity.CENTER); view.setContentDescription(description);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xDD353539); bg.setCornerRadius(dp(24)); view.setBackground(bg);
        return view;
    }
    private void removePartyView() {
        if (partyRoot != null && partyRoot.getParent() instanceof ViewGroup) ((ViewGroup) partyRoot.getParent()).removeView(partyRoot);
        partyRoot = null; partyView = null; partyActivity = null;
    }
    private void removeStaleViews(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) content;
            for (int i = root.getChildCount() - 1; i >= 0; i--) if ("party-mode:view".equals(root.getChildAt(i).getTag())) root.removeViewAt(i);
        }
    }
    private void registerLifecycle() {
        if (!(context.getApplicationContext() instanceof Application)) return;
        application = (Application) context.getApplicationContext();
        lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { currentActivity = a; if (partyFullscreen) ensurePartyView(); }
            @Override public void onActivityPaused(Activity a) {
                if (currentActivity == a) currentActivity = null;
                if (partyFullscreen && partyActivity == a && !a.isChangingConfigurations()) { activation.hide(); closePresentation(); }
            }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (partyActivity == a) removePartyView(); }
        };
        application.registerActivityLifecycleCallbacks(lifecycle);
    }
    private Activity activeKioskActivity() {
        Activity a = currentActivity;
        if (a != null && !a.isFinishing() && !a.isDestroyed()) return a;
        return findResumedActivity();
    }
    private void readKioskMusicAssistantConfig() {
        SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
        maBaseUrl = prefs.getString("flutter.ks.sendspin.ma_url", ""); maToken = prefs.getString("flutter.ks.sendspin.ma_token", "");
    }
    private void setPartyEffect(String effect) {
        partyEffect = PartySignal.effect(effect);
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("effect", partyEffect).apply();
        if (partyView != null) partyView.setPresentation(partyEffect, partyQueueVisible);
        if (partyFullscreen) publishPresentation();
    }
    private void setPartyGuests(boolean follow) {
        partyGuestsFollow = follow;
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("guests_follow", String.valueOf(follow)).apply();
        clearPartyGuests(); partyGuestLastPoll = 0; updateParty();
        if (follow && partyFullscreen) pollPartyGuests();
    }
    private void setPartyQueue(boolean visible) {
        partyQueueVisible = visible;
        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("queue_visible", String.valueOf(visible)).apply();
        if (partyView != null) partyView.setPresentation(partyEffect, partyQueueVisible);
    }
    private void updateParty() {
        if (!partyFullscreen || partyView == null) return;
        partyView.setGuests(partyGuestsFollow ? partyQr : null, partyGuestText, partyGuestsFollow ? partyGuestStatus : "");
        if (partyModel == null || SystemClock.elapsedRealtime() - partyLastSuccess > 8000) {
            java.util.List<PartyQueueModel.Track> fallback = new java.util.ArrayList<>();
            String title = attr(mediaAttributes, "media_title", "");
            if (!title.isEmpty()) fallback.add(new PartyQueueModel.Track(mediaIdentity, title, attr(mediaAttributes, "media_artist", ""), attr(mediaAttributes, "entity_picture", ""), true));
            PartyQueueModel model = new PartyQueueModel(fallback, estimatedMediaPosition(), numberAttr(mediaAttributes, "media_duration", 0), mediaState);
            partyView.setQueue(model, new HashMap<>(partyArtwork), "playing".equalsIgnoreCase(mediaState)); fetchPartyArtwork(model);
        } else partyView.setQueue(partyModel, new HashMap<>(partyArtwork), "playing".equalsIgnoreCase(mediaState));
        if (nowPlayingEntity.isEmpty()) partyView.setMessage("Vælg højttaler i Party-pluginet");
        else if (maBaseUrl.isEmpty() || maToken.isEmpty()) partyView.setMessage("Tilslut Music Assistant i Kiosk");
        else if (attr(mediaAttributes, "active_queue", "").isEmpty()) partyView.setMessage("Venter på MA-kø fra den valgte højttaler");
    }
    private String resolveHaUrl(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (haBaseUrl.isEmpty()) return null;
        return haBaseUrl.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
    }
    private static String setting(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key); return value == null ? fallback : String.valueOf(value).trim();
    }
    private static int intSetting(Map<String, Object> settings, String key, int fallback, int min, int max) {
        Object value = settings.get(key);
        return value instanceof Number ? Math.max(min, Math.min(max, ((Number) value).intValue())) : fallback;
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
                        context.getSharedPreferences(PARTY_PREFS, Context.MODE_PRIVATE).edit().putString("guests_follow", "true").apply();
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
        JSONObject request = new JSONObject(); request.put("message_id", "party-mode");
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

    private static String attr(Map<?, ?> attributes, String key, String fallback) {
        Object value = attributes == null ? null : attributes.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return "null".equals(text) ? fallback : text;
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

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
