package com.ryuu.pickup;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/** A cover-cropped photo that fills whatever size it is given, with rounded corners. */
final class Photo extends Region {
    private final ImageView view;
    private final Canvas placeholder;
    private final Rectangle clip = new Rectangle();
    private final String tint;
    private final int kind;
    private String monogram;

    /** A photo when one exists, otherwise a quiet monogram tile with the restaurant's initials. */
    static Photo of(String key, String name, String tint, int kind, double radius, double aspect) {
        Photo p = new Photo(key, tint, kind, radius, aspect);
        p.monogram = initials(name);
        return p;
    }

    static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+")) {
            if (w.isEmpty() || w.equalsIgnoreCase("the") || w.equalsIgnoreCase("by") || w.equalsIgnoreCase("hotel")
                || w.equalsIgnoreCase("sree") || w.equalsIgnoreCase("sri") || w.equalsIgnoreCase("shree")) continue;
            b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.length() == 0 ? name.substring(0, 1).toUpperCase() : b.toString();
    }

    /** @param aspect height / width when laid out by width (0 = use the given height) */
    Photo(String key, String tint, int kind, double radius, double aspect) {
        this.tint = tint;
        this.kind = kind;
        Image img = FoodArt.image(key);
        if (img != null) {
            view = new ImageView(img);
            view.setSmooth(true);
            placeholder = null;
            getChildren().add(view);
        } else {
            view = null;
            placeholder = new Canvas();
            getChildren().add(placeholder);
        }
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        setClip(clip);
        setMinSize(0, 0);
        if (aspect > 0) widthProperty().addListener((o, a, b) -> {
            double h = Math.round(b.doubleValue() * aspect);
            setMinHeight(h);
            setPrefHeight(h);
            setMaxHeight(h);
        });
    }

    Photo fixed(double w, double h) {
        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);
        return this;
    }

    @Override protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        clip.setWidth(w);
        clip.setHeight(h);
        if (w <= 0 || h <= 0) return;
        if (view != null) {
            Image img = view.getImage();
            double ir = img.getWidth() / img.getHeight(), tr = w / h;
            double vw = ir > tr ? img.getHeight() * tr : img.getWidth();
            double vh = ir > tr ? img.getHeight() : img.getWidth() / tr;
            view.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
            view.setFitWidth(w);
            view.setFitHeight(h);
        } else if (placeholder.getWidth() != w || placeholder.getHeight() != h) {
            placeholder.setWidth(w);
            placeholder.setHeight(h);
            if (monogram == null) {
                FoodArt.paintPlaceholder(placeholder.getGraphicsContext2D(), w, h, tint, kind);
            } else {
                var g = placeholder.getGraphicsContext2D();
                javafx.scene.paint.Color t = javafx.scene.paint.Color.web(tint);
                g.setFill(javafx.scene.paint.Color.web("#EEEAE3").interpolate(t, 0.16));
                g.fillRect(0, 0, w, h);
                g.setFill(t.deriveColor(0, 0.7, 0.75, 0.85));
                g.setFont(javafx.scene.text.Font.font("Sitka Text", Math.min(w, h) * 0.34));
                g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                g.setTextBaseline(javafx.geometry.VPos.CENTER);
                g.fillText(monogram, w / 2, h / 2);
            }
        }
    }
}
