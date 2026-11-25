package com.ryuu.pickup;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's commute: a real road route between two Coimbatore places (offline street map),
 * plus which restaurants sit on it and how far along.
 */
final class CommuteRoute {
    record Place(String name, String area, double lat, double lon) {
        @Override public String toString() { return name; }
    }

    static final List<Place> PLACES = List.of(
        new Place("Gandhipuram", "Bus terminus", 11.01827, 76.96777),
        new Place("RS Puram", "Cowley Brown Road", 11.01451, 76.94426),
        new Place("Saibaba Colony", "Mettupalayam Road", 11.02433, 76.94479),
        new Place("Town Hall", "Big Bazaar Street", 10.99677, 76.95562),
        new Place("Coimbatore Junction", "Railway station", 10.99757, 76.96637),
        new Place("Ukkadam", "Bus stand", 10.99130, 76.96281),
        new Place("Race Course", "Kamarajar Road", 11.00496, 76.98585),
        new Place("Brookefields Mall", "Krishnaswamy Road", 11.00887, 76.95940),
        new Place("Vadavalli", "Marudhamalai Road", 11.02534, 76.90513),
        new Place("Kavundampalayam", "Mettupalayam Road", 11.04520, 76.94724),
        new Place("Ganapathy", "Sathy Road", 11.03832, 76.97807),
        new Place("Prozone Mall", "Sathy Road", 11.05543, 76.99373),
        new Place("Saravanampatti", "Sathy Road", 11.08070, 76.99850),
        new Place("PSG Tech", "Avinashi Road, Peelamedu", 11.02468, 77.00284),
        new Place("Hope College", "Avinashi Road", 11.02577, 77.01661),
        new Place("Tidel Park", "Vilankurichi Road", 11.03146, 77.01885),
        new Place("Coimbatore Airport", "Avinashi Road", 11.03291, 77.04270),
        // across the district
        new Place("Singanallur", "Trichy Road", 10.99900, 77.02300),
        new Place("Thudiyalur", "Mettupalayam Road", 11.08000, 76.94200),
        new Place("Periyanaickenpalayam", "Mettupalayam Road", 11.15300, 76.94800),
        new Place("Karamadai", "Mettupalayam Road", 11.24200, 76.95900),
        new Place("Mettupalayam", "Bus stand", 11.29950, 76.93840),
        new Place("Annur", "Sathy Road", 11.23200, 77.10500),
        new Place("Sulur", "Trichy Road", 11.02500, 77.12500),
        new Place("Thondamuthur", "Siruvani Road", 10.99100, 76.84100),
        new Place("Isha Yoga Centre", "Velliangiri foothills", 10.97900, 76.73800),
        new Place("Kovaipudur", "Palakkad Road side", 10.94400, 76.93000),
        new Place("Madukkarai", "Palakkad Road", 10.90500, 76.96300),
        new Place("Kinathukadavu", "Pollachi Road", 10.82300, 77.01700),
        new Place("Pollachi", "Bus stand", 10.65900, 77.00800),
        new Place("Anaimalai", "Pollachi", 10.58300, 76.93300),
        new Place("Valparai", "Anamalai Hills", 10.32700, 76.95500));

    static Place place(String name) {
        return PLACES.stream().filter(p -> p.name().equals(name)).findFirst().orElse(PLACES.get(0));
    }

    /** Within this distance of the road, a restaurant counts as on the way. */
    static final double ON_ROUTE_M = 650;

    /** Current route, shared by the whole app (prototype). */
    static volatile CommuteRoute current = new CommuteRoute(place("Saibaba Colony"), place("Coimbatore Airport"), null, 0, 0);

    final Place from, to;
    final List<double[]> pts;          // {lat, lon}
    final double[] cum;                // cumulative metres along pts
    final double meters, seconds;
    final boolean live;                // true when it follows real roads

    private CommuteRoute(Place from, Place to, List<double[]> pts, double meters, double seconds) {
        this.from = from;
        this.to = to;
        if (pts == null || pts.size() < 2) {
            pts = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                double f = i / 20.0;
                pts.add(new double[]{from.lat() + (to.lat() - from.lat()) * f, from.lon() + (to.lon() - from.lon()) * f});
            }
            this.live = false;
        } else {
            this.live = true;
        }
        this.pts = pts;
        cum = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) cum[i] = cum[i - 1] + dist(pts.get(i - 1), pts.get(i));
        this.meters = meters > 0 ? meters : cum[cum.length - 1] * 1.3;
        this.seconds = seconds > 0 ? seconds : this.meters / (25_000 / 3600.0);
    }

    // ---------------------------------------------------------------- geometry

    static double dist(double[] a, double[] b) { return haversine(a[0], a[1], b[0], b[1]); }

    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    /** {distance from route in m, fraction along route 0..1} */
    double[] project(double lat, double lon) {
        double best = Double.MAX_VALUE, bestAlong = 0;
        double kx = 111_320 * Math.cos(Math.toRadians(lat)), ky = 110_540;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double ax = (a[1] - lon) * kx, ay = (a[0] - lat) * ky, bx = (b[1] - lon) * kx, by = (b[0] - lat) * ky;
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double px = ax + dx * t, py = ay + dy * t, d = Math.hypot(px, py);
            if (d < best) { best = d; bestAlong = cum[i - 1] + (cum[i] - cum[i - 1]) * t; }
        }
        return new double[]{best, cum[cum.length - 1] == 0 ? 0 : bestAlong / cum[cum.length - 1]};
    }

    private final java.util.Map<String, double[]> projCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cached {distance m, fraction} for a restaurant on this route. */
    double[] projection(Data.Restaurant r) {
        return projCache.computeIfAbsent(r.id(), k -> project(r.lat(), r.lon()));
    }

    /** Point at fraction t along the route, {lat, lon}. */
    double[] at(double t) {
        double target = Math.max(0, Math.min(1, t)) * cum[cum.length - 1];
        for (int i = 1; i < pts.size(); i++) {
            if (cum[i] >= target) {
                double seg = cum[i] - cum[i - 1], f = seg == 0 ? 0 : (target - cum[i - 1]) / seg;
                double[] a = pts.get(i - 1), b = pts.get(i);
                return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
            }
        }
        return pts.get(pts.size() - 1);
    }

    // ---------------------------------------------------------------- routing (offline, on the bundled map)

    /** Finds the road route in the background, then publishes it on the FX thread. */
    static void request(Place from, Place to, Runnable onReady) {
        current = new CommuteRoute(from, to, null, 0, 0);
        Platform.runLater(onReady);
        Thread t = new Thread(() -> {
            CommuteRoute r = fetch(from, to);
            Platform.runLater(() -> {
                if (current.from == from && current.to == to) { current = r; onReady.run(); }
            });
        }, "route-find");
        t.setDaemon(true);
        t.start();
    }

    /** Fastest driving route on the offline street map; a straight line if the map file is missing. */
    static CommuteRoute fetch(Place from, Place to) {
        OfflineMap map = OfflineMap.load();
        OfflineMap.Route r = map == null ? null : map.route(from.lat(), from.lon(), to.lat(), to.lon());
        return r == null ? new CommuteRoute(from, to, null, 0, 0) : new CommuteRoute(from, to, r.pts(), r.meters(), r.seconds());
    }

    // ---------------------------------------------------------------- restaurants on the way

    /** Minutes for the whole trip by the chosen mode, with a city-traffic allowance. */
    int minutes(String mode) {
        double f = switch (mode) { case "Bike" -> 1.05; case "Bus" -> 1.9; default -> 1.35; };
        return Math.max(3, (int) Math.round(seconds * f / 60));
    }

    double km() { return meters / 1000.0; }
}
