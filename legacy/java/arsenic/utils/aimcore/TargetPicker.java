package arsenic.utils.aimcore;

import java.util.List;

public final class TargetPicker {

    public static final class Candidate {
        public final int id;
        public final float value;
        public final double boxDistance;
        public final float angle;

        public Candidate(int id, float value, double boxDistance, float angle) {
            this.id = id;
            this.value = value;
            this.boxDistance = boxDistance;
            this.angle = angle;
        }
    }

    public boolean timed = true;
    public float turnRate = 12f;
    public float switchPenalty = 4f;
    public double keepMargin = 0.5;
    public float valueScale = 1f;

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
