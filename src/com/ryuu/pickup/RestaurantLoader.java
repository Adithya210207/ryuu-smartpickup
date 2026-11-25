package com.ryuu.pickup;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Loads real Coimbatore restaurants from assets/restaurants.tsv (exported from public Zomato listings).
 * Lines:  R  slug  name  locality  address  cuisines  rating  votes  lat  lon
 *         I  dish  price  veg(1/0/-1)  description
 */
final class RestaurantLoader {
    private RestaurantLoader() {}

    static List<Data.Restaurant> withFile(List<Data.Restaurant> curated) {
        List<Data.Restaurant> all = new ArrayList<>(curated);
        File f = find();
        if (f == null) return Collections.unmodifiableList(all);
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            String[] head = null;
            List<String[]> items = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                if (p[0].equals("R")) {
                    add(all, curated, head, items);
                    head = p;
                    items = new ArrayList<>();
                } else if (p[0].equals("I") && head != null) {
                    items.add(p);
                }
            }
            add(all, curated, head, items);
        } catch (Exception e) {
            System.err.println("Could not read restaurants.tsv: " + e);
        }
        return Collections.unmodifiableList(all);
    }

    private static File find() {
        for (String base : new String[]{System.getProperty("user.dir"), System.getProperty("ryuu.home", "")}) {
            if (base == null || base.isEmpty()) continue;
            File f = new File(base, "assets/restaurants.tsv");
            if (f.isFile()) return f;
        }
        return null;
    }

    private static void add(List<Data.Restaurant> all, List<Data.Restaurant> curated, String[] h, List<String[]> rows) {
        if (h == null || h.length < 10 || rows.size() < 3) return;
        String name = h[2];
        double lat = Double.parseDouble(h[8]), lon = Double.parseDouble(h[9]);
        for (Data.Restaurant c : curated) {
            boolean sameName = c.name().equalsIgnoreCase(name) || name.toLowerCase(Locale.ROOT).contains(c.name().toLowerCase(Locale.ROOT));
            if (sameName && CommuteRoute.haversine(c.lat(), c.lon(), lat, lon) < 500) return;
        }
        String cuisinesRaw = h[5];
        List<String> cuisines = Arrays.stream(cuisinesRaw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String cl = cuisinesRaw.toLowerCase(Locale.ROOT);

        List<Data.MenuItem> menu = new ArrayList<>();
        boolean anyNonVeg = false;
        for (String[] r : rows) {
            String dish = r[1];
            int price;
            try { price = Integer.parseInt(r[2]); } catch (Exception e) { continue; }
            boolean veg = !"0".equals(r[3]);
            if ("0".equals(r[3])) anyNonVeg = true;
            String desc = r.length > 4 ? tidy(r[4]) : "";
            menu.add(new Data.MenuItem(tidyName(dish), desc, price, prep(dish, cl), null, veg, art(dish), photoFor(dish)));
        }
        if (menu.size() < 3) return;

        Set<String> cats = new LinkedHashSet<>();
        if (has(cl, "south indian", "tamil", "kerala")) cats.add("Breakfast");
        if (has(cl, "biryani")) cats.add("Biryani");
        if (anyNonVeg || has(cl, "chettinad", "arabian", "bbq", "kebab", "shawarma", "seafood")) cats.add("Non-veg");
        if (has(cl, "chinese", "sichuan", "momos", "asian", "thai")) cats.add("Chinese");
        if (has(cl, "cafe", "coffee", "tea", "fast food", "burger", "sandwich", "pizza", "bakery", "rolls", "wraps")) cats.add("Cafe");
        if (has(cl, "mithai", "desserts", "bakery", "ice cream")) cats.add("Sweets");
        if (has(cl, "juices", "shake", "beverages") && cuisines.size() <= 3) cats.add("Juices");

        double rating = 0;
        try { rating = Double.parseDouble(h[6]); } catch (Exception ignored) { }
        int votes = 0;
        try { votes = Integer.parseInt(h[7]); } catch (Exception ignored) { }
        String area = h[3].replace(", Coimbatore", "").trim();
        String cuisine = String.join(" · ", cuisines.subList(0, Math.min(2, cuisines.size())));
        int prep = (int) Math.round(menu.stream().mapToInt(Data.MenuItem::prepMin).average().orElse(8));
        String[] look = look(cl, h[1]);
        // Prefer a photo of a dish this restaurant actually sells; fall back to a cuisine photo.
        String cover = menu.stream().map(Data.MenuItem::photo).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        all.add(new Data.Restaurant(h[1], name, area, tidyAddress(h[4]), cuisine, rating, votes, lat, lon, prep,
            look[0], Integer.parseInt(look[1]), cats, List.copyOf(menu), cover));
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    private static String tidy(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 86) s = s.substring(0, s.lastIndexOf(' ', 84) > 40 ? s.lastIndexOf(' ', 84) : 84) + "…";
        return s;
    }

    private static String tidyName(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        // Title-case shouty names like "CHICKEN BIRYANI".
        if (s.equals(s.toUpperCase(Locale.ROOT)) && s.length() > 3) {
            StringBuilder b = new StringBuilder();
            for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
                if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                b.append(' ');
            }
            s = b.toString().trim();
        }
        return s;
    }

    private static String tidyAddress(String s) {
        return s.replaceAll(",\\s*Coimbatore$", "").replaceAll("\\s+", " ").trim();
    }

    /** Estimated minutes for the kitchen, from the dish name. */
    private static int prep(String dish, String cuisine) {
        String d = dish.toLowerCase(Locale.ROOT);
        if (has(d, "juice", "shake", "lassi", "tea", "coffee", "chai", "milk", "soda", "mojito", "lime")) return 3;
        if (has(d, "cake", "pastry", "sweet", "halwa", "laddu", "mysore pak", "chips", "mixture", "ice cream", "kulfi", "brownie")) return 2;
        if (has(d, "idli", "idly", "vada", "vadai", "pongal", "kesari", "upma", "uppuma")) return 4;
        if (has(d, "dosa", "dosai", "roast", "uthappam", "uttapam", "appam", "poori", "puri", "idiyappam")) return 7;
        if (has(d, "biryani", "briyani", "meals", "thali", "rice")) return 6;
        if (has(d, "parotta", "porotta", "parota", "kothu", "chapati", "naan", "roti", "paratha")) return 8;
        if (has(d, "noodle", "fried rice", "manchurian", "chilli", "momo")) return 10;
        if (has(d, "chicken", "mutton", "fish", "prawn", "egg", "kebab", "grill", "tandoori", "shawarma")) return 12;
        if (has(d, "pizza")) return 14;
        if (has(d, "burger", "sandwich", "fries", "wrap", "roll")) return 8;
        return has(cuisine, "south indian") ? 6 : 8;
    }

    private static int art(String dish) {
        String d = dish.toLowerCase(Locale.ROOT);
        if (has(d, "juice", "shake", "lassi", "tea", "coffee", "milk")) return 1;
        if (has(d, "dosa", "roast", "parotta", "burger", "sandwich", "pizza", "cake")) return 2;
        return 0;
    }

    /** Only use a photo when the dish clearly matches one; otherwise show the dish without a photo. */
    static String photoFor(String dish) {
        String d = dish.toLowerCase(Locale.ROOT);
        if (has(d, "combo", "pack of", "family", "bucket", "box")) return null;
        boolean meat = has(d, "chicken", "mutton", "liver", "fish", "prawn", "beef", "kozhi", "nattu", "kaadai", "crab");
        String[][] rules = {
            {"chicken 65 biryani", "chicken-65-biryani"}, {"mutton biryani", "mutton-biryani"}, {"mutton briyani", "mutton-biryani"},
            {"egg biryani", "egg-biryani"}, {"egg briyani", "egg-biryani"}, {"paneer biryani", "paneer-tikka-biryani"},
            {"veg biryani", "paneer-tikka-biryani"}, {"mushroom biryani", "paneer-tikka-biryani"},
            {"chicken biryani", "chicken-biryani"}, {"chicken briyani", "chicken-biryani"}, {"bamboo", "bamboo-chicken-biryani"},
            {"ghee rice", "ghee-rice"}, {"curd rice", "curd-rice"},
            {"mini tiffin", "mini-tiffin"}, {"podi idl", "ghee-podi-idly"}, {"sambar idl", "mini-idli-bowl"}, {"mini idl", "mini-idli-bowl"},
            {"idly", "mini-idli-bowl"}, {"idli", "mini-idli-bowl"}, {"sambar vada", "sambar-vadai"}, {"vada", "sambar-vadai"},
            {"pongal", "pongal"}, {"poori", "poori-set"}, {"puri", "poori-set"}, {"idiyappam", "idiyappam-set"},
            {"masal roast", "ghee-masal-roast"}, {"masala dosa", "ghee-masal-roast"}, {"masala roast", "ghee-masal-roast"},
            {"uthappam", "podi-uttapam"}, {"uttapam", "podi-uttapam"}, {"roast", "ghee-roast-dosa"}, {"dosa", "ghee-roast-dosa"},
            {"kesari", "kesari-bath"}, {"rose milk", "rose-milk"}, {"filter coffee", "filter-coffee"},
            {"cold coffee", "cold-coffee-frappe"}, {"frappe", "cold-coffee-frappe"},
            {"mango lassi", "mango-lassi"}, {"lassi", "sweet-lassi"}, {"pomegranate", "pomegranate-juice"},
            {"sugarcane", "sugarcane-juice"}, {"watermelon", "watermelon-mint-cooler"}, {"fruit salad", "seasonal-fruit-bowl"},
            {"brownie", "brownie-milkshake"}, {"chocolate shake", "brownie-milkshake"}, {"chocolate milkshake", "brownie-milkshake"},
            {"kothu", "egg-kothu-parotta"}, {"parotta", "parotta-salna"}, {"porotta", "parotta-salna"}, {"parota", "parotta-salna"},
            {"pallipalayam", "chicken-pallipalayam"}, {"chicken chukka", "chicken-chukka"}, {"pepper chicken", "chicken-chukka"},
            {"mutton chukka", "mutton-fry"}, {"mutton fry", "mutton-fry"}, {"mutton pepper", "mutton-fry"},
            {"hakka noodles", "hakka-noodles"}, {"noodles", "hakka-noodles"}, {"chilli paneer", "chilli-paneer-dry"},
            {"steam momo", "steamed-veg-momos"}, {"steamed momo", "steamed-veg-momos"}, {"fried momo", "chicken-kothey"},
            {"chicken burger", "grilled-chicken-burger"}, {"burger", "grilled-chicken-burger"},
            {"sandwich", "chicken-grilled-sandwich"}, {"peri peri fries", "peri-peri-fries"}, {"fries", "peri-peri-fries"},
            {"alfredo", "alfredo-veg-pasta"}, {"white sauce pasta", "alfredo-veg-pasta"}, {"croissant", "almond-croissant"},
            {"thukpa", "thukpa"}, {"chole", "chole-kulche"}, {"aloo paratha", "aloo-paratha-combo"}, {"paneer paratha", "paneer-paratha"},
            {"green curry", "thai-green-curry-rice"}, {"kimchi", "kimchi-fried-rice"}, {"falafel", "tahini-falafel-bowl"},
            {"mango chia", "mango-chia-pudding"}};
        java.util.Set<String> vegOnly = java.util.Set.of("mini-tiffin", "ghee-podi-idly", "mini-idli-bowl", "sambar-vadai", "pongal",
            "poori-set", "idiyappam-set", "ghee-masal-roast", "podi-uttapam", "ghee-roast-dosa", "kesari-bath", "curd-rice", "ghee-rice",
            "paneer-tikka-biryani", "chilli-paneer-dry", "steamed-veg-momos", "alfredo-veg-pasta", "aloo-paratha-combo", "paneer-paratha");
        for (String[] r : rules) if (d.contains(r[0]) && !(meat && vegOnly.contains(r[1]))) return r[1];
        return null;
    }

    /** {tint, art kind, cover photo} for a restaurant, from its cuisines. */
    private static String[] look(String cuisines, String slug) {
        int h = Math.floorMod(slug.hashCode(), 3);
        if (cuisines.startsWith("biryani") || (cuisines.contains("biryani") && !cuisines.startsWith("south indian")))
            return new String[]{"#8E5A2B", "0", new String[]{"thalappakatti", "haribhavanam", "chicken-biryani"}[h]};
        if (has(cuisines, "chettinad", "kongu", "arabian", "bbq", "kebab", "shawarma"))
            return new String[]{"#7A3B26", "2", "juniorkuppanna"};
        if (cuisines.startsWith("south indian") || cuisines.startsWith("tamil") || cuisines.startsWith("kerala"))
            return new String[]{"#B8892E", "2", new String[]{"anandhaas", "annapoorna", "mini-tiffin"}[h]};
        if (has(cuisines, "momos")) return new String[]{"#8A6A3A", "2", "momo"};
        if (cuisines.startsWith("chinese") || has(cuisines, "sichuan", "asian")) return new String[]{"#4E6A7A", "0", "wok"};
        if (cuisines.startsWith("north indian") || has(cuisines, "mughlai")) return new String[]{"#9A4E2E", "2", "paratha"};
        if (has(cuisines, "juices", "shake")) return new String[]{"#3F8F5A", "1", "pazhamudhir"};
        if (has(cuisines, "coffee", "tea", "cafe")) return new String[]{"#6A5A4A", "1", "brew"};
        if (has(cuisines, "burger", "fast food", "sandwich", "pizza", "rolls", "wraps")) return new String[]{"#5B5F8A", "2", "cafeinc"};
        if (has(cuisines, "bakery", "desserts", "mithai", "ice cream"))
            return new String[]{"#A5687A", "2", new String[]{"almond-croissant", "cardamom-bun", "brownie-milkshake"}[h]};
        if (has(cuisines, "healthy", "salad")) return new String[]{"#4F7F5A", "0", "green"};
        return new String[]{"#8E877C", "0", null};
    }
}
