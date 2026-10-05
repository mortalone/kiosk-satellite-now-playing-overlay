// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.Collections;
import java.util.Map;

final class PartyView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final boolean fullscreen;
    private PartyQueueModel model = new PartyQueueModel(Collections.emptyList(), 0, 0, "");
    private Map<String, Bitmap> artwork = Collections.emptyMap();
    private Bitmap background;
    private String backgroundKey = "";
    private String message = "Henter afspilningskø…";
    private long anchor;
    private boolean playing;
    private String effect = "off";
    private boolean queueVisible = true;
    private Bitmap guestQr;
    private String guestText = "", guestStatus = "";
    private final PartyEffects effects = new PartyEffects();
    private final Runnable redraw = this::invalidate;

    PartyView(Context context, boolean fullscreen) {
        super(context); this.fullscreen = fullscreen;
        setContentDescription(fullscreen ? "Party Mode" : "Kompakt Party-kø");
    }

    void setQueue(PartyQueueModel model, Map<String, Bitmap> artwork, boolean playing) {
        if (this.model != model || this.playing != playing) anchor = SystemClock.elapsedRealtime();
        this.model = model; this.artwork = artwork; this.playing = playing;
        String key = "";
        for (PartyQueueModel.Track track : model.tracks) if (track.current) key = track.artwork;
        Bitmap cover = artwork.get(key);
        if (cover != null && !key.equals(backgroundKey)) {
            background = Bitmap.createScaledBitmap(cover, 18, 18, true);
            backgroundKey = key;
        } else if (key.isEmpty() || !key.equals(backgroundKey)) {
            background = null; backgroundKey = "";
        }
        if (model.tracks.isEmpty()) message = "Afspilningskøen er tom";
        invalidate();
    }

    void setMessage(String value) { message = value; invalidate(); }

    void setPresentation(String effect, boolean queueVisible) {
        this.effect = PartySignal.effect(effect); this.queueVisible = queueVisible;
        removeCallbacks(redraw); invalidate();
    }
    void setGuests(Bitmap qr, String caption, String status) {
        guestQr = qr; guestText = caption; guestStatus = status; invalidate();
    }
    void acceptAudio(float[] bands, float[] wave, int fps, boolean demo) {
        effects.accept(bands, wave, fps, demo); invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(redraw); super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        if (fullscreen) {
            canvas.drawColor(0xFF15171A);
            if (background != null) {
                paint.setAlpha("off".equals(effect) ? 130 : 45);
                canvas.drawBitmap(background, null, new RectF(0, 0, getWidth(), getHeight()), paint);
                paint.setAlpha(255);
                canvas.drawColor(0xA8000000);
            }
            if (!"off".equals(effect)) {
                effects.draw(canvas, getWidth(), getHeight(), effect, playing);
                removeCallbacks(redraw);
                postDelayed(redraw, effects.frameDelay());
                String status = effects.status();
                if (!status.isEmpty()) line(canvas, status, 14 * density, getHeight() - 14 * density,
                        getWidth() - 82 * density, 12 * sp, false, 0xDDFFFFFF);
            }
        }
        float areaLeft = 0, areaTop = 0, areaWidth = getWidth(), areaHeight = getHeight();
        if (fullscreen && guestQr != null) {
            boolean landscape = getWidth() >= getHeight();
            float side = Math.min(getWidth() * (landscape ? 0.27f : 0.46f), getHeight() * (landscape ? 0.55f : 0.26f));
            float x = landscape ? Math.max(16 * density, getWidth() * 0.035f) : (getWidth() - side) / 2;
            float y = landscape ? (getHeight() - side) / 2 - 18 * density : 64 * density;
            rect.set(x, y, x + side, y + side);
            paint.setAlpha(255); paint.setFilterBitmap(false);
            canvas.drawBitmap(guestQr, null, rect, paint);
            paint.setFilterBitmap(true);
            line(canvas, guestText, x, y + side + 25 * density,
                    Math.max(side, getWidth() * 0.30f), 14 * sp, false, Color.WHITE);
            if (landscape) {
                areaLeft = x + side + 24 * density;
                areaWidth = getWidth() - areaLeft;
            } else {
                areaTop = y + side + 44 * density;
                areaHeight = getHeight() - areaTop;
            }
        } else if (fullscreen && !guestStatus.isEmpty()) {
            line(canvas, guestStatus, 14 * density, 32 * density, getWidth() - 90 * density,
                    13 * sp, false, 0xDDFFFFFF);
        }
        if (model.tracks.isEmpty()) {
            line(canvas, message, areaLeft + 20 * density, areaTop + areaHeight / 2f, areaWidth - 40 * density,
                    (fullscreen ? 20 : 15) * sp, false, Color.WHITE);
            return;
        }
        java.util.List<PartyQueueModel.Track> tracks = model.tracks;
        if (fullscreen && !queueVisible) {
            java.util.List<PartyQueueModel.Track> selected = new java.util.ArrayList<>();
            for (PartyQueueModel.Track track : tracks) if (track.current) selected.add(track);
            tracks = selected;
        }
        float margin = (fullscreen ? 32 : 4) * density;
        float width = areaWidth - margin * 2;
        float neighbor = (fullscreen ? 66 : 30) * density;
        float current = (fullscreen ? queueVisible ? 148 : 88 : 76) * density;
        float gap = 4 * density;
        float desired = current + neighbor * (tracks.size() - 1) + gap * (tracks.size() - 1);
        float maxHeight = Math.max(1, areaHeight - (fullscreen ? 72 : 8) * density);
        float scale = Math.min(1, maxHeight / Math.max(1, desired));
        neighbor *= scale; current *= scale; gap *= scale;
        float total = desired * scale;
        float y = fullscreen && !queueVisible ? areaTop + areaHeight - total - 64 * density : areaTop + (areaHeight - total) / 2;
        int currentIndex = 0;
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).current) currentIndex = i;
        for (int i = 0; i < tracks.size(); i++) {
            PartyQueueModel.Track track = tracks.get(i);
            boolean active = track.current;
            float height = active ? current : neighbor;
            float inset = active ? 0 : Math.min(2, Math.abs(i - currentIndex)) * (fullscreen ? 20 : 9) * density;
            float x = areaLeft + margin + inset;
            float cardWidth = width - inset * 2;
            int alpha = active ? 235 : Math.abs(i - currentIndex) > 1 ? 95 : 155;
            paint.setColor(Color.argb(alpha, 55, 55, 59));
            rect.set(x, y, x + cardWidth, y + height);
            canvas.drawRoundRect(rect, 9 * density, 9 * density, paint);
            float pad = (active ? 6 : 3) * density;
            float coverSize = Math.max(1, height - pad * 2);
            float coverX = x + pad;
            float coverY = y + pad;
            Bitmap cover = artwork.get(track.artwork);
            rect.set(coverX, coverY, coverX + coverSize, coverY + coverSize);
            paint.setColor(0xFF35383D);
            canvas.drawRoundRect(rect, 5 * density, 5 * density, paint);
            if (cover != null) {
                int side = Math.min(cover.getWidth(), cover.getHeight());
                Rect crop = new Rect((cover.getWidth() - side) / 2, (cover.getHeight() - side) / 2,
                        (cover.getWidth() + side) / 2, (cover.getHeight() + side) / 2);
                clip.reset(); clip.addRoundRect(rect, 5 * density, 5 * density, Path.Direction.CW);
                int save = canvas.save(); canvas.clipPath(clip);
                paint.setAlpha(active ? 255 : 190);
                canvas.drawBitmap(cover, crop, rect, paint);
                paint.setAlpha(255); canvas.restoreToCount(save);
            }
            float tx = coverX + coverSize + 10 * density;
            float textWidth = Math.max(1, x + cardWidth - tx - 12 * density);
            float titleSize = (active ? fullscreen ? 28 : 19 : fullscreen ? 18 : 13.5f) * sp;
            float titleY = active ? y + height * 0.40f : y + height * 0.66f;
            line(canvas, track.title, tx, titleY, textWidth, titleSize, active,
                    active ? Color.WHITE : 0xDDFFFFFF);
            if (active && !track.artist.isEmpty()) {
                line(canvas, track.artist, tx, y + height * 0.71f, textWidth,
                        (fullscreen ? 18 : 13) * sp, false, 0xFFE0E0E0);
            }
            if (active && model.duration > 0) {
                double elapsed = model.elapsed + (playing ? Math.max(0, SystemClock.elapsedRealtime() - anchor) / 1000.0 : 0);
                float progress = (float) Math.max(0, Math.min(1, elapsed / model.duration));
                paint.setColor(0x4400B9F5);
                canvas.drawRect(x, y + height - 3 * density, x + cardWidth, y + height, paint);
                paint.setColor(0xFF00B9F5);
                canvas.drawRect(x, y + height - 3 * density, x + cardWidth * progress, y + height, paint);
            }
            y += height + gap;
        }
    }

    private void line(Canvas canvas, String value, float x, float baseline, float width,
            float size, boolean bold, int color) {
        text.setTextSize(size); text.setColor(color);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        canvas.drawText(TextUtils.ellipsize(value, text, width, TextUtils.TruncateAt.END).toString(), x, baseline, text);
    }
}
