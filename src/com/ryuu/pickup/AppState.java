package com.ryuu.pickup;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared app state: cart, selection, active order, and the demo clock. */
final class AppState {
    static final class CartLine {
        final Data.MenuItem item;
        int qty;
        CartLine(Data.MenuItem item, int qty) { this.item = item; this.qty = qty; }
        int total() { return item.price() * qty; }
    }

    static final int DELIVERY_FEE = 49;
    static final int PACKAGING = 10;

    final App app;
    final ObservableList<CartLine> cart = FXCollections.observableArrayList();
    final IntegerProperty version = new SimpleIntegerProperty();
    final IntegerProperty cartCount = new SimpleIntegerProperty();
    final ObjectProperty<Data.Restaurant> restaurant = new SimpleObjectProperty<>(Data.RESTAURANTS.get(0));
    Data.Restaurant cartRestaurant;
    final StringProperty query = new SimpleStringProperty("");
    final BooleanProperty routeMode = new SimpleBooleanProperty(true);
    final IntegerProperty travelMin = new SimpleIntegerProperty(25);
    final StringProperty travelMode = new SimpleStringProperty("Car");
    final ObjectProperty<CommuteRoute.Place> from = new SimpleObjectProperty<>(CommuteRoute.place("Saibaba Colony"));
    final ObjectProperty<CommuteRoute.Place> to = new SimpleObjectProperty<>(CommuteRoute.place("Coimbatore Airport"));
    /** Bumped whenever the route (and so the restaurants on it) changes. */
    final IntegerProperty routeVersion = new SimpleIntegerProperty();
    final ObjectProperty<Order> order = new SimpleObjectProperty<>();
    final DoubleProperty speed = new SimpleDoubleProperty(30);

    int pickups = 26, minutesSaved = 222, feesSaved = 1274;

    AppState(App app) {
        this.app = app;
        Timeline clock = new Timeline(new KeyFrame(Duration.millis(100), e -> tick()));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        from.addListener(o -> requestRoute());
        to.addListener(o -> requestRoute());
        travelMode.addListener(o -> routeChanged());
        requestRoute();
    }

    void requestRoute() {
        CommuteRoute.request(from.get(), to.get(), this::routeChanged);
    }

    private void routeChanged() {
        travelMin.set(CommuteRoute.current.minutes(travelMode.get()));
        routeVersion.set(routeVersion.get() + 1);
    }

    void swapRoute() {
        CommuteRoute.Place f = from.get();
        from.set(to.get());
        to.set(f);
    }

    private void tick() {
        Order o = order.get();
        if (o == null || o.collected) return;
        o.sim = Math.min(o.endSec(), o.sim + 0.1 * speed.get());
        if (!o.cookingAnnounced && o.sim >= o.cookStartSec() && o.sim > 1) {
            o.cookingAnnounced = true;
            app.toast(o.r.name() + " has started cooking your order");
        }
        if (!o.readyAnnounced && o.sim >= o.readySec()) {
            o.readyAnnounced = true;
            app.toast("Your order is ready at counter " + o.counter);
        }
    }

    void add(Data.Restaurant r, Data.MenuItem item) {
        if (cartRestaurant != null && cartRestaurant != r && !cart.isEmpty()) {
            cart.clear();
            app.toast("Your bag now has items from " + r.name());
        }
        cartRestaurant = r;
        for (CartLine l : cart) {
            if (l.item == item) { l.qty++; changed(); return; }
        }
        cart.add(new CartLine(item, 1));
        changed();
    }

    void remove(Data.MenuItem item) {
        for (CartLine l : new ArrayList<>(cart)) {
            if (l.item == item) {
                l.qty--;
                if (l.qty <= 0) cart.remove(l);
            }
        }
        if (cart.isEmpty()) cartRestaurant = null;
        changed();
    }

    int qty(Data.Restaurant r, Data.MenuItem item) {
        if (cartRestaurant != r) return 0;
        for (CartLine l : cart) if (l.item == item) return l.qty;
        return 0;
    }

    int subtotal() { return cart.stream().mapToInt(CartLine::total).sum(); }
    int taxes() { return (int) Math.round(subtotal() * 0.05); }
    int total() { return subtotal() + taxes() + PACKAGING; }
    int maxPrep() { return cart.stream().mapToInt(l -> l.item.prepMin()).max().orElse(0); }

    private void changed() {
        cartCount.set(cart.stream().mapToInt(l -> l.qty).sum());
        version.set(version.get() + 1);
    }

    /** Minutes until the user reaches this restaurant. */
    int etaTo(Data.Restaurant r) {
        return Math.max(2, r.onRoute()
            ? (int) Math.round(r.routePos() * travelMin.get()) + r.detourMin()
            : (int) Math.ceil(r.distanceKm() * 3) + 3);
    }

    Order placeOrder(int arriveMin) {
        List<CartLine> copy = new ArrayList<>();
        for (CartLine l : cart) copy.add(new CartLine(l.item, l.qty));
        Random rnd = new Random();
        Order o = new Order(cartRestaurant, copy, total(), arriveMin, Math.max(1, maxPrep()),
            "RY-" + (1000 + rnd.nextInt(9000)), 1 + rnd.nextInt(6));
        cart.clear();
        cartRestaurant = null;
        changed();
        order.set(o);
        return o;
    }

    void demoOrder() {
        Data.Restaurant r = Data.byId("thalappakatti");
        cart.clear();
        cartRestaurant = r;
        cart.add(new CartLine(r.menu().get(0), 1));
        cart.add(new CartLine(r.menu().get(2), 1));
        changed();
        placeOrder(22);
    }

    void trafficDelay(int minutes) {
        Order o = order.get();
        if (o == null || !o.canResync()) return;
        o.delay(minutes);
        app.toast("Running " + minutes + " min late. The kitchen will start at " + Fx.time(o.at(o.cookStartSec())) + " instead"
            );
    }

    void collect() {
        Order o = order.get();
        if (o == null || o.collected) return;
        o.collected = true;
        pickups++;
        minutesSaved += 14;
        feesSaved += DELIVERY_FEE;
    }
}
