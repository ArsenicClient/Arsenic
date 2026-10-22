package arsenic.utils.minecraft;

import java.util.Optional;

import arsenic.utils.java.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Converting chat components to the plain and {@code §}-coded strings the client's own fonts and code work with. */
public class TextUtils extends UtilityClass {

    /** The text with its colours and styles as legacy {@code §} codes, as 1.8's getFormattedText gave it. */
    public static String toLegacy(Component component) {
        if (component == null)
            return "";
        StringBuilder out = new StringBuilder();
        String[] last = {""};
        component.visit((style, text) -> {
            if (text.isEmpty())
                return Optional.empty();
            String codes = codes(style);
            if (!codes.equals(last[0])) {
                // a reset only when formatting has to be dropped; colours reset the formats after them anyway
                out.append(codes.isEmpty() || !last[0].isEmpty() && codes.charAt(1) != last[0].charAt(1) ? "§r" + codes : codes);
                last[0] = codes;
            }
            out.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    /** The text without any formatting, as 1.8's getUnformattedText gave it. */
    public static String plain(Component component) {
        return component == null ? "" : stripCodes(component.getString());
    }

    public static String stripCodes(String text) {
        return text == null ? "" : ChatFormatting.stripFormatting(text);
    }

    // the legacy colour codes, in code order, and the colour each one stands for
    private static final String CODES = "0123456789abcdef";
    private static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static String codes(Style style) {
        StringBuilder sb = new StringBuilder();
        TextColor color = style.getColor();
        if (color != null)
            sb.append('§').append(nearest(color.getValue()));
        if (style.isBold()) sb.append("§l");
        if (style.isItalic()) sb.append("§o");
        if (style.isUnderlined()) sb.append("§n");
        if (style.isStrikethrough()) sb.append("§m");
        if (style.isObfuscated()) sb.append("§k");
        return sb.toString();
    }

    /** The legacy colour code closest to an RGB value; the named colours map to their own code exactly. */
    private static char nearest(int rgb) {
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            int c = RGB[i];
            long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255);
            long dg = ((c >> 8) & 255) - ((rgb >> 8) & 255);
            long db = (c & 255) - (rgb & 255);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return CODES.charAt(best);
    }
}
