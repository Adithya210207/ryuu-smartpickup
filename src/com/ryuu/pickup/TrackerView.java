package com.ryuu.pickup;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Random;

final class TrackerView {
    private TrackerView() {}

    private static final String[] STEPS = {"Confirmed", "Scheduled", "Cooking", "Packed", "Ready"};

    static Node create(AppState s) {
        Order o = s.order.get();
        if (o == null) return empty(s);
        if (o.collected) return done(s, o);

        // ---- header
        Label caption = Fx.label("Order " + o.code + " · " + o.r.name(), "caption");
        Label headline = Fx.label("", "display-l");
        Label sub = Fx.label("", "body");
        Label pill = Fx.label("", "status");
        HBox head = new HBox(16, new VBox(6, caption, headline, sub), Fx.spacer(), pill);
        head.setAlignment(Pos.TOP_LEFT);

        // ---- progress
        HBox progress = new HBox(8);
        Region[] tracks = new Region[STEPS.length];
        Label[] labels = new Label[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            tracks[i] = new Region();
            tracks[i].getStyleClass().add("seg-track");
            labels[i] = Fx.label(STEPS[i], "seg-label");
            VBox v = new VBox(8, tracks[i], labels[i]);
            HBox.setHgrow(v, Priority.ALWAYS);
            v.setMaxWidth(Double.MAX_VALUE);
            progress.getChildren().add(v);
        }

        // ---- timing
        LiveCanvas chart = new LiveCanvas((g, w, h, t) -> paintTiming(g, w, h, o));
        chart.setPrefHeight(170);
        chart.setMinHeight(170);
        VBox timing = new VBox(12, Fx.label("Timing", "title"));
        if (o.prevCookStart >= 0) {
            Label n = Fx.label("Re-timed for traffic: the kitchen now starts at " + Fx.time(o.at(o.cookStartSec()))
                + " instead of " + Fx.time(o.at(o.prevCookStart)) + ". Pickup moves to " + Fx.time(o.at(o.readySec())) + ".", "meta");
            n.setWrapText(true);
            n.setMinHeight(Region.USE_PREF_SIZE);
            HBox note = new HBox(n);
            note.getStyleClass().add("note");
            timing.getChildren().add(note);
        }
        timing.getChildren().add(chart);
        timing.getStyleClass().add("card");

        // ---- prototype controls
        HBox speeds = new HBox(2);
        speeds.getStyleClass().add("segmented");
        ToggleGroup tg = new ToggleGroup();
        for (int sp : new int[]{1, 10, 30, 120}) {
            ToggleButton b = new ToggleButton(sp + "×");
            b.getStyleClass().add("seg-btn");
            b.setToggleGroup(tg);
            b.setSelected(s.speed.get() == sp);
            b.setOnAction(e -> { b.setSelected(true); s.speed.set(sp); });
            speeds.getChildren().add(b);
        }
        Button traffic = new Button("Simulate a 6 min delay");
        traffic.getStyleClass().add("btn-secondary");
        traffic.setOnAction(e -> { s.trafficDelay(6); s.app.navigate("tracker", true); });
        HBox demo = new HBox(12, Fx.label("Prototype controls", "caption"), Fx.spacer(),
            Fx.label("Clock speed", "meta"), speeds, traffic);
        demo.setAlignment(Pos.CENTER_LEFT);
        demo.getStyleClass().add("demo-panel");

        VBox left = new VBox(16, timing, demo);
        HBox.setHgrow(left, Priority.ALWAYS);

        // ---- pickup pass
        StackPane qr = new StackPane(qr(o.code, 132));
        qr.getStyleClass().add("qr");
        qr.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox items = new VBox(6);
        for (AppState.CartLine l : o.lines) {
            HBox lr = new HBox(8, Fx.label(l.qty + "×", "meta"), Fx.label(l.item.name(), "meta-ink"));
            items.getChildren().add(lr);
        }
        Button collect = new Button("Mark as picked up");
        collect.getStyleClass().add("btn-primary");
        collect.setMaxWidth(Double.MAX_VALUE);
        collect.setOnAction(e -> { s.collect(); s.app.navigate("tracker", true); });
        Region hl = new Region();
        hl.getStyleClass().add("hairline");
        VBox pass = new VBox(12, Fx.label("Pickup pass", "caption"), qr, Fx.label(o.code, "pass-code"),
            Fx.label("Counter " + o.counter + " · " + o.r.name(), "meta"), hl, items, collect);
        pass.getStyleClass().add("card");
        pass.setPrefWidth(300);
        pass.setMinWidth(300);
        pass.setMaxHeight(Region.USE_PREF_SIZE);

        HBox main = new HBox(20, left, pass);
        VBox page = new VBox(24, head, progress, main);
        page.getStyleClass().add("page");

        AnimationTimer ui = new AnimationTimer() {
            int last = -2;
            @Override public void handle(long now) {
                int st = o.stage();
                int mins = Math.max(0, (int) Math.ceil((o.travelSec() - o.sim) / 60.0));
                String ready = Fx.time(o.at(o.readySec()));
                switch (st) {
                    case 0, 1 -> {
                        headline.setText("Ready at " + ready);
                        sub.setText("The kitchen starts at " + Fx.time(o.at(o.cookStartSec())) + ". Nothing to do until you arrive.");
                    }
                    case 2 -> { headline.setText("Ready at " + ready); sub.setText("Cooking now. You're " + mins + " min away."); }
                    case 3 -> { headline.setText("Ready at " + ready); sub.setText("Being packed at counter " + o.counter + "."); }
                    default -> {
                        headline.setText("Ready at counter " + o.counter);
                        sub.setText(mins > 0 ? "It's waiting for you. You're " + mins + " min away." : "Show your pickup pass at the counter.");
                    }
                }
                if (st != last) {
                    last = st;
                    String[] text = {"Confirmed", "Scheduled", "Cooking", "Packed", "Ready"};
                    String[] cls = {"status-muted", "status-muted", "status-accent", "status-accent", "status-green"};
                    int idx = Math.min(4, st);
                    pill.setText(text[idx]);
                    pill.getStyleClass().setAll("label", "status", cls[idx]);
                    for (int i = 0; i < STEPS.length; i++) {
                        boolean doneStep = i < idx || (idx == 4 && i == 4);
                        tracks[i].pseudoClassStateChanged(Fx.DONE, doneStep);
                        tracks[i].pseudoClassStateChanged(Fx.ACTIVE, i == idx && idx < 4);
                        labels[i].pseudoClassStateChanged(Fx.DONE, doneStep);
                        labels[i].pseudoClassStateChanged(Fx.ACTIVE, i == idx);
                    }
                }
                collect.setDisable(st < 4);
                traffic.setDisable(!o.canResync());
            }
        };
        Fx.whileShowing(page, ui::start, ui::stop);

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    // ---------------------------------------------------------------- timing chart

    private static final Font CAP = Font.font("Segoe UI", 11);
    private static final Font LBL = Font.font("Segoe UI Semibold", 12);
    private static final Color INK = Color.web("#1A1814"), T2 = Color.web("#5E5850"), T3 = Color.web("#8E877C"),
        TRACK = Color.web("#EEEAE4"), BLUE = Color.web("#3A6EA5"), BLUE_L = Color.web("#D9E4EF"),
        ACC = Color.web("#C8461A"), ACC_L = Color.web("#F3DDD3"), GREEN = Color.web("#2F7D59"), AMBER = Color.web("#A8680C");

    private static void paintTiming(GraphicsContext g, double w, double h, Order o) {
        double x0 = 90, x1 = w - 110, end = o.endSec();
        double yY = 40, yK = 96;
        java.util.function.DoubleUnaryOperator X = sec -> x0 + (x1 - x0) * sec / end;
        double xt = X.applyAsDouble(o.travelSec()), xc = X.applyAsDouble(o.cookStartSec()),
            xr = X.applyAsDouble(o.readySec()), xn = X.applyAsDouble(o.sim);

        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(LBL);
        g.setFill(INK);
        g.fillText("You", 0, yY);
        g.fillText("Kitchen", 0, yK);

        g.setFill(TRACK);
        g.fillRoundRect(x0, yY - 4, x1 - x0, 8, 8, 8);
        g.fillRoundRect(x0, yK - 4, x1 - x0, 8, 8, 8);
        g.setFill(BLUE_L);
        g.fillRoundRect(x0, yY - 4, xt - x0, 8, 8, 8);
        g.setFill(ACC_L);
        g.fillRoundRect(xc, yK - 4, xr - xc, 8, 8, 8);
        g.setFill(BLUE);
        double ye = Math.min(xn, xt);
        if (ye > x0) g.fillRoundRect(x0, yY - 4, ye - x0, 8, 8, 8);
        g.setFill(ACC);
        double ke = Math.min(xn, xr);
        if (ke > xc) g.fillRoundRect(xc, yK - 4, ke - xc, 8, 8, 8);

        g.setFont(CAP);
        g.setFill(T3);
        g.setTextAlign(TextAlignment.CENTER);
        if (xc - x0 > 110) g.fillText("Not started yet", (x0 + xc) / 2, yK + 18);
        if (xr - xc > 60) g.fillText(o.prepMin + " min cook", (xc + xr) / 2, yK + 18);

        if (o.prevCookStart >= 0) {
            double gx0 = X.applyAsDouble(o.prevCookStart), gx1 = X.applyAsDouble(o.prevReady);
            g.setStroke(AMBER);
            g.setLineWidth(1);
            g.setLineDashes(3, 3);
            g.strokeRoundRect(gx0, yK - 6, gx1 - gx0, 12, 12, 12);
            g.setLineDashes((double[]) null);
            g.setFill(AMBER);
            g.fillText("Previous plan", (gx0 + gx1) / 2, yK - 18);
        }

        // now
        g.setStroke(INK);
        g.setLineWidth(1);
        g.strokeLine(xn, yY - 18, xn, yK + 8);
        g.setFill(INK);
        g.fillText("Now", xn, yY - 28);

        // pickup
        g.setStroke(GREEN);
        g.setLineDashes(3, 3);
        g.strokeLine(xr, yY - 18, xr, yK + 8);
        g.setLineDashes((double[]) null);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(GREEN);
        g.setFont(LBL);
        g.fillText("Pickup", xr + 12, yY);
        g.setFont(CAP);
        g.setFill(T2);
        g.fillText(Fx.time(o.at(o.readySec())), xr + 12, yY + 16);
        g.fillText("No wait", xr + 12, yK);

        // axis
        g.setFill(T3);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(Fx.time(o.placedAt), x0, h - 14);
        if (xc - x0 > 80 && xr - xc > 60) {
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("Starts " + Fx.time(o.at(o.cookStartSec())), xc, h - 14);
        }
    }

    static Canvas qr(String code, double size) {
        Canvas c = new Canvas(size, size);
        GraphicsContext g = c.getGraphicsContext2D();
        int n = 25;
        double cell = size / n;
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, size, size);
        Random r = new Random(code.hashCode());
        g.setFill(INK);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                boolean finder = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8);
                if (!finder && r.nextDouble() < 0.48) g.fillRect(x * cell, y * cell, cell + 0.4, cell + 0.4);
            }
        for (int[] k : new int[][]{{0, 0}, {n - 7, 0}, {0, n - 7}}) {
            double x = k[0] * cell, y = k[1] * cell;
            g.setFill(INK);
            g.fillRect(x, y, cell * 7, cell * 7);
            g.setFill(Color.WHITE);
            g.fillRect(x + cell, y + cell, cell * 5, cell * 5);
            g.setFill(INK);
            g.fillRect(x + cell * 2, y + cell * 2, cell * 3, cell * 3);
        }
        return c;
    }

    // ---------------------------------------------------------------- empty / done

    private static Node empty(AppState s) {
        Label t1 = Fx.label("No current order", "display-l");
        Label t2 = Fx.label("When you place an order, its timing and pickup pass appear here.", "body");
        Button browse = new Button("Browse restaurants");
        browse.getStyleClass().add("btn-primary");
        browse.setOnAction(e -> s.app.navigate("discover"));
        Button demo = new Button("Try a sample order");
        demo.getStyleClass().add("btn-secondary");
        demo.setOnAction(e -> { s.demoOrder(); s.app.navigate("tracker", true); });
        VBox box = new VBox(12, t1, t2, new HBox(10, browse, demo));
        ((HBox) box.getChildren().get(2)).setPadding(new Insets(8, 0, 0, 0));
        box.getStyleClass().add("page");
        return box;
    }

    private static Node done(AppState s, Order o) {
        StackPane mark = new StackPane(Fx.icon(Icons.CHECK, 20, "icon-green"));
        mark.getStyleClass().add("success-mark");
        Label t1 = Fx.label("Picked up", "display-l");
        Label t2 = Fx.label("Collected from " + o.r.name() + " at counter " + o.counter + ".", "body");

        VBox summary = new VBox(10,
            row("Time at the counter", "40 sec"),
            row("Delivery fee saved", Fx.money(AppState.DELIVERY_FEE)),
            row("Paid", Fx.money(o.total)));
        summary.getStyleClass().add("card");
        summary.setMaxWidth(420);

        HBox rate = new HBox(2);
        for (int i = 0; i < 5; i++) {
            Button b = new Button(null, Fx.icon(Icons.STAR, 18, "rate-star"));
            b.getStyleClass().add("rate-btn");
            int n = i + 1;
            b.setOnAction(e -> {
                for (int j = 0; j < rate.getChildren().size(); j++) rate.getChildren().get(j).pseudoClassStateChanged(Fx.ACTIVE, j < n);
                s.app.toast("Thanks for rating " + o.r.name());
            });
            rate.getChildren().add(b);
        }

        Button again = new Button("Order again");
        again.getStyleClass().add("btn-primary");
        again.setOnAction(e -> { s.restaurant.set(o.r); s.order.set(null); s.app.navigate("menu", true); });
        Button home = new Button("Done");
        home.getStyleClass().add("btn-secondary");
        home.setOnAction(e -> { s.order.set(null); s.app.navigate("discover"); });

        VBox box = new VBox(14, mark, t1, t2, summary, Fx.label("Rate your order", "caption"), rate, new HBox(10, again, home));
        box.getStyleClass().add("page");
        return box;
    }

    private static HBox row(String l, String v) {
        HBox h = new HBox(Fx.label(l, "meta"), Fx.spacer(), Fx.label(v, "meta-ink"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
