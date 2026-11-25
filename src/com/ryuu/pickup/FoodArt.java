package com.ryuu.pickup;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.*;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Media for restaurants and dishes. Uses a real photo when one exists in
 * assets/photos/&lt;key&gt;.jpg|png, otherwise a quiet tinted placeholder.
 */
final class FoodArt {
    private FoodArt() {}

    private static final Map<String, Image> CACHE = new HashMap<>();

    static String slug(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    static Node media(double w, double h, String tint, int kind, String key) {
        Image img = image(key);
        if (img != null) {
            ImageView iv = new ImageView(img);
            double ir = img.getWidth() / img.getHeight(), tr = w / h;
            double vw = ir > tr ? img.getHeight() * tr : img.getWidth();
            double vh = ir > tr ? img.getHeight() : img.getWidth() / tr;
            iv.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setSmooth(true);
            return iv;
        }
        Canvas c = new Canvas(w, h);
        paintPlaceholder(c.getGraphicsContext2D(), w, h, tint, kind);
        return c;
    }

    static Image image(String key) {
        if (key == null) return null;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        Image found = null;
        for (String base : new String[]{System.getProperty("user.dir"), System.getProperty("ryuu.home", "")}) {
            if (base == null || base.isEmpty()) continue;
            for (String ext : new String[]{"jpg", "jpeg", "png", "webp"}) {
                File f = new File(base, "assets/photos/" + key + "." + ext);
                if (f.isFile()) {
                    Image i = new Image(f.toURI().toString(), 900, 900, true, true);
                    if (!i.isError()) { found = i; break; }
                }
            }
            if (found != null) break;
        }
        CACHE.put(key, found);
        return found;
    }

    static void paintPlaceholder(GraphicsContext g, double w, double h, String tint, int kind) {
        Color t = Color.web(tint);
        Color base = Color.web("#ECE8E1");
        g.setFill(base.interpolate(t, 0.10));
        g.fillRect(0, 0, w, h);
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(255, 255, 255, 0.0)), new Stop(1, Color.rgb(0, 0, 0, 0.04))));
        g.fillRect(0, 0, w, h);

        double s = Math.min(w, h) * 0.2, cx = w / 2, cy = h / 2 + s * 0.1;
        g.setStroke(t.deriveColor(0, 0.5, 0.8, 0.45));
        g.setLineWidth(Math.max(1.5, s * 0.07));
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        switch (kind) {
            case 1 -> { // cup
                double top = cy - s * 0.8, bot = cy + s * 0.75, tw = s * 0.6, bw = s * 0.45;
                g.strokePolygon(new double[]{cx - tw, cx + tw, cx + bw, cx - bw}, new double[]{top, top, bot, bot}, 4);
                g.strokeLine(cx - tw - s * 0.08, top - s * 0.15, cx + tw + s * 0.08, top - s * 0.15);
                g.strokeLine(cx - tw * 0.85, cy - s * 0.1, cx + tw * 0.85, cy - s * 0.1);
            }
            case 2 -> { // plate
                g.strokeOval(cx - s * 1.2, cy - s * 0.55, s * 2.4, s * 1.1);
                g.strokeOval(cx - s * 0.8, cy - s * 0.35, s * 1.6, s * 0.7);
            }
            default -> { // bowl
                g.strokeArc(cx - s, cy - s, s * 2, s * 2, 180, 180, ArcType.OPEN);
                g.strokeLine(cx - s * 1.15, cy, cx + s * 1.15, cy);
                g.strokeLine(cx - s * 0.35, cy + s * 1.15, cx + s * 0.35, cy + s * 1.15);
            }
        }
    }
}
