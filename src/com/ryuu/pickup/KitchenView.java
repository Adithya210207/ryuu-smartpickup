package com.ryuu.pickup;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Restaurant partner screen: tickets fire on the customer's live ETA, not on order time.
 * Runs its own demo clock so the board is always moving.
 */
final class KitchenView {
    private KitchenView() {}

    private static final class Ticket {
        final String code, who, items, mode;
        double arrive;          // kitchen-clock seconds when the customer reaches the counter
        final double prep;      // seconds of cooking
        final boolean mine;
        Ticket(String code, String who, String items, String mode, double arriveMin, double prepMin, boolean mine) {
            this.code = code; this.who = who; this.items = items; this.mode = mode;
            this.arrive = arriveMin * 60; this.prep = prepMin * 60; this.mine = mine;
        }
        double fire() { return arrive - prep; }
    }

    static Node create(AppState s) {
        Data.Restaurant r = Data.byId("thalappakatti");
        List<Ticket> tickets = new ArrayList<>(List.of(
            new Ticket("RY-3104", "Priya", "1× Thalappakatti Chicken Biryani", "Bike", 8, 6, false),
            new Ticket("RY-3108", "Arjun", "2× Chicken 65 Biryani", "Car", 12, 7, false),
            new Ticket("RY-3111", "Meera", "1× Naidu Mutton Biryani", "Bus", 19, 7, false),
            new Ticket("RY-3115", "Rahul", "1× Mutton Kola Biryani", "Car", 24, 7, false),
            new Ticket("RY-3117", "Sana", "1× Paneer Biryani XL", "Bike", 9.5, 7, false),
            new Ticket("RY-3120", "Karthik", "3× Thalappakatti Chicken Biryani", "Car", 31, 8, false),
            new Ticket("RY-3122", "Neha", "1× Chicken 65 Biryani", "Bus", 37, 7, false),
            new Ticket("RY-3101", "Vikram", "1× Naidu Mutton Biryani", "Car", 4.5, 7, false),
            new Ticket("RY-3099", "Ananya", "2× Thalappakatti Chicken Biryani", "Bike", 5.2, 6, false)));
        java.time.LocalTime base = java.time.LocalTime.now().minusMinutes(6);
        double[] clock = {6 * 60};       // kitchen clock (sec), starts mid-service
        double[] last = {-1};
        double[] speed = {10};

        // ---- header
        Label k = Fx.label("Restaurant app · " + r.name(), "caption");
        Label title = Fx.label("Kitchen display", "display-l");
        Label sub = Fx.label("Orders start cooking from each customer's live ETA.", "body");
        Label clockLabel = Fx.label("", "num-l");
        HBox speeds = new HBox(2);
        speeds.getStyleClass().add("segmented");
        speeds.setMaxHeight(Region.USE_PREF_SIZE);
        ToggleGroup tg = new ToggleGroup();
        for (int sp : new int[]{1, 10, 30}) {
            ToggleButton b = new ToggleButton(sp + "×");
            b.getStyleClass().add("seg-btn");
            b.setToggleGroup(tg);
            b.setSelected(sp == 10);
            b.setOnAction(e -> { b.setSelected(true); speed[0] = sp; });
            speeds.getChildren().add(b);
        }
        HBox clockRow = new HBox(12, clockLabel, speeds);
        clockRow.setAlignment(Pos.CENTER_RIGHT);
        VBox clockBox = new VBox(4, right(Fx.label("Prototype clock", "caption")), clockRow);
        HBox head = new HBox(20, new VBox(6, k, title, sub), Fx.spacer(), clockBox);
        head.setAlignment(Pos.BOTTOM_LEFT);

        HBox kpis = new HBox(32,
            kpi("Orders today", "64"),
            kpi("Average counter wait", "0:38"),
            kpi("Collected within 2 min of cooking", "97%"),
            kpi("No-shows", "0"));

        // ---- lanes
        VBox laneSched = new VBox(8), laneCook = new VBox(8), laneReady = new VBox(8);
        Label cSched = Fx.label("", "lane-count"), cCook = Fx.label("", "lane-count"), cReady = Fx.label("", "lane-count");
        HBox lanes = new HBox(14,
            lane("Scheduled", cSched, laneSched),
            lane("Cooking", cCook, laneCook),
            lane("Ready", cReady, laneReady));

        VBox page = new VBox(24, head, kpis, lanes);
        page.getStyleClass().add("page");

        AnimationTimer timer = new AnimationTimer() {
            double acc;
            @Override public void handle(long now) {
                double t = now / 1e9;
                if (last[0] < 0) last[0] = t;
                double dt = Math.min(0.1, t - last[0]);
                last[0] = t;
                clock[0] += dt * speed[0];
                acc += dt;
                if (acc < 0.25) return;
                acc = 0;
                // recycle collected tickets into new orders
                for (Ticket x : tickets) if (clock[0] > x.arrive + 240) x.arrive += 36 * 60;
                clockLabel.setText(Fx.time(base.plusSeconds((long) clock[0])));
                render(s, r, tickets, clock[0], laneSched, laneCook, laneReady, cSched, cCook, cReady);
            }
        };
        Fx.whileShowing(page, timer::start, timer::stop);
        render(s, r, tickets, clock[0], laneSched, laneCook, laneReady, cSched, cCook, cReady);

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private static void render(AppState s, Data.Restaurant r, List<Ticket> tickets, double now,
                               VBox sched, VBox cook, VBox ready, Label cs, Label cc, Label cr) {
        sched.getChildren().clear();
        cook.getChildren().clear();
        ready.getChildren().clear();
        List<Ticket> sorted = new ArrayList<>(tickets);
        sorted.sort(Comparator.comparingDouble(Ticket::fire));
        for (Ticket x : sorted) {
            if (now < x.fire()) sched.getChildren().add(scheduled(x, now));
            else if (now < x.arrive) cook.getChildren().add(cooking(x, now));
            else ready.getChildren().add(readyCard(x, now));
        }
        // the customer's real demo order, if it's from this kitchen
        Order o = s.order.get();
        if (o != null && !o.collected && o.r == r) {
            Node mine = myOrder(o);
            int st = o.stage();
            (st <= 1 ? sched : st <= 3 ? cook : ready).getChildren().add(0, mine);
        }
        cs.setText(String.valueOf(sched.getChildren().size()));
        cc.setText(String.valueOf(cook.getChildren().size()));
        cr.setText(String.valueOf(ready.getChildren().size()));
    }

    private static String mmss(double sec) {
        sec = Math.max(0, sec);
        return String.format("%d:%02d", (int) sec / 60, (int) sec % 60);
    }

    private static VBox ticketShell(String code, String who, String items, String status, String cls) {
        Label st = Fx.label(status, "status");
        st.getStyleClass().add(cls);
        HBox top = new HBox(8, Fx.label(who, "title-s"), Fx.label(code, "mono"), Fx.spacer(), st);
        top.setAlignment(Pos.CENTER_LEFT);
        Label it = Fx.label(items, "meta-ink");
        it.setWrapText(true);
        VBox v = new VBox(6, top, it);
        v.getStyleClass().add("ticket");
        return v;
    }

    private static Node scheduled(Ticket x, double now) {
        double until = x.fire() - now;
        boolean soon = until < 90;
        VBox v = ticketShell(x.code, x.who, x.items, "Start in " + mmss(until), soon ? "status-amber" : "status-muted");
        v.getChildren().add(Fx.label("Arrives in " + mmss(x.arrive - now) + " · " + x.mode + " · " + (int) (x.prep / 60) + " min cook", "meta"));
        return v;
    }

    private static Node cooking(Ticket x, double now) {
        double p = Fx.clamp((now - x.fire()) / x.prep);
        VBox v = ticketShell(x.code, x.who, x.items, "Done in " + mmss(x.arrive - now), "status-accent");
        ProgressBar pb = new ProgressBar(p);
        pb.getStyleClass().add("bar");
        pb.setMaxWidth(Double.MAX_VALUE);
        v.getChildren().addAll(pb, Fx.label("Customer arrives in " + mmss(x.arrive - now), "meta"));
        return v;
    }

    private static Node readyCard(Ticket x, double now) {
        VBox v = ticketShell(x.code, x.who, x.items, "Ready", "status-green");
        v.getChildren().add(Fx.label("Shelf B · customer at the counter", "meta"));
        return v;
    }

    private static Node myOrder(Order o) {
        int st = o.stage();
        String status = st <= 1 ? "Start " + Fx.time(o.at(o.cookStartSec())) : st <= 3 ? "Cooking" : "Ready";
        VBox v = ticketShell(o.code, "Your order", o.itemsSummary(), status,
            st <= 1 ? "status-muted" : st <= 3 ? "status-accent" : "status-green");
        v.getStyleClass().add("ticket-mine");
        String eta = o.travelProgress() < 1 ? "Arrives " + Fx.time(o.at(o.travelSec())) : "At the counter";
        Label note = Fx.label(eta + (o.delayMin > 0 ? " · re-timed " + o.delayMin + " min later for traffic" : ""), "meta");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        v.getChildren().add(note);
        return v;
    }

    private static Node lane(String title, Label count, VBox body) {
        HBox head = new HBox(8, Fx.label(title, "title"), count);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(12, head, body);
        v.getStyleClass().add("lane");
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setPrefWidth(300);
        v.setMinHeight(380);
        return v;
    }

    private static Node kpi(String label, String value) {
        return new VBox(2, Fx.label(value, "num-l"), Fx.label(label, "meta"));
    }

    private static Node right(Node n) {
        HBox h = new HBox(n);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }
}
