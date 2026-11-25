package com.ryuu.pickup;

import java.util.List;
import java.util.Set;

 * Real Coimbatore restaurants. Names, addresses, coordinates, ratings and dishes come from their
 * public listings (November 2026). Prices are illustrative, not the restaurants' own; prep times are estimates.
 * come from their public Zomato listings (November 2026). Prep times are estimates.
 * Photos are illustrative, not the restaurants' own. Art kinds: 0 = bowl, 1 = cup, 2 = plate.
 */
final class Data {
    private Data() {}

    /** photo: file key in assets/photos, or null to show the dish without a photo. */
    record MenuItem(String name, String desc, int price, int prepMin, String tag, boolean veg, int art, String photo) {
        MenuItem(String name, String desc, int price, int prepMin, String tag, boolean veg, int art) {
            this(name, desc, price, prepMin, tag, veg, art, FoodArt.slug(name));
        }
    }

    record Restaurant(String id, String name, String area, String address, String cuisine, double rating, int reviews,
                      double lat, double lon, int prepMin, String c1, int art, Set<String> cats, List<MenuItem> menu,
                      String cover) {
        Restaurant(String id, String name, String area, String address, String cuisine, double rating, int reviews,
                   double lat, double lon, int prepMin, String c1, int art, Set<String> cats, List<MenuItem> menu) {
            this(id, name, area, address, cuisine, rating, reviews, lat, lon, prepMin, c1, art, cats, menu, id);
        }

        int fromPrice() { return menu.stream().mapToInt(MenuItem::price).min().orElse(0); }

        private double[] proj() { return CommuteRoute.current.projection(this); }

        /** Within walking distance of the road you're taking. */
        boolean onRoute() { return proj()[0] <= CommuteRoute.ON_ROUTE_M; }

        /** How far along your route (0 = start, 1 = destination). */
        double routePos() { return proj()[1]; }

        /** Extra minutes to step off the route and back. */
        int detourMin() {
            double d = proj()[0];
            return d < 150 ? 0 : (int) Math.max(1, Math.round(d * 2 / 250.0));
        }

        /** Road distance from your starting point, roughly. */
        double distanceKm() {
            CommuteRoute.Place f = CommuteRoute.current.from;
            return Math.round(CommuteRoute.haversine(f.lat(), f.lon(), lat, lon) * 1.3 / 100.0) / 10.0;
        }
    }

    static Restaurant byId(String id) {
        return RESTAURANTS.stream().filter(r -> r.id().equals(id)).findFirst().orElse(RESTAURANTS.get(0));
    }

    static final List<String> CATEGORIES = List.of("All", "Breakfast", "Biryani", "Non-veg", "Chinese", "Cafe", "Sweets", "Juices");

    /** Hand-finished restaurants first, then everything from assets/restaurants.tsv. */
    static final List<Restaurant> RESTAURANTS = RestaurantLoader.withFile(List.of(
        new Restaurant("annapoorna", "Sree Annapoorna Sree Gowrishankar", "Peelamedu",
            "20 A&B, Avinashi Road, opposite KMCH, Peelamedu", "South Indian · Vegetarian", 4.5, 42_700,
            11.04092, 77.04163, 8, "#C9822B", 2, Set.of("Breakfast"), List.of(
                new MenuItem("Ghee Roast", "Thin, crisp dosa roasted in ghee.", 160, 7, "Popular", true, 2),
                new MenuItem("Ghee Masal Roast", "Ghee roast with potato masala.", 149, 8, null, true, 2),
                new MenuItem("Ghee Mini Sambar Idly", "10 pieces in sambar.", 110, 4, null, true, 0),
                new MenuItem("Pongal", "With coconut chutney and sambar.", 105, 4, null, true, 0),
                new MenuItem("Sambar Vadai", "", 65, 3, null, true, 0),
                new MenuItem("Podi Uthappam", "", 100, 8, null, true, 2),
                new MenuItem("Rose Milk", "", 100, 2, null, true, 1),
                new MenuItem("Annapoorna Coffee (pack of 4)", "Filter coffee to take away.", 230, 3, null, true, 1))),
        new Restaurant("anandhaas", "Shree Anandhaas", "RS Puram",
            "180, East Sambandam Road, near Airtel office, RS Puram", "South Indian · Vegetarian", 4.5, 18_100,
            11.00851, 76.95115, 7, "#B8892E", 2, Set.of("Breakfast"), List.of(
                new MenuItem("Mini Tiffin", "Mini idli, mini dosa, pongal, vada and kesari.", 215, 9, "Popular", true, 2),
                new MenuItem("Ghee Podi Idly Set", "", 105, 4, null, true, 2),
                new MenuItem("Ghee Masal Roast", "", 155, 8, null, true, 2),
                new MenuItem("Poori Set (3 nos)", "With potato masala.", 120, 6, null, true, 2),
                new MenuItem("Idiyappam Set", "With coconut milk and kurma.", 115, 6, null, true, 2),
                new MenuItem("Kesari", "", 46, 2, null, true, 0))),
        new Restaurant("pazhamudhir", "Kovai Pazhamudhir Nilayam", "Saibaba Colony",
            "248/12, Mettupalayam Road, Nesavaalar Colony, Saibaba Colony", "Juices · Shakes", 4.0, 21,
            11.02312, 76.95158, 3, "#3F8F5A", 1, Set.of("Juices"), List.of(
                new MenuItem("Pomegranate Juice", "", 85, 3, null, true, 1),
                new MenuItem("Watermelon Juice", "", 30, 2, null, true, 1),
                new MenuItem("Sugarcane Juice", "With lemon and ginger.", 40, 2, null, true, 1),
                new MenuItem("Mango Lassi", "", 35, 2, null, true, 1),
                new MenuItem("Rose Milk Thick Shake", "", 60, 3, null, true, 1),
                new MenuItem("Fruit Salad", "", 55, 3, null, true, 0))),
        new Restaurant("haribhavanam", "Haribhavanam", "Gandhipuram",
            "RTO Office Road, Gandhipuram", "Chettinad · Biryani", 4.1, 6_484,
            11.00839, 76.97535, 10, "#A2462D", 0, Set.of("Biryani", "Non-veg"), List.of(
                new MenuItem("Chicken Biryani", "", 410, 6, "Popular", false, 0),
                new MenuItem("Mutton Biryani", "", 349, 6, null, false, 0),
                new MenuItem("Bamboo Chicken Biryani", "Cooked and served in bamboo.", 430, 8, null, false, 0),
                new MenuItem("Egg Biryani (2 eggs)", "", 269, 6, null, false, 0),
                new MenuItem("Ghee Rice", "", 250, 5, null, true, 0),
                new MenuItem("Curd Rice", "", 145, 3, null, true, 0))),
        new Restaurant("thalappakatti", "Dindigul Thalappakatti", "Gandhipuram",
            "350, 7th Street, Cross Cut Road, Gandhipuram", "Biryani · Since 1957", 4.3, 14_400,
            11.01773, 76.96640, 8, "#8E5A2B", 0, Set.of("Biryani", "Non-veg"), List.of(
                new MenuItem("Thalappakatti Chicken Biryani", "Seeraga samba rice.", 280, 6, "Popular", false, 0),
                new MenuItem("Thalappakatti Naidu Mutton Biryani", "", 379, 7, null, false, 0),
                new MenuItem("Thalappakatti Chicken 65 Biryani", "", 319, 7, null, false, 0),
                new MenuItem("Thalappakatti Mutton Kola Biryani", "With mutton meatballs.", 275, 7, null, false, 0),
                new MenuItem("Kongu Ghee 555 Paneer Biryani XL", "", 299, 7, null, true, 0))),
        new Restaurant("juniorkuppanna", "Hotel Junior Kuppanna", "Kavundampalayam",
            "55, Surya Zone, GN Mills, Mettupalayam Road, Kavundampalayam", "Kongu · Non-veg", 4.0, 2_273,
            11.06346, 76.94408, 12, "#7A3B26", 2, Set.of("Non-veg"), List.of(
                new MenuItem("Chicken Pallipalayam", "Dry chicken with coconut and red chillies.", 300, 12, "Popular", false, 2),
                new MenuItem("Chicken Chukka", "", 319, 12, null, false, 2),
                new MenuItem("Mutton Fry", "", 320, 14, null, false, 2),
                new MenuItem("2 Parotta with Non Veg Gravy", "", 95, 8, null, false, 2),
                new MenuItem("Egg Kothu Parotta with Gravy", "", 229, 10, null, false, 2))),
        new Restaurant("cafeinc", "Cafe INC", "Saravanampatti",
            "82/8, Saravanampatti–Thudiyalur Road, opposite Saro Complex", "Cafe · Burgers · Pasta", 4.2, 1_091,
            11.08333, 76.98549, 12, "#5B5F8A", 1, Set.of("Cafe"), List.of(
                new MenuItem("Grilled Chicken Burger", "", 339, 10, "Popular", false, 2),
                new MenuItem("Chicken Grilled Sandwich", "", 209, 8, null, false, 2),
                new MenuItem("Alfredo Veg Pasta", "", 325, 14, null, true, 0),
                new MenuItem("Peri Peri Fries", "", 170, 7, null, true, 2),
                new MenuItem("Cold Coffee Frappe", "", 180, 4, null, true, 1),
                new MenuItem("Brownie Milkshake", "", 269, 4, null, true, 1)))
    ));
}
