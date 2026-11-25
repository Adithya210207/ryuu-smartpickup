package com.ryuu.pickup;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.time.LocalTime;
import java.util.Locale;

final class MenuView {
    private MenuView() {}

    static Node create(AppState s) {
        Data.Restaurant r = s.restaurant.get();
        int eta = s.etaTo(r);

        Button back = new Button("All restaurants", Fx.icon(Icons.ARROW, 12, "icon-muted"));
        back.getStyleClass().add("btn-text");
        back.getGraphic().setRotate(180);
        back.setOnAction(e -> s.app.navigate("discover"));

        Photo cover = Photo.of(r.cover(), r.name(), r.c1(), r.art(), 10, 0.28);
        Label name = Fx.label(r.name(), "display-l");
        HBox rating = new HBox(4, Fx.icon(Icons.STAR, 11, "icon-star"), Fx.label(String.valueOf(r.rating()), "title-s"),
            Fx.label("(" + String.format(Locale.ENGLISH, "%,d", r.reviews()) + ")", "meta"));
        rating.setAlignment(Pos.CENTER_LEFT);
        HBox meta = new HBox(10, Fx.label(r.cuisine(), "meta-ink"), Fx.label("·", "meta"), rating,
            Fx.label("·", "meta"), Fx.label(r.distanceKm() + " km", "meta"));
        meta.setAlignment(Pos.CENTER_LEFT);
        Label reach = Fx.label("You reach it in about " + eta + " min · Pickup, no delivery fee", "meta");

        VBox list = new VBox();
        for (Data.MenuItem m : r.menu()) list.getChildren().add(row(s, r, m, eta));

        VBox left = new VBox(18, back, cover, new VBox(6, name, meta, reach), sectionTitle("Menu"), list);
        left.setPadding(new Insets(0, 8, 24, 0));
        ScrollPane sp = new ScrollPane(left);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(sp, Priority.ALWAYS);

        VBox bag = new VBox();
        bag.getStyleClass().add("bag");
        bag.setPrefWidth(360);
        bag.setMinWidth(360);
        bag.setMaxHeight(Region.USE_PREF_SIZE);
        IntegerProperty arrive = new SimpleIntegerProperty(Math.max(5, Math.min(60,
            s.cartRestaurant != null ? s.etaTo(s.cartRestaurant) : eta)));
        BooleanProperty autoSync = new SimpleBooleanProperty(true);
        Runnable rebuild = () -> buildBag(s, bag, arrive, autoSync);
        rebuild.run();
        arrive.addListener(o -> rebuild.run());
        autoSync.addListener(o -> {
            if (autoSync.get() && s.cartRestaurant != null) arrive.set(Math.max(5, Math.min(60, s.etaTo(s.cartRestaurant))));
            rebuild.run();
        });

        VBox bagCol = new VBox(bag);
        HBox page = new HBox(32, sp, bagCol);
        page.getStyleClass().add("page");
        Fx.listen(page, s.version, o -> rebuild.run());
        return page;
    }

    private static Node sectionTitle(String t) {
        Label l = Fx.label(t, "display-s");
        VBox v = new VBox(l);
        v.setPadding(new Insets(10, 0, 0, 0));
        return v;
    }

    // ---------------------------------------------------------------- menu rows

    private static Node row(AppState s, Data.Restaurant r, Data.MenuItem m, int eta) {
        Region veg = new Region();
        veg.getStyleClass().addAll("veg", m.veg() ? "veg-yes" : "veg-no");
        HBox titleRow = new HBox(8, veg, Fx.label(m.name(), "title"));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label desc = Fx.label(m.desc(), "meta-ink");
        desc.setVisible(!m.desc().isEmpty());
        desc.setManaged(!m.desc().isEmpty());
        desc.setWrapText(true);
        desc.setMaxWidth(520);

        HBox meta = new HBox(10, Fx.label(Fx.money(m.price()), "price"), Fx.label("·", "meta"),
            Fx.label(m.prepMin() + " min", "meta"));
        meta.setAlignment(Pos.CENTER_LEFT);
        if (m.tag() != null) meta.getChildren().addAll(Fx.label("·", "meta"), accent(m.tag()));
        if (m.prepMin() > eta) {
            Label w = Fx.label("Ready " + (m.prepMin() - eta) + " min after you arrive", "meta");
            w.getStyleClass().add("warning");
            meta.getChildren().addAll(Fx.label("·", "meta"), w);
        }
        VBox info = new VBox(6, titleRow, desc, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        Node photo = m.photo() != null && FoodArt.image(m.photo()) != null
            ? new Photo(m.photo(), r.c1(), m.art(), 8, 0).fixed(104, 88) : new Region();
        HBox controls = new HBox();
        controls.setAlignment(Pos.CENTER);
        VBox right = new VBox(8, photo, controls);
        right.setMinWidth(104);
        right.setAlignment(Pos.TOP_CENTER);

        HBox row = new HBox(24, info, right);
        row.getStyleClass().add("menu-row");

        Runnable upd = () -> {
            int q = s.qty(r, m);
            if (q == 0) {
                Button add = new Button("Add");
                add.getStyleClass().add("btn-add");
                add.setOnAction(e -> s.add(r, m));
                controls.getChildren().setAll(add);
            } else {
                Button minus = new Button(null, Fx.icon(Icons.MINUS, 11, "icon-ink"));
                Button plus = new Button(null, Fx.icon(Icons.PLUS, 11, "icon-ink"));
                minus.getStyleClass().add("step-btn");
                plus.getStyleClass().add("step-btn");
                minus.setOnAction(e -> s.remove(m));
                plus.setOnAction(e -> s.add(r, m));
                HBox stepper = new HBox(2, minus, Fx.label(String.valueOf(q), "step-qty"), plus);
                stepper.setAlignment(Pos.CENTER);
                stepper.getStyleClass().add("stepper");
                controls.getChildren().setAll(stepper);
            }
        };
        upd.run();
        Fx.listen(row, s.version, o -> upd.run());
        return row;
    }

    private static Label accent(String t) {
        Label l = Fx.label(t, "meta");
        l.getStyleClass().add("accent-text");
        return l;
    }

    // ---------------------------------------------------------------- bag

    private static void buildBag(AppState s, VBox bag, IntegerProperty arrive, BooleanProperty autoSync) {
        bag.getChildren().clear();
        Data.Restaurant cr = s.cartRestaurant;

        VBox head = new VBox(2, Fx.label("Your order", "display-s"),
            Fx.label(cr == null ? "Nothing added yet" : cr.name(), "meta"));
        head.getStyleClass().add("bag-section");
        bag.getChildren().add(head);

        if (cr == null || s.cart.isEmpty()) {
            Label hint = Fx.label("Add a dish and we'll time it to arrive with you.", "body");
            hint.setWrapText(true);
            VBox empty = new VBox(hint);
            empty.setPadding(new Insets(18, 20, 22, 20));
            bag.getChildren().add(empty);
            return;
        }

        VBox lines = new VBox(10);
        for (AppState.CartLine l : s.cart) {
            HBox lr = new HBox(10, Fx.label(l.qty + "×", "meta"), Fx.label(l.item.name(), "meta-ink"),
                Fx.spacer(), Fx.label(Fx.money(l.total()), "meta-ink"));
            lr.setAlignment(Pos.CENTER_LEFT);
            lines.getChildren().add(lr);
        }
        lines.getStyleClass().add("bag-section");

        // ---- pickup timing
        int prep = s.maxPrep();
        int a = arrive.get();
        int ready = Math.max(a, prep);
        LocalTime now = LocalTime.now();
        Label pickupTime = Fx.label(Fx.time(now.plusMinutes(ready)), "display-m");
        Label explain = Fx.label(a >= prep
            ? "The kitchen starts at " + Fx.time(now.plusMinutes(a - prep)) + ", so it's ready as you arrive."
            : "This order takes " + prep + " min, so the earliest it can be ready is " + Fx.time(now.plusMinutes(prep)) + ".", "meta");
        explain.setWrapText(true);
        if (a < prep) explain.getStyleClass().add("warning");

        HBox syncRow = new HBox(10, Fx.label("Match my live ETA", "meta-ink"), Fx.spacer(), Fx.toggleSwitch(autoSync));
        syncRow.setAlignment(Pos.CENTER_LEFT);
        VBox pickup = new VBox(8, Fx.label("Pickup", "caption"), pickupTime, explain, syncRow);
        if (!autoSync.get()) {
            Button earlier = new Button(null, Fx.icon(Icons.MINUS, 11, "icon-ink"));
            Button later = new Button(null, Fx.icon(Icons.PLUS, 11, "icon-ink"));
            earlier.getStyleClass().add("step-btn");
            later.getStyleClass().add("step-btn");
            earlier.setDisable(a - 5 < 5);
            earlier.setOnAction(e -> arrive.set(Math.max(5, a - 5)));
            later.setOnAction(e -> arrive.set(Math.min(90, a + 5)));
            HBox stepper = new HBox(2, earlier, Fx.label("5 min", "meta"), later);
            stepper.setAlignment(Pos.CENTER);
            stepper.getStyleClass().add("stepper");
            HBox adj = new HBox(10, Fx.label("Arriving in " + a + " min", "meta-ink"), Fx.spacer(), stepper);
            adj.setAlignment(Pos.CENTER_LEFT);
            pickup.getChildren().add(adj);
        }
        pickup.getStyleClass().add("bag-section");

        // ---- totals
        Text strike = new Text(Fx.money(AppState.DELIVERY_FEE));
        strike.getStyleClass().add("strike");
        HBox fee = new HBox(8, strike, Fx.label(Fx.money(0), "meta-ink"));
        fee.setAlignment(Pos.CENTER_RIGHT);
        VBox totals = new VBox(8,
            billRow("Subtotal", Fx.label(Fx.money(s.subtotal()), "meta-ink")),
            billRow("Taxes", Fx.label(Fx.money(s.taxes()), "meta-ink")),
            billRow("Packaging", Fx.label(Fx.money(AppState.PACKAGING), "meta-ink")),
            billRow("Delivery fee", fee),
            billRow("Total", Fx.label(Fx.money(s.total()), "total"), "total"));
        totals.getStyleClass().add("bag-section");

        Button pay = new Button("Place order · " + Fx.money(s.total()));
        pay.getStyleClass().addAll("btn-primary", "btn-lg");
        pay.setMaxWidth(Double.MAX_VALUE);
        pay.setOnAction(e -> checkout(s, ready, prep));
        VBox footer = new VBox(8, pay, centered(Fx.label("Paid with UPI · Pickup at the counter", "meta")));
        footer.getStyleClass().add("bag-footer");

        bag.getChildren().addAll(lines, pickup, totals, footer);
    }

    private static Node centered(Node n) {
        HBox h = new HBox(n);
        h.setAlignment(Pos.CENTER);
        return h;
    }

    private static HBox billRow(String l, Node right, String... cls) {
        Label left = Fx.label(l, cls.length > 0 ? cls[0] : "meta");
        HBox h = new HBox(left, Fx.spacer(), right);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---------------------------------------------------------------- checkout

    static void checkout(AppState s, int readyMin, int prep) {
        StackPane scrim = new StackPane();
        scrim.getStyleClass().add("scrim");
        VBox sheet = new VBox(16);
        sheet.getStyleClass().add("sheet");
        sheet.setPrefWidth(420);
        sheet.setMaxSize(420, Region.USE_PREF_SIZE);
        scrim.getChildren().add(sheet);
        scrim.setOnMouseClicked(e -> { if (e.getTarget() == scrim) s.app.removeOverlay(scrim); });

        LocalTime now = LocalTime.now();
        Data.Restaurant r = s.cartRestaurant;
        int total = s.total();

        Label title = Fx.label("Confirm order", "display-m");
        VBox rows = new VBox(10,
            billRow("Restaurant", Fx.label(r.name(), "meta-ink")),
            billRow("Pickup", Fx.label(Fx.time(now.plusMinutes(readyMin)) + " · counter", "meta-ink")),
            billRow("Payment", Fx.label("UPI · default", "meta-ink")),
            billRow("Total", Fx.label(Fx.money(total), "total"), "total"));
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> s.app.removeOverlay(scrim));
        Button pay = new Button("Pay " + Fx.money(total));
        pay.getStyleClass().add("btn-primary");
        HBox actions = new HBox(10, Fx.spacer(), cancel, pay);
        sheet.getChildren().addAll(title, rows, actions);

        pay.setOnAction(e -> {
            Order o = s.placeOrder(readyMin);
            StackPane mark = new StackPane(Fx.icon(Icons.CHECK, 20, "icon-green"));
            mark.getStyleClass().add("success-mark");
            Label t = Fx.label("Order placed", "display-m");
            Label msg = Fx.label(o.r.name() + " will start cooking at " + Fx.time(o.at(o.cookStartSec()))
                + ". It will be ready at " + Fx.time(o.at(o.readySec())) + ", when you arrive.", "body");
            msg.setWrapText(true);
            Button track = new Button("Track order");
            track.getStyleClass().add("btn-primary");
            track.setOnAction(ev -> { s.app.removeOverlay(scrim); s.app.navigate("tracker", true); });
            HBox act = new HBox(Fx.spacer(), track);
            sheet.getChildren().setAll(mark, t, msg, act);
        });
        s.app.overlay(scrim);
    }
}
