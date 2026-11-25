# Ryuu SmartPickup — JavaFX prototype

> Order ahead. Skip the wait. Avoid delivery charges. Pick up on your way.

A clickable desktop prototype of the Smart Food Pickup idea (see `Startup_idea.txt`), built with plain JavaFX + CSS, set in **Coimbatore**.

## Run

```powershell
.\run.ps1
```

You need JDK 23+. On the first run it downloads the OpenJFX 26.0.2 jars from Maven Central into `lib/`.

## Real data (works offline)

- **Map:** streets, lakes, parks, railways and town names for the whole Coimbatore district (Mettupalayam to Valparai) from OpenStreetMap, bundled in `assets/coimbatore.map` and drawn by the app itself, so no internet is needed. Drag to pan, scroll to zoom (from the whole district down to street level); road names appear when zoomed in. Map data © OpenStreetMap contributors, available under the Open Database License (ODbL); `coimbatore.map` is a derived database under the same licence.
- **Route:** choose *From* and *To* in the top bar (32 real places across the district, from Gandhipuram to Pollachi and Valparai). The fastest road route is found on the bundled street map (one-way streets respected), so this works offline too.
- **Restaurants:** 297 real Coimbatore restaurants: 291 in `assets/restaurants.tsv` (exported from public Zomato listings on 2026-11-25: name, area, address, coordinates, rating, votes, and up to 12 dishes each with veg flags), plus 7 hand-finished ones in `Data.java` (one duplicate is skipped). "On your way" means within about 650 m of your road. Prices are illustrative (varied from the listed ones, not what the restaurants charge) and prep times are estimates. Dish photos are illustrative and only shown when the dish name clearly matches one; other dishes appear without a photo.

## Real photos

Put images in `assets/photos/` and they replace the placeholders automatically (cover-cropped):
- Restaurant cover: `<restaurant-id>.jpg`, where the id is `dosa`, `juice`, `brew`, `biryani`, `green`, `momo`, `wok` or `paratha`
- Dish: the dish name as a slug, e.g. `chicken-dum-biryani.jpg` or `ghee-roast-dosa.png`

The packaged exe reads them from `dist\RyuuSmartPickup\assets\photos\`. Build the exe with `package.ps1`; it copies photos, `restaurants.tsv` and `coimbatore.map` next to it.

To refresh the map data: download the Overpass extracts described at the top of `tools/PackMap.java`, then run `java tools/PackMap.java roads.json areas.json assets/coimbatore.map`.

## Screens

| Screen | What it shows |
|---|---|
| **How it works** (first launch, then from the sidebar) | A 4-step story: the problem (waiting, delivery fees, uncertainty) → order on your way → the kitchen is timed to your live ETA → walk in, walk out. |
| **Discover** | Your commute at a glance, category filters, *Along my route / Nearby*, and photo-led restaurant cards. |
| **Route** | A calm map of Home → Office with every restaurant you pass, when you pass it, and the detour. Travel mode changes the times. |
| **Restaurant & bag** | Cover photo, menu with dish photos, and a bag that shows the pickup time and when the kitchen starts. Checkout is a simple confirm sheet. |
| **Current order** | Status, step progress, the You vs Kitchen timing chart, and a pickup pass. A dashed *Prototype controls* panel speeds up the clock or simulates a 6 min delay, which re-times the kitchen. |
| **Activity** | Time and fees saved, two charts, and recent orders. |
| **Kitchen display** (restaurant side) | Tickets sorted by customer arrival, not order time: Scheduled → Cooking → Ready. Your live order appears here too. |

## Code map

`src/com/ryuu/pickup/`
- `App.java`: window shell, sidebar, top bar, navigation transitions, toasts, confetti
- `AppState.java` / `Order.java`: cart, active order, simulated clock
- `*View.java`: one class per screen
- `FoodArt.java`, `LiveCanvas.java`, `Backdrop.java`: procedural art and animation helpers
- `app.css`: the whole theme ("dragon fire at midnight")

Dev helper: `-Dryuu.snap=<dir>` walks through every screen and saves PNG screenshots.
