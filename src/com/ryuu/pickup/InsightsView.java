package com.ryuu.pickup;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

final class InsightsView {
    private InsightsView() {}

    static Node create(AppState s) {
        VBox head = new VBox(6, Fx.label("Activity", "display-l"), Fx.label("November", "body"));

        // ---- headline numbers
        HBox stats = new HBox(0,
            first(stat(s.minutesSaved / 60 + " h " + s.minutesSaved % 60 + " m", "Time saved", "+38 min vs October")),
            vline(),
            stat(Fx.money(s.feesSaved), "Delivery fees avoided", null),
            vline(),
            stat(String.valueOf(s.pickups), "Pickups", null),
            vline(),
            stat("0:42", "Average wait at the counter", null));
        stats.getStyleClass().add("card");

        // ---- charts
        CategoryAxis x = new CategoryAxis(FXCollections.observableArrayList("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8"));
        x.setAnimated(false);
        NumberAxis y = new NumberAxis(0, 70, 10);
        y.setAnimated(false);
        y.setTickLabelFormatter(new NumberAxis.DefaultFormatter(y, null, " m"));
        AreaChart<String, Number> area = new AreaChart<>(x, y);
        area.setLegendVisible(false);
        area.setAnimated(false);
        area.setPrefHeight(260);
        area.setVerticalGridLinesVisible(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        int[] mins = {18, 26, 31, 29, 42, 47, 55, 64};
        for (int i = 0; i < mins.length; i++) series.getData().add(new XYChart.Data<>("W" + (i + 1), mins[i]));
        area.getData().add(series);
        VBox areaCard = new VBox(10, Fx.label("Minutes saved per week", "title"), area);
        areaCard.getStyleClass().add("card");
        HBox.setHgrow(areaCard, Priority.ALWAYS);

        CategoryAxis bx = new CategoryAxis(FXCollections.observableArrayList("Breakfast", "Lunch", "Snacks", "Dinner"));
        bx.setAnimated(false);
        NumberAxis by = new NumberAxis(0, 14, 2);
        by.setAnimated(false);
        BarChart<String, Number> bars = new BarChart<>(bx, by);
        bars.setLegendVisible(false);
        bars.setAnimated(false);
        bars.setPrefHeight(260);
        bars.setCategoryGap(36);
        bars.setVerticalGridLinesVisible(false);
        XYChart.Series<String, Number> bs = new XYChart.Series<>();
        String[] meals = {"Breakfast", "Lunch", "Snacks", "Dinner"};
        int[] vals = {12, 6, 3, 5};
        for (int i = 0; i < meals.length; i++) bs.getData().add(new XYChart.Data<>(meals[i], vals[i]));
        bars.getData().add(bs);
        VBox barCard = new VBox(10, Fx.label("Pickups by meal", "title"), bars);
        barCard.getStyleClass().add("card");
        barCard.setPrefWidth(400);
        barCard.setMinWidth(360);
        HBox charts = new HBox(16, areaCard, barCard);

        // ---- recent orders
        VBox recent = new VBox(0, Fx.label("Recent orders", "title"));
        Object[][] rows = {
            {Data.byId("annapoorna"), "Today, 8:52 AM", "0:35", 280},
            {Data.byId("anandhaas"), "Yesterday, 9:04 AM", "0:20", 321},
            {Data.byId("thalappakatti"), "Mon, 7:41 PM", "0:48", 694},
            {Data.byId("cafeinc"), "Fri, 1:12 PM", "0:31", 528},
            {Data.byId("pazhamudhir"), "Thu, 8:49 AM", "0:40", 135}};
        for (Object[] r : rows) {
            Data.Restaurant rr = (Data.Restaurant) r[0];
            Photo p = new Photo(rr.cover(), rr.c1(), rr.art(), 6, 0).fixed(40, 40);
            HBox row = new HBox(14, p, new VBox(0, Fx.label(rr.name(), "title-s"), Fx.label((String) r[1], "meta")),
                Fx.spacer(), new VBox(0, right(Fx.label("Waited " + r[2], "meta-ink")), right(Fx.label(Fx.money((int) r[3]), "meta"))));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("menu-row");
            recent.getChildren().add(row);
        }
        recent.getStyleClass().add("card");

        VBox page = new VBox(20, head, stats, charts, recent);
        page.getStyleClass().add("page");
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private static Node right(Label l) {
        HBox h = new HBox(l);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private static Node stat(String value, String label, String delta) {
        VBox v = new VBox(4, Fx.label(value, "num-l"), Fx.label(label, "meta"));
        if (delta != null) {
            Label d = Fx.label(delta, "meta");
            d.getStyleClass().add("positive");
            v.getChildren().add(d);
        }
        v.setPadding(new javafx.geometry.Insets(0, 24, 0, 24));
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private static Node first(Node n) {
        ((VBox) n).setPadding(new javafx.geometry.Insets(0, 24, 0, 0));
        return n;
    }

    private static Node vline() {
        Region r = new Region();
        r.getStyleClass().add("vline");
        return r;
    }
}
