package com.ryuu.pickup;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class DiscoverView {
    private DiscoverView() {}

    static Node create(AppState s) {
        // ---- header
        Label title = Fx.label("Pick up on your way", "display-l");
        Label sub = Fx.label("Ready when you arrive. No queue, no delivery fee.", "body");
        ToggleGroup mg = new ToggleGroup();
        ToggleButton route = new ToggleButton("Along my route");
        ToggleButton near = new ToggleButton("Nearby");
        for (ToggleButton b : List.of(route, near)) { b.getStyleClass().add("seg-btn"); b.setToggleGroup(mg); }
        route.setOnAction(e -> { route.setSelected(true); s.routeMode.set(true); });
        near.setOnAction(e -> { near.setSelected(true); s.routeMode.set(false); });
        HBox seg = new HBox(2, route, near);
        seg.getStyleClass().add("segmented");
        seg.setMaxHeight(Region.USE_PREF_SIZE);
        HBox header = new HBox(16, new VBox(6, title, sub), Fx.spacer(), seg);
        header.setAlignment(Pos.BOTTOM_LEFT);

        // ---- commute strip
        VBox commuteBox = new VBox();

        // ---- filters
        StringProperty cat = new SimpleStringProperty("All");
        HBox cats = new HBox(8);
        ToggleGroup tg = new ToggleGroup();
        for (String c : Data.CATEGORIES) {
            ToggleButton b = new ToggleButton(c);
            b.getStyleClass().add("chip-filter");
            b.setToggleGroup(tg);
            if (c.equals("All")) b.setSelected(true);
            b.setOnAction(e -> { if (!b.isSelected()) b.setSelected(true); cat.set(c); });
            cats.getChildren().add(b);
        }

        Label count = Fx.label("", "meta");
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(32);

        VBox page = new VBox(24, header, commuteBox, cats, count, grid);
        page.getStyleClass().add("page");

        int[] cols = {0};
        Runnable refresh = () -> {
            route.setSelected(s.routeMode.get());
            near.setSelected(!s.routeMode.get());
            boolean rm = s.routeMode.get();
            commuteBox.getChildren().setAll(rm ? List.of(commuteStrip(s)) : List.of());
            String q = s.query.get() == null ? "" : s.query.get().trim().toLowerCase(Locale.ROOT);
            List<Data.Restaurant> list = Data.RESTAURANTS.stream()
                .filter(r -> !q.isEmpty() || (rm ? r.onRoute() : r.distanceKm() <= 4.0))
                .filter(r -> cat.get().equals("All") || r.cats().contains(cat.get()))
                .filter(r -> q.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(q)
                    || r.cuisine().toLowerCase(Locale.ROOT).contains(q)
                    || r.menu().stream().anyMatch(m -> m.name().toLowerCase(Locale.ROOT).contains(q)))
                .sorted(!q.isEmpty() ? Comparator.comparing((Data.Restaurant r) -> r.onRoute() ? 0 : 1).thenComparingDouble(Data.Restaurant::distanceKm)
                    : rm ? Comparator.comparingDouble(Data.Restaurant::routePos)
                           : Comparator.comparingDouble(Data.Restaurant::distanceKm))
                .limit(rm ? 200 : 60).toList();
            count.setText(!q.isEmpty() ? list.size() + " results for “" + s.query.get().trim() + "”"
                : list.size() + (rm ? " restaurants on your way, in the order you pass them" : " restaurants within 4 km of " + CommuteRoute.current.from.name() + ", closest first"));
            grid.getChildren().clear();
            grid.getColumnConstraints().clear();
            int n = Math.max(1, cols[0]);
            for (int i = 0; i < n; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(100.0 / n);
                grid.getColumnConstraints().add(cc);
            }
            if (list.isEmpty()) grid.add(Fx.label("Nothing matches. Try another category.", "body"), 0, 0);
            java.util.Set<String> used = new java.util.HashSet<>();
            for (int i = 0; i < list.size(); i++) grid.add(card(s, list.get(i), pickCover(list.get(i), used, q)), i % n, i / n);
        };
        page.widthProperty().addListener((o, a, b) -> {
            int n = b.doubleValue() > 1180 ? 4 : b.doubleValue() > 820 ? 3 : 2;
            if (n != cols[0]) { cols[0] = n; refresh.run(); }
        });
        cat.addListener(o -> refresh.run());
        Fx.listen(page, s.routeMode, o -> refresh.run());
        Fx.listen(page, s.query, o -> refresh.run());
        Fx.listen(page, s.routeVersion, o -> refresh.run());
        cols[0] = 3;
        refresh.run();

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    // ---------------------------------------------------------------- commute strip

    private static Node commuteStrip(AppState s) {
        LocalTime now = LocalTime.now();
        VBox left = new VBox(2, Fx.label("Your commute", "caption"),
            Fx.label(CommuteRoute.current.from.name() + " → " + CommuteRoute.current.to.name(), "title"),
            Fx.label("Leave " + Fx.time(now) + " · arrive " + Fx.time(now.plusMinutes(s.travelMin.get())), "meta"));
        left.setMinWidth(200);

        Canvas line = new Canvas(10, 44);
        StackPane lineWrap = new StackPane(line);
        lineWrap.setMinWidth(100);
        HBox.setHgrow(lineWrap, Priority.ALWAYS);
        lineWrap.widthProperty().addListener((o, a, b) -> {
            line.setWidth(b.doubleValue());
            paintStrip(line.getGraphicsContext2D(), b.doubleValue(), 44);
        });

        Button open = new Button("View route");
        open.getStyleClass().add("btn-secondary");
        open.setOnAction(e -> s.app.navigate("route"));

        HBox strip = new HBox(28, left, lineWrap, open);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("commute");
        return strip;
    }

    private static void paintStrip(GraphicsContext g, double w, double h) {
        g.clearRect(0, 0, w, h);
        double x0 = 8, x1 = w - 8, y = 16;
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(Color.web("#D5CEC3"));
        g.setLineWidth(2);
        g.strokeLine(x0, y, x1, y);
        g.setFill(Color.web("#1A1814"));
        g.fillOval(x0 - 4, y - 4, 8, 8);
        g.fillRect(x1 - 4, y - 4, 8, 8);
        Font f = Font.font("Segoe UI", 10.5);
        g.setFont(f);
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.CENTER);
        for (Data.Restaurant r : Data.RESTAURANTS) {
            if (!r.onRoute()) continue;
            double x = x0 + (x1 - x0) * r.routePos();
            // a quiet tick per restaurant; where they cluster, the ticks build up into a darker band
            g.setStroke(Color.rgb(200, 70, 26, 0.55));
            g.setLineWidth(1.5);
            g.strokeLine(x, y - 6, x, y + 6);
        }
        g.setFill(Color.web("#8E877C"));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(CommuteRoute.current.from.name(), x0 - 4, y + 18);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText(CommuteRoute.current.to.name(), x1 + 4, y + 18);
        g.setTextAlign(TextAlignment.CENTER);
        long n = Data.RESTAURANTS.stream().filter(Data.Restaurant::onRoute).count();
        g.fillText(n == 0 ? "No restaurants on this route yet" : n + (n == 1 ? " restaurant" : " restaurants") + " on the way", (x0 + x1) / 2, y + 18);
    }

    // ---------------------------------------------------------------- cards

    static HBox metaLine(String... parts) {
        HBox h = new HBox(6);
        h.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) h.getChildren().add(Fx.label("·", "meta"));
            String p = parts[i];
            boolean good = p.startsWith("!");
            Label l = Fx.label(good ? p.substring(1) : p, "meta");
            if (good) l.getStyleClass().add("positive");
            h.getChildren().add(l);
        }
        return h;
    }

    /** The searched-for dish if pictured, else the restaurant cover, else another of its dishes; never a repeat on screen. */
    static String pickCover(Data.Restaurant r, java.util.Set<String> used, String q) {
        java.util.List<String> options = new java.util.ArrayList<>();
        if (!q.isEmpty()) {   // searching for a dish: show that dish, repeats are expected
            for (Data.MenuItem m : r.menu())
                if (m.photo() != null && m.name().toLowerCase(Locale.ROOT).contains(q) && FoodArt.image(m.photo()) != null) return m.photo();
        }
        if (r.cover() != null) options.add(r.cover());
        r.menu().stream().map(Data.MenuItem::photo).filter(java.util.Objects::nonNull).forEach(options::add);
        for (String o : options) if (FoodArt.image(o) != null && used.add(o)) return o;
        return null;
    }

    static Node card(AppState s, Data.Restaurant r, String cover) {
        Photo photo = Photo.of(cover, r.name(), r.c1(), r.art(), 10, 0.66);
        photo.getStyleClass().add("r-photo");

        Label name = Fx.label(r.name(), "r-name");
        HBox rating = new HBox(4, Fx.icon(Icons.STAR, 11, "icon-star"),
            Fx.label(r.rating() > 0 ? String.format(Locale.ENGLISH, "%.1f", r.rating()) : "New", "title-s"));
        rating.setAlignment(Pos.CENTER_LEFT);
        rating.setMinWidth(Region.USE_PREF_SIZE);
        name.setMinWidth(0);
        HBox titleRow = new HBox(8, name, Fx.spacer(), rating);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label cuisine = Fx.label(r.cuisine(), "meta-ink");
        String where = !r.onRoute() ? r.distanceKm() + " km away"
            : r.detourMin() == 0 ? "!On your way" : "+" + r.detourMin() + " min detour";
        HBox meta = metaLine("Ready in " + r.prepMin() + " min", where, "from " + Fx.money(r.fromPrice()));

        VBox card = new VBox(12, photo, new VBox(4, titleRow, cuisine, meta));
        card.getStyleClass().add("r-card");
        card.setOnMouseEntered(e -> card.pseudoClassStateChanged(Fx.ACTIVE, true));
        card.setOnMouseExited(e -> card.pseudoClassStateChanged(Fx.ACTIVE, false));
        card.setOnMouseClicked(e -> { s.restaurant.set(r); s.app.navigate("menu", true); });
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(card, true);
        return card;
    }
}
