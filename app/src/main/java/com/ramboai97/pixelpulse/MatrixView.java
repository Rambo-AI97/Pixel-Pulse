package com.ramboai97.pixelpulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import com.ramboai97.pixelpulse.core.Config;
import com.ramboai97.pixelpulse.core.Px;
import com.ramboai97.pixelpulse.core.Scene;

import java.util.Calendar;

/** Live mini Glyph Matrix running the real Scene with a scripted demo. */
final class MatrixView extends View {
    static final int DEMO_CLOCK = 0, DEMO_BREATHE = 1, DEMO_MUSIC = 2;

    private final int demo;
    private final Config cfg;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Scene scene;
    private long start;
    private final Scene.Input in = new Scene.Input();
    private final float[] bands = new float[6];

    MatrixView(Context c, int demo, Config cfg) {
        super(c);
        this.demo = demo;
        this.cfg = cfg;
        reset();
    }

    private void reset() {
        start = SystemClock.uptimeMillis();
        scene = new Scene(cfg, null);
        scene.open(start, demo == DEMO_CLOCK, false);
        if (demo == DEMO_BREATHE) scene.longPress(start);
    }

    @Override protected void onMeasure(int w, int h) {
        int s = MeasureSpec.getSize(w);
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        long t = now - start;
        Calendar c = Calendar.getInstance();
        in.hour = c.get(Calendar.HOUR_OF_DAY);
        in.minute = c.get(Calendar.MINUTE);
        in.second = c.get(Calendar.SECOND);
        in.headingOk = true;
        in.heading = (float) (Math.sin(t / 2600.0) * 1.3);
        in.musicActive = false;
        in.bands = null;
        if (demo == DEMO_BREATHE && t > 3000 + scene.sessionMs() + 3500) reset();
        if (demo == DEMO_MUSIC) {
            long cyc = t % 12000;
            in.musicActive = cyc < 9000;
            for (int k = 0; k < 6; k++) {
                double beat = Math.pow(Math.max(0, Math.sin(t / 1000.0 * Math.PI * 2 * 1.9)), 6);
                bands[k] = (float) Math.max(0, Math.min(1,
                        (k == 0 ? 0.55 + 0.45 * beat : 0.25 + 0.35 * Math.abs(Math.sin(t / (260.0 + 70 * k) + k)))
                                * (1 - 0.08 * k)));
            }
            in.bands = bands;
        }
        int[] f = scene.frame(now, in);

        float size = getWidth();
        canvas.drawColor(Color.TRANSPARENT);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(size / 2, size / 2, size / 2, paint);
        float cell = size / 27.5f, off = (size - cell * 25) / 2;
        for (int y = 0; y < 25; y++)
            for (int x = 0; x < 25; x++) {
                if (!Px.on(x, y)) continue;
                int v = Math.max(30, f[y * 25 + x]);
                paint.setColor(Color.rgb(v, v, v));
                canvas.drawCircle(off + (x + 0.5f) * cell, off + (y + 0.5f) * cell, cell * 0.38f, paint);
            }
        postInvalidateOnAnimation();
    }
}
