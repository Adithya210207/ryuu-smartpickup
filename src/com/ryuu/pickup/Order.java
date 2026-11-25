package com.ryuu.pickup;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/** An active pickup order. Time is simulated in seconds since placement. */
final class Order {
    final Data.Restaurant r;
    final List<AppState.CartLine> lines;
    final int total;
    int travelMin;
    int delayMin;
    double prevCookStart = -1, prevReady = -1;
    final int prepMin;
    final String code;
    final int counter;
    final LocalTime placedAt = LocalTime.now();

    double sim;
    boolean collected, cookingAnnounced, readyAnnounced;

    Order(Data.Restaurant r, List<AppState.CartLine> lines, int total, int travelMin, int prepMin, String code, int counter) {
        this.r = r;
        this.lines = lines;
        this.total = total;
        this.travelMin = Math.max(1, travelMin);
        this.prepMin = Math.max(1, prepMin);
        this.code = code;
        this.counter = counter;
    }

    double travelSec() { return travelMin * 60.0; }
    double cookStartSec() { return Math.max(0, (travelMin - prepMin) * 60.0); }
    double readySec() { return cookStartSec() + prepMin * 60.0; }
    double endSec() { return Math.max(travelSec(), readySec()); }
    double travelProgress() { return Fx.clamp(sim / travelSec()); }
    double cookProgress() { return Fx.clamp((sim - cookStartSec()) / (prepMin * 60.0)); }

    /** Live ETA changed (e.g. traffic). Before cooking starts, the kitchen simply starts later. */
    boolean canResync() { return !collected && sim < cookStartSec() - 30; }

    void delay(int minutes) {
        prevCookStart = cookStartSec();
        prevReady = readySec();
        travelMin += minutes;
        delayMin += minutes;
        cookingAnnounced = false;
        readyAnnounced = false;
    }

    /** 0 confirmed, 1 scheduled, 2 cooking, 3 packing, 4 ready, 5 collected */
    int stage() {
        if (collected) return 5;
        if (sim >= readySec()) return 4;
        if (sim >= readySec() - 60) return 3;
        if (sim >= cookStartSec()) return 2;
        if (sim >= 20) return 1;
        return 0;
    }

    LocalTime at(double sec) { return placedAt.plusSeconds((long) sec); }

    String itemsSummary() {
        return lines.stream().map(l -> l.qty + "× " + l.item.name()).collect(Collectors.joining(", "));
    }

    String mainItem() { return lines.isEmpty() ? "your order" : lines.get(0).item.name(); }
}
