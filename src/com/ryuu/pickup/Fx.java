package com.ryuu.pickup;

import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small UI helpers. */
final class Fx {
    private Fx() {}

    static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    static final PseudoClass DONE = PseudoClass.getPseudoClass("done");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    static Region icon(String path, double size, String... cls) {
        Region r = new Region();
        r.getStyleClass().add("icon");
        r.getStyleClass().addAll(cls);
        r.setStyle("-fx-shape: \"" + path + "\";");
        double h = path.equals(Icons.MINUS) ? Math.max(2, size / 6) : size; // shapes scale to fill the region
        r.setMinSize(size, h);
        r.setPrefSize(size, h);
        r.setMaxSize(size, h);
        return r;
    }

    static Label label(String text, String... cls) {
        Label l = new Label(text);
        l.getStyleClass().addAll(cls);
        return l;
    }

    static HBox chip(String iconPath, String text, String... cls) {
        HBox h = new HBox(6);
        h.setAlignment(Pos.CENTER_LEFT);
        if (iconPath != null) h.getChildren().add(icon(iconPath, 12, "chip-icon"));
        h.getChildren().add(label(text, "chip-text"));
        h.getStyleClass().add("chip");
        h.getStyleClass().addAll(cls);
        h.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        h.setMinWidth(Region.USE_PREF_SIZE);
        return h;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    static void clip(Region r, double radius) {
        Rectangle c = new Rectangle();
        c.setArcWidth(radius * 2);
        c.setArcHeight(radius * 2);
        c.widthProperty().bind(r.widthProperty());
        c.heightProperty().bind(r.heightProperty());
        r.setClip(c);
    }

    /** Runs on/off as the node enters and leaves a scene (used to start/stop timers & listeners). */
    static void whileShowing(Node n, Runnable on, Runnable off) {
        n.sceneProperty().addListener((o, a, b) -> {
            if (b != null && a == null) on.run();
            else if (b == null && a != null) off.run();
        });
        if (n.getScene() != null) on.run();
    }

    static void listen(Node n, Observable obs, InvalidationListener l) {
        whileShowing(n, () -> obs.addListener(l), () -> obs.removeListener(l));
    }

    static String time(LocalTime t) { return t.format(TF); }

    static String money(int v) { return "₹" + String.format(Locale.ENGLISH, "%,d", v); }

    static double textWidth(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    static StackPane toggleSwitch(BooleanProperty prop) {
        Region track = new Region();
        track.getStyleClass().add("switch-track");
        track.setPrefSize(44, 24);
        track.setMaxSize(44, 24);
        Circle knob = new Circle(9);
        knob.getStyleClass().add("switch-knob");
        StackPane sp = new StackPane(track, knob);
        sp.setMaxSize(44, 24);
        sp.setMinSize(44, 24);
        StackPane.setAlignment(knob, Pos.CENTER_LEFT);
        Runnable upd = () -> {
            track.pseudoClassStateChanged(ACTIVE, prop.get());
            TranslateTransition tt = new TranslateTransition(Duration.millis(180), knob);
            tt.setToX(prop.get() ? 23 : 3);
            tt.play();
        };
        knob.setTranslateX(prop.get() ? 23 : 3);
        track.pseudoClassStateChanged(ACTIVE, prop.get());
        prop.addListener((o, a, b) -> upd.run());
        sp.setOnMouseClicked(e -> prop.set(!prop.get()));
        sp.setCursor(Cursor.HAND);
        return sp;
    }
}
