package com.ryuu.pickup;

import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A slippy map of Coimbatore drawn from bundled OpenStreetMap data (works offline), with the commute route,
 * restaurant pins and the user's position drawn on top. Drag to pan, scroll to zoom.
 */
final class MapView extends Pane {
    private static final int MIN_Z = 9, MAX_Z = 18;   // tiles are rendered locally from the offline map
    private static final Map<String, Image> TILES = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 120; }
    };

    private final Canvas canvas = new Canvas();
    private double cLat = 11.02, cLon = 76.98;   // view centre
    private int zoom = 13;
    private double dragX, dragY;
    private boolean dragged;

    Supplier<Data.Restaurant> selected = () -> null;
    Supplier<Double> progress = () -> 0.0;       // 0..1 along the route for the "you" marker
    Consumer<Data.Restaurant> onPick = r -> { };
    private CommuteRoute fitted;

    MapView() {
        canvas.setManaged(false);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);
        setMinSize(0, 0);
        setCursor(Cursor.OPEN_HAND);

        setOnMousePressed(e -> { dragX = e.getX(); dragY = e.getY(); dragged = false; setCursor(Cursor.CLOSED_HAND); });
        setOnMouseDragged(e -> {
            double dx = e.getX() - dragX, dy = e.getY() - dragY;
            if (Math.abs(dx) + Math.abs(dy) > 2) dragged = true;
            double[] c = toPixel(cLat, cLon);
            double[] ll = toLatLon(c[0] - dx, c[1] - dy);
            cLat = ll[0];
            cLon = ll[1];
            dragX = e.getX();
            dragY = e.getY();
        });
        setOnMouseReleased(e -> {
            setCursor(Cursor.OPEN_HAND);
            if (!dragged) pick(e.getX(), e.getY());
        });
        setOnScroll(e -> {
            int nz = Math.max(MIN_Z, Math.min(MAX_Z, zoom + (e.getDeltaY() > 0 ? 1 : -1)));
            if (nz == zoom) return;
            double[] before = screenToLatLon(e.getX(), e.getY());
            zoom = nz;
            double[] after = screenToLatLon(e.getX(), e.getY());
            cLat += before[0] - after[0];
            cLon += before[1] - after[1];
        });

        AnimationTimer t = new AnimationTimer() {
            @Override public void handle(long now) { paint(); }
        };
        Fx.whileShowing(this, t::start, t::stop);
    }

    // ---------------------------------------------------------------- projection

    private double world() { return 256 * Math.pow(2, zoom); }

    private double[] toPixel(double lat, double lon) {
        double w = world();
        double x = (lon + 180) / 360 * w;
        double s = Math.sin(Math.toRadians(lat));
        double y = (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * w;
        return new double[]{x, y};
    }

    private double[] toLatLon(double x, double y) {
        double w = world();
        double lon = x / w * 360 - 180;
        double n = Math.PI - 2 * Math.PI * y / w;
        double lat = Math.toDegrees(Math.atan(Math.sinh(n)));
        return new double[]{lat, lon};
    }

    private double[] screen(double lat, double lon) {
        double[] p = toPixel(lat, lon), c = toPixel(cLat, cLon);
        return new double[]{p[0] - c[0] + getWidth() / 2, p[1] - c[1] + getHeight() / 2};
    }

    private double[] screenToLatLon(double sx, double sy) {
        double[] c = toPixel(cLat, cLon);
        return toLatLon(c[0] + sx - getWidth() / 2, c[1] + sy - getHeight() / 2);
    }

    /** Centres the map on a point at a zoom level. */
    void zoomTo(double lat, double lon, int z) {
        fitted = CommuteRoute.current;
        cLat = lat; cLon = lon; zoom = Math.max(MIN_Z, Math.min(MAX_Z, z));
    }

    /** Frames the whole route with some padding. */
    void fit(CommuteRoute r) {
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (double[] p : r.pts) {
            minLat = Math.min(minLat, p[0]); maxLat = Math.max(maxLat, p[0]);
            minLon = Math.min(minLon, p[1]); maxLon = Math.max(maxLon, p[1]);
        }
        cLat = (minLat + maxLat) / 2;
        cLon = (minLon + maxLon) / 2;
        double w = Math.max(200, getWidth() - 70), h = Math.max(200, getHeight() - 60);
        for (zoom = 16; zoom > MIN_Z; zoom--) {
            double[] a = toPixel(maxLat, minLon), b = toPixel(minLat, maxLon);
            if (b[0] - a[0] <= w && b[1] - a[1] <= h) break;
        }
        fitted = r;
    }

    // ---------------------------------------------------------------- painting

    private static final Font LABEL = Font.font("Segoe UI Semibold", 11.5);
    private static final Font SMALL = Font.font("Segoe UI", 10);
    private static final Font AREA = Font.font("Segoe UI Semibold", 10.5), ROADF = Font.font("Segoe UI", 10.5);
    private static final Color INK = Color.web("#1A1814"), ROUTE = Color.web("#3A6EA5"), MUTED = Color.web("#8E877C");

    private void paint() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        CommuteRoute r = CommuteRoute.current;
        if (fitted != r) fit(r);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#EFEDE8"));
        g.fillRect(0, 0, w, h);

        // tiles
        double[] c = toPixel(cLat, cLon);
        double ox = c[0] - w / 2, oy = c[1] - h / 2;
        int n = 1 << zoom;
        int tx0 = (int) Math.floor(ox / 256), ty0 = (int) Math.floor(oy / 256);
        int tx1 = (int) Math.floor((ox + w) / 256), ty1 = (int) Math.floor((oy + h) / 256);
        for (int ty = ty0; ty <= ty1; ty++) {
            if (ty < 0 || ty >= n) continue;
            for (int tx = tx0; tx <= tx1; tx++) {
                int x = ((tx % n) + n) % n;
                Image img = tile(zoom, x, ty);
                double sx = tx * 256 - ox, sy = ty * 256 - oy;
                if (img != null) g.drawImage(img, sx, sy, 256, 256);
            }
        }

        OfflineMap map = OfflineMap.get();
        if (map != null) {
            java.util.List<double[]> taken = new java.util.ArrayList<>();
            roadLabels(g, map, w, h, taken);
            placeLabels(g, map, w, h, taken);
        }

        // route
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        strokeRoute(g, r, 0, 1, Color.WHITE, 9);
        strokeRoute(g, r, 0, 1, ROUTE, 5);
        double prog = progress.get();
        if (prog > 0) strokeRoute(g, r, 0, prog, Color.web("#A8B4C1"), 5);

        // destination
        double[] d = screen(r.to.lat(), r.to.lon());
        g.setFill(INK);
        g.fillRoundRect(d[0] - 8, d[1] - 8, 16, 16, 4, 4);
        g.setFill(Color.WHITE);
        g.fillRoundRect(d[0] - 3, d[1] - 3, 6, 6, 2, 2);
        label(g, r.to.name(), d[0], d[1] + 22, TextAlignment.CENTER, true);

        // restaurants: off-route ones are quiet dots, on-route ones are pins; names only when there's room
        Data.Restaurant sel = selected.get();
        long onCount = Data.RESTAURANTS.stream().filter(Data.Restaurant::onRoute).count();
        boolean names = zoom >= 15 || onCount <= 10;
        java.util.List<double[]> pinLabels = new java.util.ArrayList<>();
        double[] selBox = null;
        if (sel != null) {     // the selected restaurant's name always keeps its space
            double[] p = screen(sel.lat(), sel.lon());
            double tw = Fx.textWidth(sel.name(), LABEL);
            double bx = p[0] + 14 + tw > w - 10 ? p[0] - 14 - tw : p[0] + 14;
            selBox = new double[]{bx - 2, p[1] - 8, bx + tw + 2, p[1] + 8};
        }
        for (int pass = 0; pass < 2; pass++) {
            for (Data.Restaurant x : Data.RESTAURANTS) {
                boolean on = x.onRoute(), isSel = x == sel;
                if ((pass == 0) == (on || isSel)) continue;      // draw quiet dots first, pins on top
                double[] p = screen(x.lat(), x.lon());
                if (p[0] < -20 || p[1] < -20 || p[0] > w + 20 || p[1] > h + 20) continue;
                if (isSel) {
                    g.setFill(INK);
                    g.fillOval(p[0] - 9, p[1] - 9, 18, 18);
                    g.setFill(Color.web("#C8461A"));
                    g.fillOval(p[0] - 4, p[1] - 4, 8, 8);
                } else if (on) {
                    g.setFill(Color.WHITE);
                    double pr = names ? 7 : 5;
                    g.fillOval(p[0] - pr, p[1] - pr, pr * 2, pr * 2);
                    g.setStroke(INK);
                    g.setLineWidth(2.2);
                    g.setLineWidth(names ? 2.2 : 1.8);
                    g.strokeOval(p[0] - pr + 1, p[1] - pr + 1, pr * 2 - 2, pr * 2 - 2);
                } else {
                    g.setFill(Color.rgb(255, 255, 255, 0.9));
                    g.fillOval(p[0] - 4.5, p[1] - 4.5, 9, 9);
                    g.setFill(MUTED);
                    g.fillOval(p[0] - 3, p[1] - 3, 6, 6);
                }
                if (isSel || (on && names)) {
                    double tw = Fx.textWidth(x.name(), LABEL);
                    boolean left = p[0] + 14 + tw > w - 10;
                    double bx = left ? p[0] - 14 - tw : p[0] + 14;
                    double[] box = {bx - 2, p[1] - 8, bx + tw + 2, p[1] + 8};
                    if (!isSel && (overlaps(pinLabels, box) || (selBox != null && overlaps(java.util.List.of(selBox), box)))) continue;
                    pinLabels.add(box);
                    label(g, x.name(), left ? p[0] - 14 : p[0] + 14, p[1], left ? TextAlignment.RIGHT : TextAlignment.LEFT, isSel);
                }
            }
        }


        // you
        double[] u = r.at(prog);
        double[] y = screen(u[0], u[1]);
        g.setFill(Color.rgb(58, 110, 165, 0.18));
        g.fillOval(y[0] - 18, y[1] - 18, 36, 36);
        g.setFill(Color.WHITE);
        g.fillOval(y[0] - 9, y[1] - 9, 18, 18);
        g.setFill(ROUTE);
        g.fillOval(y[0] - 6, y[1] - 6, 12, 12);
        label(g, prog > 0 ? "You" : r.from.name(), y[0], y[1] + 24, TextAlignment.CENTER, true);

        // attribution
        String attr = "© OpenStreetMap contributors";
        g.setFont(SMALL);
        double aw = Fx.textWidth(attr, SMALL) + 12;
        g.setFill(Color.rgb(255, 255, 255, 0.85));
        g.fillRect(w - aw, h - 18, aw, 18);
        g.setFill(Color.web("#5E5850"));
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(attr, w - 6, h - 9);
        if (!r.live) {
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(OfflineMap.unavailable() ? "Map file missing: showing a straight line" : "Loading the map…", 10, h - 9);
        }
    }

    private void strokeRoute(GraphicsContext g, CommuteRoute r, double from, double to, Color col, double width) {
        g.setStroke(col);
        g.setLineWidth(width);
        g.beginPath();
        double total = r.cum[r.cum.length - 1];
        boolean started = false;
        for (int i = 0; i < r.pts.size(); i++) {
            double f = total == 0 ? 0 : r.cum[i] / total;
            if (f < from) continue;
            if (f > to) {
                double[] e = r.at(to), s = screen(e[0], e[1]);
                g.lineTo(s[0], s[1]);
                break;
            }
            double[] s = screen(r.pts.get(i)[0], r.pts.get(i)[1]);
            if (!started) { g.moveTo(s[0], s[1]); started = true; } else g.lineTo(s[0], s[1]);
        }
        g.stroke();
    }

    private void label(GraphicsContext g, String text, double x, double y, TextAlignment a, boolean strong) {
        g.setFont(LABEL);
        g.setTextAlign(a);
        g.setTextBaseline(VPos.CENTER);
        g.setStroke(Color.WHITE);
        g.setLineWidth(4);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.strokeText(text, x, y);
        g.setFill(strong ? INK : Color.web("#3D3830"));
        g.fillText(text, x, y);
    }

    // Tiles are drawn on this computer from the bundled OpenStreetMap extract (OfflineMap),
    // on two background threads, and kept in a small in-memory cache.
    private static final java.util.concurrent.ExecutorService LOADER =
        java.util.concurrent.Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "tiles"); t.setDaemon(true); return t; });
    private static final java.util.Set<String> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Image tile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        Image img;
        synchronized (TILES) { img = TILES.get(key); }
        if (img == null && PENDING.add(key)) LOADER.submit(() -> {
            try {
                OfflineMap map = OfflineMap.load();
                if (map == null) return;
                Image t = map.tile(z, x, y);
                synchronized (TILES) { TILES.put(key, t); }
            } catch (Throwable e) {
                System.err.println("tile " + key + ": " + e);
            } finally {
                PENDING.remove(key);
            }
        });
        return img;
    }

    /** Area names (suburbs, then neighbourhoods when zoomed in), kept clear of each other. */
    private void placeLabels(GraphicsContext g, OfflineMap map, double w, double h, java.util.List<double[]> taken) {
        if (zoom < 9) return;
        g.setFont(AREA);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (int pass = 0; pass < 3; pass++) {
            if (pass == 1 && zoom < 12 || pass == 2 && zoom < 14) break;
            for (OfflineMap.Label l : map.labels) {
                if (l.kind() != pass) continue;
                double[] p = screen(l.lat(), l.lon());
                String t = l.name().toUpperCase(java.util.Locale.ROOT);
                double tw = Fx.textWidth(t, AREA) + 8;
                double[] box = {p[0] - tw / 2, p[1] - 9, p[0] + tw / 2, p[1] + 9};
                if (box[0] < 0 || box[1] < 0 || box[2] > w || box[3] > h || overlaps(taken, box)) continue;
                taken.add(box);
                g.setStroke(Color.rgb(241, 238, 232, 0.9));
                g.setLineWidth(3.5);
                g.strokeText(t, p[0], p[1]);
                g.setFill(Color.web("#8E877C"));
                g.fillText(t, p[0], p[1]);
            }
        }
    }

    /** Names of main roads, written along their longest straight stretch on screen. */
    private void roadLabels(GraphicsContext g, OfflineMap map, double w, double h, java.util.List<double[]> taken) {
        if (zoom < 15) return;
        double[] tl = screenToLatLon(0, 0), br = screenToLatLon(w, h);
        int maxCls = zoom >= 16 ? OfflineMap.RESIDENTIAL : OfflineMap.TERTIARY;
        java.util.Set<String> done = new java.util.HashSet<>();
        g.setFont(ROADF);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (int i : map.waysIn(br[0], tl[1], tl[0], br[1])) {
            OfflineMap.Way way = map.ways[i];
            if (way.cls() > maxCls || way.name().isEmpty() || !ascii(way.name()) || done.contains(way.name())) continue;
            double tw = Fx.textWidth(way.name(), ROADF) + 16;
            double best = 0; double[] a = null, b = null;
            for (int k = 1; k < way.nodes().length; k++) {
                double[] p = screen(map.lat[way.nodes()[k - 1]], map.lon[way.nodes()[k - 1]]);
                double[] q = screen(map.lat[way.nodes()[k]], map.lon[way.nodes()[k]]);
                double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
                if (len > best) { best = len; a = p; b = q; }
            }
            if (a == null || best < tw) continue;
            double cx = (a[0] + b[0]) / 2, cy = (a[1] + b[1]) / 2;
            double[] box = {cx - tw / 2, cy - tw / 2, cx + tw / 2, cy + tw / 2};
            if (cx < 30 || cy < 20 || cx > w - 30 || cy > h - 30 || overlaps(taken, new double[]{cx - 30, cy - 10, cx + 30, cy + 10})) continue;
            double ang = Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0]));
            if (ang > 90) ang -= 180;
            if (ang < -90) ang += 180;
            done.add(way.name());
            taken.add(new double[]{cx - 30, cy - 10, cx + 30, cy + 10});
            g.save();
            g.translate(cx, cy);
            g.rotate(ang);
            g.setStroke(Color.rgb(255, 255, 255, 0.95));
            g.setLineWidth(3);
            g.strokeText(way.name(), 0, 0);
            g.setFill(Color.web("#6F685E"));
            g.fillText(way.name(), 0, 0);
            g.restore();
        }
    }

    private static boolean ascii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 32 || s.charAt(i) > 126) return false;
        return true;
    }

    private static boolean overlaps(java.util.List<double[]> taken, double[] b) {
        for (double[] t : taken) if (b[0] < t[2] && b[2] > t[0] && b[1] < t[3] && b[3] > t[1]) return true;
        return false;
    }

    private void pick(double sx, double sy) {
        for (Data.Restaurant x : Data.RESTAURANTS) {
            double[] p = screen(x.lat(), x.lon());
            if (Math.hypot(sx - p[0], sy - p[1]) < 14) { onPick.accept(x); return; }
        }
    }
}
