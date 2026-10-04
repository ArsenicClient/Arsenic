package arsenic.utils.aimcore;

import java.util.List;

/**
 * Chooses which of the candidates KillAura aims at (no Minecraft classes).
 * <p>
 * The original took the first candidate in sort order that was in range, re-decided every tick.
 * In a crowd that flips the target whenever the best one steps across the range edge or the sort
 * order shuffles (ties under SmartSwitch break by the world's entity order), and every flip is a
 * fresh flick across the screen. The timed pick instead estimates how soon each candidate could be
 * hit - its sort value as ticks until it can take damage again, plus the ticks needed to turn onto
 * it - and only leaves the current target for one that is clearly sooner.
 */
public final class TargetPicker {

    public static final class Candidate {
        public final int id;
        /** The sort mode's value (lower is better); for SmartSwitch, ticks until it can be hurt again. */
        public final float value;
        public final double boxDistance;
        /** Degrees we'd have to turn (largest of yaw and pitch) to look at it. */
        public final float angle;

        public Candidate(int id, float value, double boxDistance, float angle) {
            this.id = id;
            this.value = value;
            this.boxDistance = boxDistance;
            this.angle = angle;
        }
    }

    /** Off = the original first-in-range pick. */
    public boolean timed = true;
    /** Degrees per tick assumed for turning onto a new target when costing it. */
    public float turnRate = 12f;
    /** Extra ticks charged for leaving the current target. */
    public float switchPenalty = 4f;
    /** The current target stays eligible this far past the aim range. */
    public double keepMargin = 0.5;
    /** Scale from the sort value to ticks (1 for SmartSwitch, whose value already is ticks). */
    public float valueScale = 1f;

    /** Index of the candidate to aim at, or -1. {@code sorted} is best first by the sort mode. */
    public int pick(List<Candidate> sorted, int currentId, double aimRange) {
        if (!timed) {
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).boxDistance <= aimRange) return i;
            }
            return -1;
        }
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            Candidate c = sorted.get(i);
            boolean current = c.id == currentId;
            if (c.boxDistance > aimRange + (current ? keepMargin : 0)) continue;
            double cost = c.value * valueScale + c.angle / turnRate + (current ? 0 : switchPenalty);
            if (cost < bestCost - 1e-6) {
                bestCost = cost;
                best = i;
            }
        }
        return best;
    }
}
