package com.ryuu.pickup;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;

/**
 * Coimbatore's streets, lakes, parks and railways from OpenStreetMap, stored in assets/coimbatore.map
 * (built by tools/PackMap.java). The app draws its map tiles and finds road routes from this file,
 * so neither needs an internet connection.
 */
final class OfflineMap {
    // road classes, in the order PackMap writes them
    static final int MOTORWAY = 0, TRUNK = 1, PRIMARY = 2, SECONDARY = 3, TERTIARY = 4, UNCLASSIFIED = 5,
        RESIDENTIAL = 6, LIVING = 7, SERVICE = 8, PEDESTRIAN = 9;
    private static final double[] KMH = {70, 50, 42, 36, 30, 26, 22, 12, 15, 5};

    record Label(int kind, String name, double lat, double lon) {}
    record Way(int cls, int oneway, String name, int[] nodes) {}
    record Route(List<double[]> pts, double meters, double seconds) {}

    final double[] lat, lon, mx, my;        // nodes; mx/my = Web-Mercator 0..1
    final Way[] ways;
    final int[] polyKind; final double[][][] polys;   // rings as interleaved mx,my
    final int[] lineKind; final double[][] lines;
    final List<Label> labels = new ArrayList<>();
    final double minLat, maxLat, minLon, maxLon;

    // spatial grid (lat/lon cells) for ways, areas and lines
    private static final double CELL = 0.01;
    private final int gw, gh;
    private final int[][] gridWays, gridPolys, gridLines;

    // road graph (compressed adjacency)
    private final int[] adjStart, adjTo;
    private final float[] adjSec, adjM;
    private final boolean[] routable, major;

    // ------------------------------------------------------------------ loading

    private static volatile OfflineMap instance;
    private static volatile boolean failed;

    /** The map, or null while it is still loading or when the file is missing. */
    static OfflineMap get() { return instance; }

    static boolean unavailable() { return failed; }

    /** Loads the map file (blocking). Safe to call from several threads. */
    static synchronized OfflineMap load() {
        if (instance != null || failed) return instance;
        for (String base : new String[]{System.getProperty("user.dir"), System.getProperty("ryuu.home", "")}) {
            File f = new File(base, "assets/coimbatore.map");
            if (!f.isFile()) continue;
            try { instance = new OfflineMap(f); return instance; }
            catch (Exception e) { System.err.println("Could not read " + f + ": " + e); }
        }
        failed = true;
        return null;
    }

    private OfflineMap(File file) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file), 1 << 16)))) {
            if (in.readInt() != 0x52594D31) throw new IllegalStateException("not a Ryuu map file");
            int n = in.readInt();
            lat = new double[n]; lon = new double[n]; mx = new double[n]; my = new double[n];
            int la = 0, lo = 0;
            for (int i = 0; i < n; i++) {
                la += in.readInt(); lo += in.readInt();
                lat[i] = la / 1e6; lon[i] = lo / 1e6;
                mx[i] = mercX(lon[i]); my[i] = mercY(lat[i]);
            }
            ways = new Way[in.readInt()];
            for (int i = 0; i < ways.length; i++) {
                int cls = in.readByte(), ow = in.readByte();
                in.readByte();                                    // link flag (unused)
                String name = in.readUTF();
                int[] nodes = new int[in.readUnsignedShort()];
                for (int k = 0; k < nodes.length; k++) nodes[k] = in.readInt();
                ways[i] = new Way(cls, ow, name, nodes);
            }
            int pc = in.readInt();
            polyKind = new int[pc]; polys = new double[pc][][];
            for (int i = 0; i < pc; i++) {
                polyKind[i] = in.readByte();
                polys[i] = new double[in.readUnsignedShort()][];
                for (int r = 0; r < polys[i].length; r++) polys[i][r] = readCoords(in);
            }
            int lc = in.readInt();
            lineKind = new int[lc]; lines = new double[lc][];
            for (int i = 0; i < lc; i++) { lineKind[i] = in.readByte(); lines[i] = readCoords(in); }
            int nl = in.readInt();
            for (int i = 0; i < nl; i++) labels.add(new Label(in.readByte(), in.readUTF(), in.readInt() / 1e6, in.readInt() / 1e6));
        }
        double a = 90, b = -90, c = 180, d = -180;
        for (int i = 0; i < lat.length; i++) { a = Math.min(a, lat[i]); b = Math.max(b, lat[i]); c = Math.min(c, lon[i]); d = Math.max(d, lon[i]); }
        minLat = a; maxLat = b; minLon = c; maxLon = d;

        gw = (int) Math.ceil((maxLon - minLon) / CELL) + 1;
        gh = (int) Math.ceil((maxLat - minLat) / CELL) + 1;
        gridWays = grid(ways.length, i -> bboxOfNodes(ways[i].nodes));
        gridPolys = grid(polys.length, i -> bboxOfMerc(polys[i]));
        gridLines = grid(lines.length, i -> bboxOfMerc(new double[][]{lines[i]}));

        // graph: every consecutive node pair of a drivable way is an edge
        int[] deg = new int[lat.length];
        for (Way w : ways) {
            if (w.cls == PEDESTRIAN) continue;
            for (int k = 1; k < w.nodes.length; k++) {
                if (w.oneway >= 0) deg[w.nodes[k - 1]]++;
                if (w.oneway <= 0) deg[w.nodes[k]]++;
            }
        }
        adjStart = new int[lat.length + 1];
        for (int i = 0; i < lat.length; i++) adjStart[i + 1] = adjStart[i] + deg[i];
        int m = adjStart[lat.length];
        adjTo = new int[m]; adjSec = new float[m]; adjM = new float[m];
        int[] fill = Arrays.copyOf(adjStart, lat.length);
        routable = new boolean[lat.length];
        major = new boolean[lat.length];
        for (Way w : ways) {
            if (w.cls == PEDESTRIAN) continue;
            double speed = KMH[w.cls] / 3.6;
            for (int k = 1; k < w.nodes.length; k++) {
                int p = w.nodes[k - 1], q = w.nodes[k];
                float meters = (float) CommuteRoute.haversine(lat[p], lon[p], lat[q], lon[q]);
                float sec = (float) (meters / speed);
                if (w.oneway >= 0) { adjTo[fill[p]] = q; adjM[fill[p]] = meters; adjSec[fill[p]++] = sec; }
                if (w.oneway <= 0) { adjTo[fill[q]] = p; adjM[fill[q]] = meters; adjSec[fill[q]++] = sec; }
                if (w.cls <= RESIDENTIAL) { routable[p] = true; routable[q] = true; }
                if (w.cls <= TERTIARY) { major[p] = true; major[q] = true; }
            }
        }
    }

    private static double[] readCoords(DataInputStream in) throws Exception {
        int n = in.readUnsignedShort();
        double[] out = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double la = in.readInt() / 1e6, lo = in.readInt() / 1e6;
            out[i * 2] = mercX(lo);
            out[i * 2 + 1] = mercY(la);
        }
        return out;
    }

    static double mercX(double lon) { return (lon + 180) / 360; }

    static double mercY(double lat) {
        double s = Math.sin(Math.toRadians(lat));
        return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
    }

    static double latOf(double y) { return Math.toDegrees(Math.atan(Math.sinh(Math.PI - 2 * Math.PI * y))); }

    static double lonOf(double x) { return x * 360 - 180; }

    // ------------------------------------------------------------------ spatial grid

    /** {minLat, minLon, maxLat, maxLon} */
    private double[] bboxOfNodes(int[] nodes) {
        double a = 90, b = 180, c = -90, d = -180;
        for (int n : nodes) { a = Math.min(a, lat[n]); b = Math.min(b, lon[n]); c = Math.max(c, lat[n]); d = Math.max(d, lon[n]); }
        return new double[]{a, b, c, d};
    }

    private static double[] bboxOfMerc(double[][] rings) {
        double x0 = 1, y0 = 1, x1 = 0, y1 = 0;
        for (double[] r : rings)
            for (int i = 0; i < r.length; i += 2) { x0 = Math.min(x0, r[i]); x1 = Math.max(x1, r[i]); y0 = Math.min(y0, r[i + 1]); y1 = Math.max(y1, r[i + 1]); }
        return new double[]{latOf(y1), lonOf(x0), latOf(y0), lonOf(x1)};
    }

    private int[][] grid(int count, java.util.function.IntFunction<double[]> bbox) {
        List<List<Integer>> cells = new ArrayList<>();
        for (int i = 0; i < gw * gh; i++) cells.add(new ArrayList<>());
        for (int i = 0; i < count; i++) {
            double[] bb = bbox.apply(i);
            int c0 = cx(bb[1]), c1 = cx(bb[3]), r0 = cy(bb[0]), r1 = cy(bb[2]);
            for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) cells.get(r * gw + c).add(i);
        }
        int[][] out = new int[gw * gh][];
        for (int i = 0; i < out.length; i++) out[i] = cells.get(i).stream().mapToInt(Integer::intValue).toArray();
        return out;
    }

    private int cx(double lo) { return Math.max(0, Math.min(gw - 1, (int) ((lo - minLon) / CELL))); }
    private int cy(double la) { return Math.max(0, Math.min(gh - 1, (int) ((la - minLat) / CELL))); }

    /** Indices of items whose cells overlap the lat/lon box. */
    private int[] query(int[][] grid, int count, double la0, double lo0, double la1, double lo1) {
        if (la1 < minLat || la0 > maxLat || lo1 < minLon || lo0 > maxLon) return new int[0];
        BitSet seen = new BitSet(count);
        for (int r = cy(la0); r <= cy(la1); r++) for (int c = cx(lo0); c <= cx(lo1); c++) for (int i : grid[r * gw + c]) seen.set(i);
        return seen.stream().toArray();
    }

    int[] waysIn(double la0, double lo0, double la1, double lo1) { return query(gridWays, ways.length, la0, lo0, la1, lo1); }

    // ------------------------------------------------------------------ tile rendering

    static final int SCALE = 2;                       // tiles are drawn at 2x for sharp text on HiDPI screens
    private static final Color PAPER = new Color(0xF1EEE8), WATER = new Color(0xC9DBE4), PARK = new Color(0xDCE4D0),
        WOOD = new Color(0xD3DDC8), CAMPUS = new Color(0xEAE4D9), AIRPORT = new Color(0xE7E3DB), OTHER = new Color(0xE4E1D8),
        RAIL = new Color(0xB4ADA2), RUNWAY = new Color(0xD5D0C6),
        CASE_MINOR = new Color(0xDCD5C9), CASE_MAJOR = new Color(0xCBBEA8), ROAD = Color.WHITE, ROAD_MAJOR = new Color(0xFBF4E4);
    private static final double[] WIDTH = {9, 9, 8, 7, 6, 4.5, 4, 3, 2.5, 2.5};   // px at zoom 15

    static double roadWidth(int cls, int z) {
        return Math.max(0.6, WIDTH[cls] * Math.min(3, Math.pow(1.6, z - 15)));
    }

    static int maxClass(int z) {
        return z <= 9 ? PRIMARY : z <= 11 ? SECONDARY : z == 12 ? TERTIARY : z == 13 ? UNCLASSIFIED : z == 14 ? LIVING : PEDESTRIAN;
    }

    /** Renders one 256-unit map tile (at {@link #SCALE}x pixels). */
    WritableImage tile(int z, int tx, int ty) {
        int size = 256 * SCALE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(PAPER);
        g.fillRect(0, 0, size, size);

        double world = 256.0 * (1 << z);
        double ox = tx * 256.0, oy = ty * 256.0;
        // query box with a margin for wide roads crossing the edge
        double m = 24 / world;
        double la0 = latOf((ty + 1) * 256 / world + m), la1 = latOf(ty * 256 / world - m);
        double lo0 = lonOf(tx * 256 / world - m), lo1 = lonOf((tx + 1) * 256 / world + m);
        double k = world * SCALE, sx = ox * SCALE, sy = oy * SCALE;

        for (int i : query(gridPolys, polys.length, la0, lo0, la1, lo1)) {
            Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            for (double[] r : polys[i]) {
                for (int j = 0; j < r.length; j += 2) {
                    double x = r[j] * k - sx, y = r[j + 1] * k - sy;
                    if (j == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.closePath();
            }
            g.setColor(switch (polyKind[i]) { case 0 -> WATER; case 1 -> PARK; case 2 -> WOOD; case 3 -> CAMPUS; case 4 -> AIRPORT; default -> OTHER; });
            g.fill(p);
        }

        int[] lineIdx = query(gridLines, lines.length, la0, lo0, la1, lo1);
        float lw = (float) Math.min(3, Math.pow(1.6, z - 15));
        for (int pass = 0; pass < 2; pass++) {
            for (int i : lineIdx) {
                int kind = lineKind[i];
                if ((pass == 0) != (kind != 0)) continue;          // waterways and runways first, railways later
                if (kind == 2 && z < 14) continue;                    // streams
                Path2D.Double p = path(lines[i], k, sx, sy);
                switch (kind) {
                    case 0 -> {
                        g.setColor(RAIL);
                        g.setStroke(new BasicStroke(Math.max(1f, 1.6f * lw) * SCALE, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                            1, new float[]{6f * SCALE, 4f * SCALE}, 0));
                    }
                    case 1, 2 -> { g.setColor(WATER); g.setStroke(new BasicStroke((kind == 1 ? 5f : 2f) * lw * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); }
                    case 3 -> { g.setColor(RUNWAY); g.setStroke(new BasicStroke(Math.max(2f, 22f * lw) * SCALE, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)); }
                    default -> { g.setColor(RUNWAY); g.setStroke(new BasicStroke(Math.max(1f, 8f * lw) * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); }
                }
                g.draw(p);
            }
        }

        int maxCls = maxClass(z);
        int[] wayIdx = query(gridWays, ways.length, la0, lo0, la1, lo1);
        Path2D.Double[] paths = new Path2D.Double[wayIdx.length];
        for (int j = 0; j < wayIdx.length; j++) {
            Way w = ways[wayIdx[j]];
            if (w.cls > maxCls) continue;
            Path2D.Double p = new Path2D.Double();
            for (int q = 0; q < w.nodes.length; q++) {
                int nd = w.nodes[q];
                double x = mx[nd] * k - sx, y = my[nd] * k - sy;
                if (q == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            paths[j] = p;
        }
        // casings (outlines), minor roads first so major roads sit on top
        boolean casings = z >= 13;
        for (int cls = maxCls; cls >= 0 && casings; cls--) {
            float wpx = (float) (roadWidth(cls, z) + (cls <= SECONDARY ? 2 : 1.5)) * SCALE;
            g.setColor(cls <= SECONDARY ? CASE_MAJOR : CASE_MINOR);
            g.setStroke(new BasicStroke(wpx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int j = 0; j < wayIdx.length; j++) if (paths[j] != null && ways[wayIdx[j]].cls == cls) g.draw(paths[j]);
        }
        for (int cls = maxCls; cls >= 0; cls--) {
            float wpx = (float) roadWidth(cls, z) * SCALE;
            g.setColor(!casings ? (cls <= SECONDARY ? CASE_MAJOR : CASE_MINOR) : cls <= PRIMARY ? ROAD_MAJOR : ROAD);
            g.setStroke(new BasicStroke(wpx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int j = 0; j < wayIdx.length; j++) if (paths[j] != null && ways[wayIdx[j]].cls == cls) g.draw(paths[j]);
        }
        g.dispose();

        WritableImage out = new WritableImage(size, size);
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        out.getPixelWriter().setPixels(0, 0, size, size, PixelFormat.getIntArgbPreInstance(), data, 0, size);
        return out;
    }

    private static Path2D.Double path(double[] c, double k, double sx, double sy) {
        Path2D.Double p = new Path2D.Double();
        for (int j = 0; j < c.length; j += 2) {
            double x = c[j] * k - sx, y = c[j + 1] * k - sy;
            if (j == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        return p;
    }

    // ------------------------------------------------------------------ routing

    /** Nearest nodes on the drivable network, closest first. */
    private int[] nearest(double la, double lo, int count, boolean[] allowed) {
        int[] best = new int[count];
        double[] bd = new double[count];
        Arrays.fill(best, -1);
        Arrays.fill(bd, Double.MAX_VALUE);
        double kx = Math.cos(Math.toRadians(la));
        for (int i = 0; i < lat.length; i++) {
            if (!allowed[i]) continue;
            double dx = (lon[i] - lo) * kx, dy = lat[i] - la, d = dx * dx + dy * dy;
            if (d >= bd[count - 1]) continue;
            int p = count - 1;
            while (p > 0 && bd[p - 1] > d) { bd[p] = bd[p - 1]; best[p] = best[p - 1]; p--; }
            bd[p] = d; best[p] = i;
        }
        return best;
    }

    /** Start points: on a main road when one is within 350 m (places are named by their main road), else the nearest street. */
    private int[] snap(double la, double lo) {
        int[] m = nearest(la, lo, 4, major);
        if (m[0] >= 0 && CommuteRoute.haversine(la, lo, lat[m[0]], lon[m[0]]) < 350) return m;
        return nearest(la, lo, 4, routable);
    }

    /** Fastest driving route between two points, or null if none is found. */
    Route route(double la0, double lo0, double la1, double lo1) {
        int[] starts = snap(la0, lo0), ends = snap(la1, lo1);
        for (int s : starts) for (int e : ends) {
            if (s < 0 || e < 0) continue;
            Route r = astar(s, e);
            if (r != null) {
                r.pts.add(0, new double[]{la0, lo0});
                r.pts.add(new double[]{la1, lo1});
                return r;
            }
        }
        return null;
    }

    private Route astar(int s, int e) {
        int n = lat.length;
        double[] gs = new double[n];
        Arrays.fill(gs, Double.MAX_VALUE);
        int[] prev = new int[n];
        float[] prevM = new float[n];
        boolean[] done = new boolean[n];
        double vmax = KMH[MOTORWAY] / 3.6;
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        gs[s] = 0;
        prev[s] = -1;
        open.add(new double[]{0, s});
        while (!open.isEmpty()) {
            int u = (int) open.poll()[1];
            if (done[u]) continue;
            done[u] = true;
            if (u == e) break;
            for (int j = adjStart[u]; j < adjStart[u + 1]; j++) {
                int v = adjTo[j];
                double ng = gs[u] + adjSec[j];
                if (ng < gs[v]) {
                    gs[v] = ng;
                    prev[v] = u;
                    prevM[v] = adjM[j];
                    open.add(new double[]{ng + CommuteRoute.haversine(lat[v], lon[v], lat[e], lon[e]) / vmax, v});
                }
            }
        }
        if (!done[e]) return null;
        List<double[]> pts = new ArrayList<>();
        double meters = 0;
        for (int v = e; v != -1; v = prev[v]) {
            pts.add(0, new double[]{lat[v], lon[v]});
            if (prev[v] != -1) meters += prevM[v];
        }
        return new Route(pts, meters, gs[e]);
    }
}
