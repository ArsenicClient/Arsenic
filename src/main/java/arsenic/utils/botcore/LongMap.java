package arsenic.utils.botcore;

/**
 * Open-addressing hash map from long keys (packed block positions) to values. Java's HashMap with
 * boxed Long keys hashed these badly (the packed x/y/z bits cancel out in Long.hashCode), which made
 * it the search's biggest cost; this mixes the bits properly and avoids boxing.
 */
final class LongMap<V> {
    private static final long EMPTY = Long.MIN_VALUE;
    private long[] keys;
    private Object[] vals;
    private int size;
    private int mask;

    LongMap(int capacity) {
        int cap = Integer.highestOneBit(Math.max(16, capacity) * 2 - 1);
        keys = new long[cap];
        vals = new Object[cap];
        java.util.Arrays.fill(keys, EMPTY);
        mask = cap - 1;
    }

    private static int mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int) k;
    }

    @SuppressWarnings("unchecked")
    V get(long k) {
        int i = mix(k) & mask;
        while (true) {
            long kk = keys[i];
            if (kk == k) return (V) vals[i];
            if (kk == EMPTY) return null;
            i = (i + 1) & mask;
        }
    }

    void put(long k, V v) {
        if (size * 2 >= keys.length) grow();
        int i = mix(k) & mask;
        while (true) {
            long kk = keys[i];
            if (kk == k) {
                vals[i] = v;
                return;
            }
            if (kk == EMPTY) {
                keys[i] = k;
                vals[i] = v;
                size++;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        long[] ok = keys;
        Object[] ov = vals;
        keys = new long[ok.length * 2];
        vals = new Object[ok.length * 2];
        java.util.Arrays.fill(keys, EMPTY);
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) {
            if (ok[i] != EMPTY) put(ok[i], (V) ov[i]);
        }
    }
}
