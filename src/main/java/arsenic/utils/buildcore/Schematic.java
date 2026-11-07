package arsenic.utils.buildcore;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads Sponge schematics (.schem, versions 2 and 3, what WorldEdit writes) into a {@link Blueprint}. Block entities,
 * entities and biomes are ignored.
 */
public final class Schematic {
    private Schematic() {
    }

    public static Blueprint read(InputStream raw) throws IOException {
        BufferedInputStream buf = new BufferedInputStream(raw);
        buf.mark(2);
        int b0 = buf.read(), b1 = buf.read();
        buf.reset();
        InputStream in = b0 == 0x1f && b1 == 0x8b ? new GZIPInputStream(buf) : buf;
        Map<String, Object> root = readRoot(new DataInputStream(in));
        // version 3 wraps everything in a "Schematic" compound
        Object wrapped = root.get("Schematic");
        if (wrapped instanceof Map) root = cast(wrapped);

        int w = num(root.get("Width")), h = num(root.get("Height")), l = num(root.get("Length"));
        Map<String, Object> palette;
        byte[] data;
        Object blocks = root.get("Blocks");
        if (blocks instanceof Map) {
            Map<String, Object> bm = cast(blocks);
            palette = cast(bm.get("Palette"));
            data = (byte[]) bm.get("Data");
        } else {
            palette = cast(root.get("Palette"));
            data = (byte[]) root.get("BlockData");
        }
        if (palette == null || data == null) throw new IOException("not a Sponge schematic (no palette or block data)");

        Blueprint bp = new Blueprint(w, h, l);
        int[] map = new int[palette.size() + 1];
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            int idx = num(e.getValue());
            if (idx >= map.length) map = java.util.Arrays.copyOf(map, idx + 1);
            map[idx] = bp.idOf(e.getKey());
        }
        int pos = 0;
        for (int i = 0; i < w * h * l; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (pos >= data.length) throw new IOException("block data ends early");
                int b = data[pos++];
                value |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            int x = i % w, z = (i / w) % l, y = i / (w * l);
            bp.set(x, y, z, value < map.length ? map[value] : Blueprint.AIR);
        }
        return bp;
    }

    /** Writes a Sponge version 3 schematic (gzipped); {@link Blueprint#ANY} cells are written as air. */
    public static void write(Blueprint bp, java.io.OutputStream raw, int dataVersion) throws IOException {
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int v = Math.max(0, bp.get(x, y, z));
                    while ((v & ~0x7f) != 0) {
                        data.write((v & 0x7f) | 0x80);
                        v >>>= 7;
                    }
                    data.write(v);
                }
        java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(raw);
        java.io.DataOutputStream out = new java.io.DataOutputStream(gz);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(10);
        out.writeUTF("Schematic");
        out.writeByte(3);
        out.writeUTF("Version");
        out.writeInt(3);
        out.writeByte(3);
        out.writeUTF("DataVersion");
        out.writeInt(dataVersion);
        String[] names = {"Width", "Height", "Length"};
        int[] sizes = {bp.sizeX, bp.sizeY, bp.sizeZ};
        for (int i = 0; i < 3; i++) {
            out.writeByte(2);
            out.writeUTF(names[i]);
            out.writeShort(sizes[i]);
        }
        out.writeByte(11);
        out.writeUTF("Offset");
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeByte(10);
        out.writeUTF("Blocks");
        out.writeByte(10);
        out.writeUTF("Palette");
        for (int i = 0; i < bp.palette.size(); i++) {
            out.writeByte(3);
            out.writeUTF(bp.palette.get(i));
            out.writeInt(i);
        }
        out.writeByte(0);
        out.writeByte(7);
        out.writeUTF("Data");
        out.writeInt(data.size());
        data.writeTo(out);
        out.writeByte(9);
        out.writeUTF("BlockEntities");
        out.writeByte(10);
        out.writeInt(0);
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
        gz.finish();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static int num(Object o) {
        if (o == null) throw new IllegalArgumentException("missing size tag");
        return ((Number) o).intValue();
    }

    // ---- NBT -----------------------------------------------------------------------------------------

    private static Map<String, Object> readRoot(DataInputStream in) throws IOException {
        int type = in.readByte();
        if (type != 10) throw new IOException("NBT root is not a compound");
        in.readUTF();
        return cast(readTag(in, 10));
    }

    private static Object readTag(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                int elem = in.readByte();
                int n = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) list.add(readTag(in, elem));
                return list;
            }
            case 10: {
                Map<String, Object> m = new HashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) return m;
                    String name = in.readUTF();
                    m.put(name, readTag(in, t));
                }
            }
            case 11: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("bad NBT tag type " + type);
        }
    }
}
