package com.ryuu.pickup;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Dev helper: run with -Dryuu.snap=<dir> to walk every screen and save PNGs. */
final class Snapshots {
    private Snapshots() {}

    static void run(App app, AppState s, Scene scene, String dir) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.err.println("SNAP-ERROR " + e); e.printStackTrace(); });
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> save(scene, dir, "0-intro-1"));
        steps.add(() -> { ((Runnable) app.intro.getUserData()).run(); ((Runnable) app.intro.getUserData()).run(); });
        steps.add(() -> save(scene, dir, "0-intro-3"));
        steps.add(() -> app.removeOverlay(app.intro));
        steps.add(() -> save(scene, dir, "1-discover"));
        steps.add(() -> s.query.set("ghee roast"));
        steps.add(() -> save(scene, dir, "1b-search"));
        steps.add(() -> { s.query.set(""); s.routeMode.set(false); });
        steps.add(() -> save(scene, dir, "1c-nearby"));
        steps.add(() -> s.routeMode.set(true));
        steps.add(() -> { s.restaurant.set(Data.byId("hotel-maa-race-course")); app.navigate("menu", true); });
        steps.add(() -> save(scene, dir, "3c-menu-real"));
        steps.add(() -> { s.restaurant.set(Data.byId("night-lion-by-madhampatty-pakashala-peelamedu")); app.navigate("menu", true); });
        steps.add(() -> save(scene, dir, "3d-menu-real"));
        steps.add(() -> app.navigate("route"));
        steps.add(() -> save(scene, dir, "2-route"));
        steps.add(() -> { MapView m = find(scene.getRoot()); if (m != null) m.zoomTo(11.0130, 76.9560, 16); });
        steps.add(() -> save(scene, dir, "2c-route-zoom"));
        steps.add(() -> { MapView m = find(scene.getRoot()); if (m != null) m.zoomTo(10.85, 76.97, 9); });
        steps.add(() -> save(scene, dir, "2d-district"));
        steps.add(() -> { MapView m = find(scene.getRoot()); if (m != null) m.zoomTo(11.29, 76.94, 14); });
        steps.add(() -> save(scene, dir, "2e-mettupalayam"));
        steps.add(() -> s.to.set(CommuteRoute.place("Pollachi")));
        steps.add(() -> save(scene, dir, "2f-route-pollachi"));
        steps.add(() -> s.to.set(CommuteRoute.place("Coimbatore Airport")));
        steps.add(() -> s.from.set(CommuteRoute.place("Vadavalli")));
        steps.add(() -> save(scene, dir, "2b-route-vadavalli"));
        steps.add(() -> s.from.set(CommuteRoute.place("Saibaba Colony")));
        steps.add(() -> {
            Data.Restaurant r = Data.byId("thalappakatti");
            s.restaurant.set(r);
            s.add(r, r.menu().get(0));
            s.add(r, r.menu().get(3));
            s.add(r, r.menu().get(3));
            app.navigate("menu", true);
        });
        steps.add(() -> save(scene, dir, "3-menu"));
        steps.add(() -> MenuView.checkout(s, 23, 18));
        steps.add(() -> save(scene, dir, "3b-checkout"));
        steps.add(app::removeTopOverlay);
        steps.add(() -> { s.placeOrder(22); s.order.get().sim = 60; s.trafficDelay(6); app.navigate("tracker", true); });
        steps.add(() -> save(scene, dir, "4-tracker-resync"));
        steps.add(() -> app.navigate("kitchen"));
        steps.add(() -> save(scene, dir, "7-kitchen"));
        steps.add(() -> { s.order.get().sim = 900; app.navigate("tracker", true); });
        steps.add(() -> save(scene, dir, "4-tracker"));
        steps.add(() -> { s.order.get().sim = s.order.get().endSec(); s.collect(); app.navigate("tracker", true); });
        steps.add(() -> save(scene, dir, "6-done"));
        steps.add(() -> app.navigate("insights"));
        steps.add(() -> save(scene, dir, "5-insights"));
        steps.add(() -> {   // robustness: every restaurant's card and menu page, plus the remaining actions
            int fails = 0;
            for (Data.Restaurant r : Data.RESTAURANTS) {
                try {
                    DiscoverView.card(s, r, DiscoverView.pickCover(r, new java.util.HashSet<>(), ""));
                    s.restaurant.set(r);
                    MenuView.create(s);
                    s.add(r, r.menu().get(0));
                    s.remove(r.menu().get(0));
                } catch (Exception e) { fails++; System.err.println("SNAP-ERROR " + r.id() + " " + e); }
            }
            s.swapRoute();
            s.travelMode.set("Bike");
            s.order.set(null);
            s.demoOrder();
            System.out.println("SNAP-CHECK restaurants=" + Data.RESTAURANTS.size() + " failures=" + fails
                + " bagAfterAddRemove=" + s.cartCount.get() + " demoOrder=" + (s.order.get() != null));
        });
        steps.add(() -> app.navigate("tracker", true));
        steps.add(() -> save(scene, dir, "8-after-swap"));
        steps.add(Platform::exit);
        next(steps, 0);
    }

    private static MapView find(javafx.scene.Node n) {
        if (n instanceof MapView m) return m;
        if (n instanceof javafx.scene.Parent p) for (javafx.scene.Node c : p.getChildrenUnmodifiable()) { MapView m = find(c); if (m != null) return m; }
        return null;
    }

    private static void next(List<Runnable> steps, int i) {
        if (i >= steps.size()) return;
        PauseTransition p = new PauseTransition(Duration.seconds(3.5));
        p.setOnFinished(e -> { steps.get(i).run(); next(steps, i + 1); });
        p.play();
    }

    private static void save(Scene scene, String dir, String name) {
        WritableImage img = scene.snapshot(null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        try {
            new File(dir).mkdirs();
            ImageIO.write(bi, "png", new File(dir, name + ".png"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
