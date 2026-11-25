package com.ryuu.pickup;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;

/** A resizable canvas that repaints every frame while it is on screen. */
final class LiveCanvas extends Pane {
    interface Painter { void paint(GraphicsContext g, double w, double h, double t); }

    final Canvas canvas = new Canvas();

    LiveCanvas(Painter p) {
        canvas.setManaged(false);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);
        setMinSize(0, 0);
        AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) {
                double w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) return;
                GraphicsContext g = canvas.getGraphicsContext2D();
                g.clearRect(0, 0, w, h);
                g.save();
                p.paint(g, w, h, now / 1e9);
                g.restore();
            }
        };
        Fx.whileShowing(this, timer::start, timer::stop);
    }

    LiveCanvas(double w, double h, Painter p) {
        this(p);
        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);
    }
}
