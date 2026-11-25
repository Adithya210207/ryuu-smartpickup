import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Dev tool: packs OpenStreetMap extracts (Overpass JSON) into assets/coimbatore.map,
 * the compact file the app draws and routes on offline.
 *
 *   java -Xmx8g tools/PackMap.java assets/coimbatore.map areas.json roads1.json [roads2.json ...]
 *
 * roadsN.json: way[highway~...](bbox);out body;>;out skel qt;   (chunks may overlap; ways are de-duplicated)
 * areas.json: water/parks/rail ways and relations with "out geom", then place nodes.
 */
public class PackMap {
    static final List<String> ROAD_CLASSES = List.of("motorway", "trunk", "primary", "secondary", "tertiary",
        "unclassified", "residential", "living_street", "service", "pedestrian");

    public static void main(String[] a) throws Exception {
        String outFile = a[0];
        Map<String, Object> areas = obj(new Json(Files.readString(Path.of(a[1]), StandardCharsets.UTF_8)).value());

        // ---- road graph
        Map<Long, double[]> nodeLL = new HashMap<>();
        Map<Long, Map<String, Object>> wayById = new LinkedHashMap<>();
        for (int f = 2; f < a.length; f++) {
            Map<String, Object> roads = obj(new Json(Files.readString(Path.of(a[f]), StandardCharsets.UTF_8)).value());
            for (Object o : list(roads.get("elements"))) {
                Map<String, Object> e = obj(o);
                if (e.get("type").equals("node")) nodeLL.put(num(e.get("id")).longValue(), new double[]{num(e.get("lat")).doubleValue(), num(e.get("lon")).doubleValue()});
                else if (e.get("type").equals("way")) wayById.put(num(e.get("id")).longValue(), e);
            }
            System.out.println("read " + a[f] + ": " + wayById.size() + " ways so far");
        }
        List<Map<String, Object>> ways = new ArrayList<>(wayById.values());
        Map<Long, Integer> index = new LinkedHashMap<>();
        List<int[]> wayNodes = new ArrayList<>();
        List<int[]> wayMeta = new ArrayList<>();   // class, oneway, link
        List<String> wayNames = new ArrayList<>();
        for (Map<String, Object> w : ways) {
            Map<String, Object> tags = obj(w.getOrDefault("tags", Map.of()));
            String hw = str(tags.get("highway"));
            boolean link = hw.endsWith("_link");
            int cls = ROAD_CLASSES.indexOf(link ? hw.substring(0, hw.length() - 5) : hw);
            if (cls < 0) continue;
            String ow = str(tags.get("oneway"));
            int oneway = ow.equals("yes") || ow.equals("1") || ow.equals("true") ? 1 : ow.equals("-1") ? -1
                : (hw.equals("motorway") || str(tags.get("junction")).equals("roundabout")) && !ow.equals("no") ? 1 : 0;
            List<Object> nds = list(w.get("nodes"));
            int[] idx = new int[nds.size()];
            int k = 0;
            for (Object n : nds) {
                long id = num(n).longValue();
                if (!nodeLL.containsKey(id)) continue;
                idx[k++] = index.computeIfAbsent(id, x -> index.size());
            }
            if (k < 2) continue;
            wayNodes.add(Arrays.copyOf(idx, k));
            wayMeta.add(new int[]{cls, oneway, link ? 1 : 0});
            String name = str(tags.get("name:en"));
            if (name.isEmpty()) name = str(tags.get("name"));
            wayNames.add(name);
        }

        // ---- areas, lines, labels
        List<Object[]> polys = new ArrayList<>();   // {kind, List<double[][]> rings}
        List<Object[]> lines = new ArrayList<>();   // {kind, double[][]}
        List<Object[]> labels = new ArrayList<>();  // {kind, name, lat, lon}
        for (Object o : list(areas.get("elements"))) {
            Map<String, Object> e = obj(o);
            Map<String, Object> tags = obj(e.getOrDefault("tags", Map.of()));
            String type = str(e.get("type"));
            if (type.equals("node")) {
                String name = str(tags.get("name:en"));
                if (name.isEmpty()) name = str(tags.get("name"));
                if (name.isEmpty() || !name.matches("[\\x20-\\x7E]+")) continue;
                String p = str(tags.get("place"));
                int kind = p.equals("city") || p.equals("town") ? 0 : p.equals("suburb") ? 1 : 2;
                labels.add(new Object[]{kind, name, num(e.get("lat")).doubleValue(), num(e.get("lon")).doubleValue()});
                continue;
            }
            String ww = str(tags.get("waterway")), rw = str(tags.get("railway")), ae = str(tags.get("aeroway"));
            if (type.equals("way") && (!ww.isEmpty() || rw.equals("rail") || ae.equals("runway") || ae.equals("taxiway"))) {
                int kind = rw.equals("rail") ? 0 : !ww.isEmpty() ? (ww.equals("river") ? 1 : 2) : ae.equals("runway") ? 3 : 4;
                lines.add(new Object[]{kind, geom(e.get("geometry"))});
                continue;
            }
            int kind = areaKind(tags);
            if (kind < 0) continue;
            List<double[][]> rings = new ArrayList<>();
            if (type.equals("way")) {
                double[][] g = geom(e.get("geometry"));
                if (g.length >= 3) rings.add(g);
            } else {
                List<double[][]> parts = new ArrayList<>();
                for (Object m : list(e.getOrDefault("members", List.of()))) {
                    Map<String, Object> mm = obj(m);
                    if (mm.get("geometry") != null) parts.add(geom(mm.get("geometry")));
                }
                rings.addAll(join(parts));
            }
            if (!rings.isEmpty()) polys.add(new Object[]{kind, rings});
        }

        // ---- write
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(outFile))))) {
            out.writeInt(0x52594D31); // RYM1
            out.writeInt(index.size());
            double[][] ll = new double[index.size()][];
            for (var en : index.entrySet()) ll[en.getValue()] = nodeLL.get(en.getKey());
            int pl = 0, pn = 0;
            for (double[] p : ll) {
                int la = (int) Math.round(p[0] * 1e6), lo = (int) Math.round(p[1] * 1e6);
                out.writeInt(la - pl); out.writeInt(lo - pn); pl = la; pn = lo;
            }
            out.writeInt(wayNodes.size());
            for (int i = 0; i < wayNodes.size(); i++) {
                int[] m = wayMeta.get(i);
                out.writeByte(m[0]); out.writeByte(m[1]); out.writeByte(m[2]);
                out.writeUTF(wayNames.get(i));
                out.writeShort(wayNodes.get(i).length);
                for (int n : wayNodes.get(i)) out.writeInt(n);
            }
            out.writeInt(polys.size());
            for (Object[] p : polys) {
                out.writeByte((int) p[0]);
                @SuppressWarnings("unchecked") List<double[][]> rings = (List<double[][]>) p[1];
                out.writeShort(rings.size());
                for (double[][] r : rings) coords(out, r);
            }
            out.writeInt(lines.size());
            for (Object[] l : lines) { out.writeByte((int) l[0]); coords(out, (double[][]) l[1]); }
            out.writeInt(labels.size());
            for (Object[] l : labels) {
                out.writeByte((int) l[0]); out.writeUTF((String) l[1]);
                out.writeInt((int) Math.round((double) l[2] * 1e6)); out.writeInt((int) Math.round((double) l[3] * 1e6));
            }
        }
        System.out.printf("nodes=%d ways=%d areas=%d lines=%d labels=%d -> %d bytes%n",
            index.size(), wayNodes.size(), polys.size(), lines.size(), labels.size(), new File(outFile).length());
    }

    /** 0 water, 1 park/green, 2 wood, 3 campus/hospital, 4 airport, 5 cemetery/military */
    static int areaKind(Map<String, Object> t) {
        String n = str(t.get("natural")), l = str(t.get("landuse")), le = str(t.get("leisure")), am = str(t.get("amenity")), ae = str(t.get("aeroway"));
        if (n.equals("water") || l.equals("reservoir")) return 0;
        if (!le.isEmpty() || l.equals("grass") || l.equals("recreation_ground")) return 1;
        if (n.equals("wood") || l.equals("forest")) return 2;
        if (!am.isEmpty()) return 3;
        if (ae.equals("aerodrome")) return 4;
        if (l.equals("cemetery") || l.equals("military")) return 5;
        return -1;
    }

    static void coords(DataOutputStream out, double[][] r) throws IOException {
        int n = Math.min(r.length, 65535);
        out.writeShort(n);
        for (int i = 0; i < n; i++) { out.writeInt((int) Math.round(r[i][0] * 1e6)); out.writeInt((int) Math.round(r[i][1] * 1e6)); }
    }

    static double[][] geom(Object g) {
        List<Object> pts = list(g);
        List<double[]> out = new ArrayList<>();
        for (Object o : pts) {
            if (o == null) continue;
            Map<String, Object> p = obj(o);
            out.add(new double[]{num(p.get("lat")).doubleValue(), num(p.get("lon")).doubleValue()});
        }
        return out.toArray(new double[0][]);
    }

    /** Joins multipolygon member fragments end to end into rings. */
    static List<double[][]> join(List<double[][]> parts) {
        List<double[][]> rings = new ArrayList<>();
        LinkedList<double[][]> open = new LinkedList<>();
        for (double[][] p : parts) if (p.length >= 2) { if (same(p[0], p[p.length - 1])) rings.add(p); else open.add(p); }
        while (!open.isEmpty()) {
            List<double[]> cur = new ArrayList<>(Arrays.asList(open.removeFirst()));
            boolean grew = true;
            while (grew && !same(cur.get(0), cur.get(cur.size() - 1))) {
                grew = false;
                for (Iterator<double[][]> it = open.iterator(); it.hasNext(); ) {
                    double[][] p = it.next();
                    double[] end = cur.get(cur.size() - 1);
                    if (same(end, p[0])) { cur.addAll(Arrays.asList(p).subList(1, p.length)); }
                    else if (same(end, p[p.length - 1])) { List<double[]> r = new ArrayList<>(Arrays.asList(p)); Collections.reverse(r); cur.addAll(r.subList(1, r.size())); }
                    else continue;
                    it.remove(); grew = true; break;
                }
            }
            if (cur.size() >= 3) rings.add(cur.toArray(new double[0][]));
        }
        return rings;
    }

    static boolean same(double[] a, double[] b) { return Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7; }

    // ---------------------------------------------------------------- tiny JSON reader
    @SuppressWarnings("unchecked") static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> list(Object o) { return o == null ? List.of() : (List<Object>) o; }
    static Number num(Object o) { return (Number) o; }
    static String str(Object o) { return o == null ? "" : o.toString(); }

    static final class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = string(); ws(); i++; m.put(k, value()); ws(); if (s.charAt(i++) == '}') return m; }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(value()); ws(); if (s.charAt(i++) == ']') return l; }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(st, i);
            return n.contains(".") || n.contains("e") || n.contains("E") ? (Number) Double.parseDouble(n) : (Number) Long.parseLong(n);
        }
        String string() {
            i++; StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else b.append(c);
            }
        }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }
}
