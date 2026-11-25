package com.ryuu.pickup;

import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** First-run introduction: the problem, then the three steps. Static illustrations, no motion. */
final class IntroView {
    private IntroView() {}

    private static final String[][] STEPS = {
        {"The problem", "Waiting is the hidden cost of food.",
            "Most people pay for delivery to avoid standing 10–20 minutes at a counter, not knowing when their food will be ready."},
        {"1  ·  Order on your way", "Choose a restaurant on your commute.",
            "See the places you'll pass and when you'll pass them. Order and pay before you leave."},
        {"2  ·  Timed to you", "Cooking starts when you're close.",
            "The kitchen starts from your live ETA. If you're delayed, it starts later, so your food is fresh when you arrive."},
        {"3  ·  Walk in, walk out", "Collect it and go.",
            "Show your pass at the counter. No queue and no delivery fee."}};

    private static final double W = 480, H = 360;
    private static final Font SERIF_L = Font.font("Sitka Text", 40);
    private static final Font SERIF_M = Font.font("Sitka Text", 20);
    private static final Font UI = Font.font("Segoe UI", 12);
    private static final Font UI_B = Font.font("Segoe UI Semibold", 12.5);
    private static final Color INK = Color.web("#1A1814"), T2 = Color.web("#5E5850"), T3 = Color.web("#8E877C"),
        LINE = Color.web("#E7E2DA"), TRACK = Color.web("#EEEAE4"), BLUE = Color.web("#3A6EA5"),
        ACC = Color.web("#C8461A"), GREEN = Color.web("#2F7D59"), AMBER = Color.web("#A8680C");

    static Node create(App app) {
        int[] step = {0};
        Label caption = Fx.label("", "caption");
        Label title = Fx.label("", "display-xl");
        title.setWrapText(true);
        Label body = Fx.label("", "body");
        body.setWrapText(true);
        HBox dots = new HBox(6);
        dots.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < STEPS.length; i++) {
            Region d = new Region();
            d.getStyleClass().add("intro-dot");
            dots.getChildren().add(d);
        }
        Button skip = new Button("Skip");
        skip.getStyleClass().add("btn-text");
        Button back = new Button("Back");
        back.getStyleClass().add("btn-secondary");
        Button next = new Button("Next");
        next.getStyleClass().add("btn-primary");
        HBox nav = new HBox(10, dots, Fx.spacer(), skip, back, next);
        nav.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(16, caption, title, body);
        text.setPrefWidth(420);
        text.setMaxWidth(420);
        Canvas art = new Canvas(W, H);
        HBox content = new HBox(64, text, art);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(48, brand(), content, nav);
        card.getStyleClass().add("intro-card");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        StackPane scrim = new StackPane(card);
        scrim.getStyleClass().add("intro-scrim");

        Runnable render = () -> {
            String[] s = STEPS[step[0]];
            caption.setText(s[0]);
            title.setText(s[1]);
            body.setText(s[2]);
            for (int i = 0; i < dots.getChildren().size(); i++)
                dots.getChildren().get(i).pseudoClassStateChanged(Fx.ACTIVE, i == step[0]);
            back.setVisible(step[0] > 0);
            back.setManaged(step[0] > 0);
            next.setText(step[0] == STEPS.length - 1 ? "Get started" : "Next");
            GraphicsContext g = art.getGraphicsContext2D();
            g.clearRect(0, 0, W, H);
            g.setFill(Color.WHITE);
            g.fillRoundRect(0, 0, W, H, 16, 16);
            g.setStroke(LINE);
            g.setLineWidth(1);
            g.strokeRoundRect(0.5, 0.5, W - 1, H - 1, 16, 16);
            switch (step[0]) {
                case 0 -> problem(g);
                case 1 -> order(g);
                case 2 -> timing(g);
                default -> pickup(g);
            }
        };
        next.setOnAction(e -> { if (step[0] == STEPS.length - 1) app.removeOverlay(scrim); else { step[0]++; render.run(); } });
        back.setOnAction(e -> { if (step[0] > 0) { step[0]--; render.run(); } });
        skip.setOnAction(e -> app.removeOverlay(scrim));
        render.run();
        scrim.setUserData((Runnable) () -> { step[0] = Math.min(STEPS.length - 1, step[0] + 1); render.run(); });
        return scrim;
    }

    private static Node brand() {
        Label l = Fx.label("Ryuu", "brand-name");
        Label s = Fx.label("Pickup", "brand-sub");
        HBox h = new HBox(8, l, s);
        h.setAlignment(Pos.BASELINE_LEFT);
        return h;
    }

    // ---------------------------------------------------------------- illustrations

    private static void text(GraphicsContext g, Font f, Color c, String s, double x, double y, TextAlignment a) {
        g.setFont(f);
        g.setFill(c);
        g.setTextAlign(a);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(s, x, y);
    }

    private static void problem(GraphicsContext g) {
        double cx = W / 2, cy = H / 2 - 18, r = 84;
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(TRACK);
        g.setLineWidth(6);
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(AMBER);
        g.strokeArc(cx - r, cy - r, r * 2, r * 2, 90, -360 * 0.72, ArcType.OPEN);
        g.setFill(TRACK);
        g.fillRoundRect(cx - 10, cy - r - 18, 20, 8, 3, 3);
        text(g, SERIF_L, INK, "14:20", cx, cy - 4, TextAlignment.CENTER);
        text(g, UI, T3, "minutes at the counter", cx, cy + 32, TextAlignment.CENTER);
        g.setStroke(LINE);
        g.setLineWidth(1);
        g.strokeLine(56, H - 70, W - 56, H - 70);
        text(g, UI, T2, "Or pay a delivery fee to avoid it", 56, H - 46, TextAlignment.LEFT);
        text(g, UI_B, INK, "₹49", W - 56, H - 46, TextAlignment.RIGHT);
    }

    private static void order(GraphicsContext g) {
        double x0 = 60, x1 = W - 60, y = 120;
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(Color.web("#D5CEC3"));
        g.setLineWidth(2);
        g.strokeLine(x0, y, x1, y);
        g.setFill(INK);
        g.fillOval(x0 - 5, y - 5, 10, 10);
        g.fillRect(x1 - 5, y - 5, 10, 10);
        text(g, UI, T3, "Saibaba Colony", x0, y + 24, TextAlignment.CENTER);
        text(g, UI, T3, "Airport", x1, y + 24, TextAlignment.CENTER);
        String[] names = {"Pazhamudhir", "Thalappakatti", "Annapoorna"};
        String[] times = {"8:24", "8:31", "8:46"};
        double[] pos = {0.12, 0.36, 0.9};
        for (int i = 0; i < names.length; i++) {
            double px = x0 + (x1 - x0) * pos[i];
            boolean sel = i == 1;
            g.setFill(sel ? INK : Color.WHITE);
            g.fillOval(px - 6, y - 6, 12, 12);
            g.setStroke(INK);
            g.setLineWidth(1.6);
            g.strokeOval(px - 5.5, y - 5.5, 11, 11);
            text(g, sel ? UI_B : UI, sel ? INK : T2, times[i], px, y - 22, TextAlignment.CENTER);
            text(g, UI, T3, names[i], px, y + 44, TextAlignment.CENTER);
        }
        double cw = W - 120, ch = 92, cx = 60, cyy = 196;
        g.setFill(Color.web("#F6F4F0"));
        g.fillRoundRect(cx, cyy, cw, ch, 12, 12);
        text(g, SERIF_M, INK, "Dindigul Thalappakatti", cx + 20, cyy + 28, TextAlignment.LEFT);
        text(g, UI, T2, "1× Chicken Biryani, 1× Chicken 65 Biryani", cx + 20, cyy + 54, TextAlignment.LEFT);
        text(g, UI_B, INK, "Paid ₹694", cx + cw - 20, cyy + 28, TextAlignment.RIGHT);
        text(g, UI, GREEN, "Pickup at 8:31, on your way", cx + 20, cyy + 74, TextAlignment.LEFT);
    }

    private static void timing(GraphicsContext g) {
        double x0 = 110, x1 = W - 60, y1 = 110, y2 = 190;
        double fire = x0 + (x1 - x0) * 0.62, old = x0 + (x1 - x0) * 0.44;
        text(g, UI_B, INK, "You", 40, y1, TextAlignment.LEFT);
        text(g, UI_B, INK, "Kitchen", 40, y2, TextAlignment.LEFT);
        g.setFill(TRACK);
        g.fillRoundRect(x0, y1 - 4, x1 - x0, 8, 8, 8);
        g.fillRoundRect(x0, y2 - 4, x1 - x0, 8, 8, 8);
        g.setFill(BLUE);
        g.fillRoundRect(x0, y1 - 4, x1 - x0, 8, 8, 8);
        g.setFill(ACC);
        g.fillRoundRect(fire, y2 - 4, x1 - fire, 8, 8, 8);
        // "was going to start here" -> "now starts here"
        g.setStroke(AMBER);
        g.setLineWidth(1.4);
        g.strokeLine(old, y2 - 10, old, y2 + 10);
        g.strokeLine(old, y2 + 22, fire - 4, y2 + 22);
        g.strokeLine(fire - 9, y2 + 18, fire - 4, y2 + 22);
        g.strokeLine(fire - 9, y2 + 26, fire - 4, y2 + 22);
        text(g, UI, AMBER, "Starts 6 min later", (old + fire) / 2, y2 + 40, TextAlignment.CENTER);
        text(g, UI, T3, "Not started yet", (x0 + old) / 2, y2 - 18, TextAlignment.CENTER);
        g.setLineWidth(1);
        g.setStroke(GREEN);
        g.setLineDashes(3, 3);
        g.strokeLine(x1, y1 - 26, x1, y2 + 14);
        g.setLineDashes((double[]) null);
        text(g, UI_B, GREEN, "Pickup", x1, y1 - 38, TextAlignment.CENTER);
        g.setStroke(LINE);
        g.strokeLine(40, H - 70, W - 40, H - 70);
        text(g, UI, T2, "Running late? The kitchen simply starts later.", 40, H - 46, TextAlignment.LEFT);
    }

    private static void pickup(GraphicsContext g) {
        double qx = 60, qy = 70, qs = 150;
        g.setFill(Color.WHITE);
        g.fillRect(qx, qy, qs, qs);
        java.util.Random r = new java.util.Random(7);
        int n = 21;
        double cell = qs / n;
        g.setFill(INK);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                boolean finder = (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13);
                if (!finder && r.nextDouble() < 0.45) g.fillRect(qx + x * cell, qy + y * cell, cell + 0.3, cell + 0.3);
            }
        for (int[] k : new int[][]{{0, 0}, {14, 0}, {0, 14}}) {
            double x = qx + k[0] * cell, y = qy + k[1] * cell;
            g.setFill(INK);
            g.fillRect(x, y, cell * 7, cell * 7);
            g.setFill(Color.WHITE);
            g.fillRect(x + cell, y + cell, cell * 5, cell * 5);
            g.setFill(INK);
            g.fillRect(x + cell * 2, y + cell * 2, cell * 3, cell * 3);
        }
        double tx = 250;
        text(g, UI, T3, "Pickup pass", tx, 86, TextAlignment.LEFT);
        text(g, SERIF_M, INK, "RY-2705", tx, 114, TextAlignment.LEFT);
        text(g, UI, T2, "Counter 6 · Thalappakatti", tx, 140, TextAlignment.LEFT);
        text(g, UI, T3, "At the counter", tx, 180, TextAlignment.LEFT);
        text(g, SERIF_M, GREEN, "40 seconds", tx, 206, TextAlignment.LEFT);
        g.setStroke(LINE);
        g.strokeLine(60, H - 70, W - 60, H - 70);
        text(g, UI, T2, "No queue", 60, H - 46, TextAlignment.LEFT);
        text(g, UI, T2, "No delivery fee", W / 2, H - 46, TextAlignment.CENTER);
        text(g, UI, T2, "No guessing", W - 60, H - 46, TextAlignment.RIGHT);
    }
}
