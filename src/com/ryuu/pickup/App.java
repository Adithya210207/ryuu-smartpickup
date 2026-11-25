package com.ryuu.pickup;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ryuu Pickup: order ahead, food is ready when you arrive. */
public class App extends Application {
    private final StackPane root = new StackPane();
    private final StackPane content = new StackPane();
    private final Map<String, Button> nav = new LinkedHashMap<>();
    private AppState state;
    private String current;
    private Node toast;
    Node intro;

    private static final File INTRO_SEEN = new File(System.getProperty("user.home"), ".ryuu-pickup-intro-seen");

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        state = new AppState(this);

        BorderPane main = new BorderPane();
        main.setTop(buildTopBar());
        main.setCenter(content);

        BorderPane shell = new BorderPane();
        shell.setLeft(buildSidebar());
        shell.setCenter(main);
        root.getChildren().add(shell);

        // Fit inside the screen's usable area so the title bar (close / minimise) stays visible.
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double sw = Math.min(1440, vb.getWidth() - 40), sh = Math.min(900, vb.getHeight() - 60);
        Scene scene = new Scene(root, sw, sh);
        scene.setFill(Color.web("#F6F4F0"));
        scene.getStylesheets().add(App.class.getResource("app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Ryuu Pickup");
        for (String base : new String[]{System.getProperty("user.dir"), System.getProperty("ryuu.home", "")}) {
            java.io.File ic = new java.io.File(base, "assets/icon.png");
            if (ic.isFile()) { stage.getIcons().add(new javafx.scene.image.Image(ic.toURI().toString(), 256, 256, true, true)); break; }
        }
        stage.setMinWidth(Math.min(1180, vb.getWidth()));
        stage.setMinHeight(Math.min(680, vb.getHeight()));
        stage.setX(vb.getMinX() + (vb.getWidth() - sw) / 2);
        stage.setY(vb.getMinY() + 10);
        boolean snap = System.getProperty("ryuu.snap") != null;
        if (!snap) stage.setMaximized(true);
        stage.show();
        navigate("discover");

        if (snap) {
            showIntro();
            Snapshots.run(this, state, scene, System.getProperty("ryuu.snap"));
        } else if (!INTRO_SEEN.exists()) {
            showIntro();
            try { INTRO_SEEN.createNewFile(); } catch (Exception ignored) { }
        }
    }

    // ---------------------------------------------------------------- navigation

    public void navigate(String key) { navigate(key, false); }

    public void navigate(String key, boolean force) {
        if (key.equals(current) && !force) return;
        current = key;
        // A restaurant page belongs to Discover; the Bag item is only highlighted when opened as the bag.
        String hl = key.equals("menu") ? "discover" : key;
        nav.forEach((k, b) -> b.pseudoClassStateChanged(Fx.ACTIVE, k.equals(hl)));
        Node view = switch (key) {
            case "route" -> RouteView.create(state);
            case "menu" -> MenuView.create(state);
            case "tracker" -> TrackerView.create(state);
            case "insights" -> InsightsView.create(state);
            case "kitchen" -> KitchenView.create(state);
            default -> DiscoverView.create(state);
        };
        content.getChildren().setAll(view);
    }

    /** Opens the restaurant page of whatever is in the bag, with the Bag item highlighted. */
    public void openBag() {
        if (state.cartRestaurant != null) state.restaurant.set(state.cartRestaurant);
        navigate("menu", true);
        nav.forEach((k, b) -> b.pseudoClassStateChanged(Fx.ACTIVE, k.equals("menu")));
    }

    public void showIntro() {
        if (intro != null) root.getChildren().remove(intro);
        intro = IntroView.create(this);
        root.getChildren().add(intro);
    }

    // ---------------------------------------------------------------- chrome

    private Node brandMark() {
        Canvas c = new Canvas(30, 30);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setStroke(Color.WHITE);
        g.setLineWidth(2.2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeArc(8, 8, 14, 14, 110, 300, ArcType.OPEN);
        g.setFill(Color.web("#E0704A"));
        g.fillOval(16.4, 6.2, 4.6, 4.6);
        StackPane mark = new StackPane(c);
        mark.getStyleClass().add("brand-mark");
        return mark;
    }

    private Node buildSidebar() {
        VBox word = new VBox(-4, Fx.label("Ryuu", "brand-name"), Fx.label("Pickup", "brand-sub"));
        HBox brand = new HBox(10, brandMark(), word);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 0, 8));

        VBox customer = new VBox(2,
            navItem("discover", Icons.EXPLORE, "Discover", null),
            navItem("route", Icons.ROUTE, "Route", null));
        Label bagBadge = Fx.label("0", "nav-badge");
        bagBadge.textProperty().bind(state.cartCount.asString());
        bagBadge.visibleProperty().bind(state.cartCount.greaterThan(0));
        customer.getChildren().add(navItem("menu", Icons.BAG, "Bag", bagBadge));
        Circle live = new Circle(3.5);
        live.getStyleClass().add("live-dot");
        live.visibleProperty().bind(state.order.isNotNull());
        customer.getChildren().add(navItem("tracker", Icons.TIMER, "Current order", live));
        customer.getChildren().add(navItem("insights", Icons.INSIGHTS, "Activity", null));

        VBox partner = new VBox(2, navItem("kitchen", Icons.FLAME, "Kitchen display", null));

        Button how = new Button("How it works", Fx.icon(Icons.EXPLORE, 14, "icon-muted"));
        how.getStyleClass().add("side-link");
        how.setOnAction(e -> showIntro());

        StackPane avatar = new StackPane(Fx.icon(Icons.PERSON, 16, "icon-muted"));
        avatar.getStyleClass().add("avatar");
        HBox account = new HBox(10, avatar, new VBox(0, Fx.label("Account", "title-s"), Fx.label("UPI · default", "meta")));
        account.setAlignment(Pos.CENTER_LEFT);
        account.getStyleClass().add("account");

        VBox side = new VBox(10, brand, gap(14), customer, gap(14), Fx.label("For restaurants", "side-heading"), partner,
            Fx.spacer(), how, account);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(232);
        side.setMinWidth(232);
        return side;
    }

    private static Region gap(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        return r;
    }

    private Node navItem(String key, String icon, String text, Node trailing) {
        Button b = new Button(text, Fx.icon(icon, 16, "nav-icon"));
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> { if (key.equals("menu")) openBag(); else navigate(key); });
        nav.put(key, b);
        StackPane sp = new StackPane(b);
        if (trailing != null) {
            trailing.setMouseTransparent(true);
            sp.getChildren().add(trailing);
            StackPane.setAlignment(trailing, Pos.CENTER_RIGHT);
            StackPane.setMargin(trailing, new Insets(0, 12, 0, 0));
        }
        return sp;
    }

    private Node buildTopBar() {
        javafx.scene.control.ComboBox<CommuteRoute.Place> fromBox = placeBox(state.from);
        javafx.scene.control.ComboBox<CommuteRoute.Place> toBox = placeBox(state.to);
        Button swap = new Button(null, Fx.icon(Icons.SWAP, 13, "icon-muted"));
        swap.getStyleClass().add("swap-btn");
        swap.setOnAction(e -> state.swapRoute());
        Label trip = Fx.label("", "meta-ink");
        trip.textProperty().bind(state.travelMin.asString("%d min"));
        HBox context = new HBox(6, Fx.label("From", "meta"), fromBox, swap, Fx.label("To", "meta"), toBox,
            Fx.label("·", "meta"), trip);
        context.setAlignment(Pos.CENTER_LEFT);
        context.getStyleClass().add("context");

        TextField field = new TextField();
        field.setPromptText("Search restaurants and dishes");
        field.textProperty().bindBidirectional(state.query);
        field.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.isBlank() && !"discover".equals(current)) navigate("discover");
        });
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox search = new HBox(8, Fx.icon(Icons.SEARCH, 15, "icon-muted"), field);
        search.getStyleClass().add("search");
        search.setAlignment(Pos.CENTER_LEFT);
        search.setPrefWidth(360);

        Button bag = new Button(null, Fx.icon(Icons.BAG, 16, "icon-ink"));
        bag.getStyleClass().add("icon-btn");
        bag.setOnAction(e -> openBag());
        Label badge = Fx.label("", "bag-badge");
        badge.textProperty().bind(state.cartCount.asString());
        badge.visibleProperty().bind(state.cartCount.greaterThan(0));
        badge.setMouseTransparent(true);
        StackPane bagWrap = new StackPane(bag, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        badge.setTranslateX(5);
        badge.setTranslateY(-5);

        HBox bar = new HBox(12, context, Fx.spacer(), search, bagWrap);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private static javafx.scene.control.ComboBox<CommuteRoute.Place> placeBox(
            javafx.beans.property.ObjectProperty<CommuteRoute.Place> prop) {
        javafx.scene.control.ComboBox<CommuteRoute.Place> box =
            new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(CommuteRoute.PLACES));
        box.getStyleClass().add("place-box");
        box.setValue(prop.get());
        box.setVisibleRowCount(12);
        box.valueProperty().addListener((o, a, b) -> { if (b != null && b != prop.get()) prop.set(b); });
        prop.addListener((o, a, b) -> { if (box.getValue() != b) box.setValue(b); });
        box.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(CommuteRoute.Place p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); setText(null); return; }
                VBox v = new VBox(0, Fx.label(p.name(), "title-s"), Fx.label(p.area(), "meta"));
                setText(null);
                setGraphic(v);
            }
        });
        return box;
    }

    // ---------------------------------------------------------------- overlays

    /** One quiet notification at a time, bottom centre. */
    public void toast(String msg) {
        if (toast != null) root.getChildren().remove(toast);
        HBox t = new HBox(Fx.label(msg, "toast-text"));
        t.getStyleClass().add("toast");
        t.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        t.setMouseTransparent(true);
        StackPane.setAlignment(t, Pos.BOTTOM_CENTER);
        StackPane.setMargin(t, new Insets(0, 0, 28, 232));
        toast = t;
        root.getChildren().add(t);
        FadeTransition out = new FadeTransition(Duration.millis(200), t);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(new PauseTransition(Duration.seconds(3.2)), out);
        seq.setOnFinished(e -> { root.getChildren().remove(t); if (toast == t) toast = null; });
        seq.play();
    }

    public void overlay(Node n) { root.getChildren().add(n); }

    public void removeOverlay(Node n) { root.getChildren().remove(n); }

    void removeTopOverlay() {
        if (root.getChildren().size() > 1) root.getChildren().remove(root.getChildren().size() - 1);
    }
}
