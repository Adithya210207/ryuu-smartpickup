package com.ryuu.pickup;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class RouteView {
    private RouteView() {}

    static Node create(AppState s) {
        ObjectProperty<Data.Restaurant> sel = new SimpleObjectProperty<>(s.restaurant.get());

        MapView map = new MapView();
        map.selected = sel::get;
        map.onPick = sel::set;
        map.progress = () -> {
            Order o = s.order.get();
            return o != null && !o.collected && o.r.onRoute() ? o.travelProgress() * o.r.routePos() : 0.0;
        };
        Fx.clip(map, 10);
        StackPane mapWrap = new StackPane(map);
        mapWrap.getStyleClass().add("map-wrap");
        HBox.setHgrow(mapWrap, Priority.ALWAYS);

        VBox side = new VBox(16);
        side.setPrefWidth(360);
        side.setMinWidth(360);
        VBox trip = new VBox(14);
        trip.getStyleClass().add("card");
        VBox detail = new VBox(10);
        detail.getStyleClass().add("card");
        VBox list = new VBox(2);
        Label listTitle = Fx.label("", "title");
        VBox listCard = new VBox(10, listTitle, list);
        listCard.getStyleClass().add("card");

        Runnable rebuild = () -> {
            LocalTime now = LocalTime.now();
            List<Data.Restaurant> stops = Data.RESTAURANTS.stream().filter(Data.Restaurant::onRoute)
                .sorted(Comparator.comparingDouble(Data.Restaurant::routePos)).toList();
            if (sel.get() == null)
                sel.set(stops.isEmpty() ? Data.RESTAURANTS.get(0) : stops.get(0));
            trip.getChildren().setAll(tripCard(s, now));
            detail.getChildren().setAll(detailCard(s, sel.get(), now));
            listTitle.setText(stops.isEmpty() ? "No restaurants on this route" : stops.size() + " on your way");
            list.getChildren().clear();
            if (stops.isEmpty()) {
                Label l = Fx.label("None of our partner restaurants are within a few minutes of this road. Try another destination, or pick one on the map.", "meta");
                l.setWrapText(true);
                list.getChildren().add(l);
            }
            for (int i = 0; i < stops.size(); i++) list.getChildren().add(stopRow(s, stops.get(i), i + 1, sel, now));
        };
        sel.addListener(o -> rebuild.run());
        Fx.listen(side, s.routeVersion, o -> rebuild.run());
        rebuild.run();
        side.getChildren().addAll(trip, detail, listCard);

        ScrollPane sideScroll = new ScrollPane(side);
        sideScroll.setFitToWidth(true);
        sideScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sideScroll.setMinWidth(372);
        sideScroll.setPrefWidth(372);

        Label sub = Fx.label("", "body");
        Runnable subText = () -> {
            CommuteRoute r = CommuteRoute.current;
            sub.setText(r.from.name() + " to " + r.to.name() + " · " + String.format(Locale.ENGLISH, "%.1f km", r.km())
                + " · about " + s.travelMin.get() + " min by " + s.travelMode.get().toLowerCase(Locale.ROOT));
        };
        Fx.listen(sub, s.routeVersion, o -> subText.run());
        subText.run();
        VBox head = new VBox(6, Fx.label("Your route", "display-l"), sub);
        HBox body = new HBox(20, mapWrap, sideScroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox page = new VBox(20, head, body);
        page.getStyleClass().add("page");
        return page;
    }

    private static Node tripCard(AppState s, LocalTime now) {
        CommuteRoute r = CommuteRoute.current;
        Node from = endpoint(r.from.name(), r.from.area(), "Leave " + Fx.time(now));
        Node to = endpoint(r.to.name(), r.to.area(), "Arrive " + Fx.time(now.plusMinutes(s.travelMin.get())));
        HBox modes = new HBox(2);
        modes.getStyleClass().add("segmented");
        ToggleGroup tg = new ToggleGroup();
        for (String m : new String[]{"Car", "Bike", "Bus"}) {
            ToggleButton b = new ToggleButton(m);
            b.getStyleClass().add("seg-btn");
            b.setToggleGroup(tg);
            b.setSelected(m.equals(s.travelMode.get()));
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setOnAction(e -> { b.setSelected(true); s.travelMode.set(m); });
            modes.getChildren().add(b);
        }
        Region hl = new Region();
        hl.getStyleClass().add("hairline");
        return new VBox(12, from, hl, to, modes);
    }

    private static Node endpoint(String title, String sub, String time) {
        HBox h = new HBox(10, new VBox(0, Fx.label(title, "title"), Fx.label(sub, "meta")), Fx.spacer(), Fx.label(time, "meta-ink"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Node stopRow(AppState s, Data.Restaurant r, int idx, ObjectProperty<Data.Restaurant> sel, LocalTime now) {
        Label index = Fx.label(String.valueOf(idx), "stop-index");
        VBox txt = new VBox(0, Fx.label(r.name(), "title-s"), Fx.label(r.area() + " · " + r.cuisine(), "meta"));
        HBox.setHgrow(txt, Priority.ALWAYS);
        Label detour = Fx.label(r.detourMin() == 0 ? "On the way" : "+" + r.detourMin() + " min", "meta");
        if (r.detourMin() == 0) detour.getStyleClass().add("positive");
        VBox right = new VBox(0, Fx.label(Fx.time(now.plusMinutes(s.etaTo(r))), "meta-ink"), detour);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(12, index, txt, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stop-row");
        row.pseudoClassStateChanged(Fx.ACTIVE, r == sel.get());
        row.setOnMouseClicked(e -> sel.set(r));
        return row;
    }

    private static Node detailCard(AppState s, Data.Restaurant r, LocalTime now) {
        int eta = s.etaTo(r);
        boolean fits = r.prepMin() <= eta;
        Photo photo = Photo.of(r.cover(), r.name(), r.c1(), r.art(), 8, 0).fixed(64, 64);
        Label name = Fx.label(r.name(), "display-s");
        name.setWrapText(true);
        VBox txt = new VBox(2, name, Fx.label(r.area() + " · " + r.cuisine(), "meta"));
        HBox.setHgrow(txt, Priority.ALWAYS);
        HBox top = new HBox(14, photo, txt);
        top.setAlignment(Pos.CENTER_LEFT);
        String where = r.onRoute()
            ? (r.detourMin() == 0 ? "On your way." : r.detourMin() + " min off your route.")
            : "Not on this route · " + r.distanceKm() + " km from " + CommuteRoute.current.from.name() + ".";
        Label line = Fx.label(where + (fits
            ? " You reach it at " + Fx.time(now.plusMinutes(eta)) + ". Order now and it will be ready then."
            : " It needs " + r.prepMin() + " min, so order now for " + Fx.time(now.plusMinutes(r.prepMin())) + "."), "body");
        line.setWrapText(true);
        Label addr = Fx.label(r.address(), "meta");
        addr.setWrapText(true);
        Button go = new Button("View menu");
        go.getStyleClass().add("btn-primary");
        go.setMaxWidth(Double.MAX_VALUE);
        go.setOnAction(e -> { s.restaurant.set(r); s.app.navigate("menu", true); });
        return new VBox(12, top, line, addr, go);
    }
}
