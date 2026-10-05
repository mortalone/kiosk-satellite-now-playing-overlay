// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.nowplayingoverlay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

/** Native bounded spectrum effects; movement amplitude comes from the shared analyzer. */
final class PartyEffects {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] hsv = new float[] {0, 0.88f, 1};
    private float[] target = new float[0], levels = new float[0], waveform = new float[0];
    private long lastFrame;
    private int fps = 20;
    private boolean demo;

    void accept(float[] bands, float[] wave, int rate, boolean animated) {
        target = PartySignal.bounded(bands, false);
        waveform = PartySignal.bounded(wave, true);
        if (levels.length != target.length) levels = new float[target.length];
        lastFrame = SystemClock.elapsedRealtime();
        fps = Math.max(10, Math.min(30, rate)); demo = animated;
    }
    int frameDelay() { return 1000 / fps; }
    String status() {
        if (SystemClock.elapsedRealtime() - lastFrame > 1500) return "Venter på lyddata fra Visualizer";
        return demo ? "Demo · ikke lydstyret" : "";
    }
    void draw(Canvas c, float width, float height, String mode, boolean playing) {
        boolean fresh = playing && SystemClock.elapsedRealtime() - lastFrame <= 1000;
        float energy = 0;
        for (int i = 0; i < levels.length; i++) {
            float value = fresh ? target[i] : 0;
            levels[i] = levels[i] * (value > levels[i] ? 0.35f : 0.82f) + value * (value > levels[i] ? 0.65f : 0.18f);
            energy += levels[i];
        }
        if (levels.length == 0) return;
        energy /= levels.length;
        float time = SystemClock.elapsedRealtime() / 1000f;
        paint.setStyle(Paint.Style.FILL);
        if ("spectrum".equals(mode) || "mirror".equals(mode)) {
            float cell = width / levels.length;
            boolean mirror = "mirror".equals(mode);
            float baseline = mirror ? height * 0.54f : height * 0.92f;
            for (int i = 0; i < levels.length; i++) {
                float x = i * cell, amplitude = levels[i] * height * (mirror ? 0.40f : 0.75f);
                paint.setColor(color(i / (float) levels.length, 215));
                c.drawRoundRect(x + cell * 0.15f, baseline - amplitude, x + cell * 0.85f, baseline, cell * 0.2f, cell * 0.2f, paint);
                if (mirror) {
                    paint.setAlpha(85);
                    c.drawRoundRect(x + cell * 0.15f, baseline, x + cell * 0.85f, baseline + amplitude * 0.7f, cell * 0.2f, cell * 0.2f, paint);
                    paint.setAlpha(255);
                }
            }
        } else if ("radial".equals(mode)) {
            float radius = Math.min(width, height) * (0.19f + energy * 0.08f);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2, Math.min(width, height) / 220));
            for (int i = 0; i < levels.length; i++) {
                double angle = Math.PI * 2 * i / levels.length - Math.PI / 2;
                float extent = radius + levels[i] * Math.min(width, height) * 0.28f;
                paint.setColor(color(i / (float) levels.length, 220));
                c.drawLine(width / 2 + (float) Math.cos(angle) * radius, height / 2 + (float) Math.sin(angle) * radius,
                        width / 2 + (float) Math.cos(angle) * extent, height / 2 + (float) Math.sin(angle) * extent, paint);
            }
        } else if ("wave".equals(mode)) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            for (int layer = 0; layer < 3; layer++) {
                path.reset();
                for (int i = 0; i < waveform.length; i++) {
                    float x = width * i / Math.max(1, waveform.length - 1);
                    float y = height * 0.52f + (fresh ? waveform[i] : 0) * height * (0.28f - layer * 0.045f);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                paint.setColor(color(0.30f + layer * 0.25f, layer == 0 ? 220 : 95));
                paint.setStrokeWidth(layer == 0 ? 3 : 8); c.drawPath(path, paint);
            }
        } else if ("particles".equals(mode)) {
            for (int i = 0; i < 100; i++) {
                float strength = levels[i % levels.length];
                float phase = (time * (0.08f + strength * 0.13f) + i * 0.618034f) % 1;
                double angle = i * 2.39996 + time * 0.08;
                float distance = phase * Math.max(width, height) * 0.70f;
                float x = width / 2 + (float) Math.cos(angle) * distance;
                float y = height / 2 + (float) Math.sin(angle) * distance;
                paint.setColor(color(i / 100f, (int) (220 * strength * (1 - phase))));
                c.drawCircle(x, y, 2 + strength * 12, paint);
            }
        } else if ("tunnel".equals(mode)) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 + energy * 9);
            for (int ring = 0; ring < 14; ring++) {
                float phase = (ring / 14f + time * 0.07f) % 1;
                float radius = phase * Math.max(width, height) * 0.7f;
                paint.setColor(color(ring / 14f, (int) (200 * levels[ring % levels.length] * (1 - phase))));
                rect.set(width / 2 - radius, height / 2 - radius, width / 2 + radius, height / 2 + radius);
                c.drawOval(rect, paint);
            }
        }
        paint.setAlpha(255); paint.setStrokeCap(Paint.Cap.BUTT); paint.setStyle(Paint.Style.FILL);
    }
    private int color(float position, int alpha) {
        hsv[0] = 165 + position * 185;
        return Color.HSVToColor(Math.max(0, Math.min(255, alpha)), hsv);
    }
}
