package arsenic.module.impl.player;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "ChatBypass", category = ModuleCategory.PLAYER, hidden = true)
public class ChatBypass extends Module {

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Filtered);

    private static final String VOWELS = "aeiouyAEIOUY";
    private static final String ACCENTED = "äëïöüÿÄËÏÖÜŸ";

    private static final String[] STEMS = {
            "fuck", "shit", "bitch", "cunt", "bastard", "whore", "slut", "pussy", "wank", "bollock",
            "asshole", "dickhead", "cocksuck", "motherf"
    };
    private static final Pattern SHORT_WORDS = Pattern.compile(
            "(?i)(ass|arse|dick|cock|piss|damn|crap|prick|twat|tit)(s|es|ed|ing|y|hole|head)?");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    private boolean resending;

    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (resending || !(event.getPacket() instanceof ServerboundChatPacket) || mc.getConnection() == null)
            return;

        String original = ((ServerboundChatPacket) event.getPacket()).message();
        if (original.startsWith("/") || original.startsWith("."))
            return;

        String rewritten = bypass(original);
        if (rewritten.equals(original))
            return;

        event.setCancelled(true);
        resending = true;
        try {
            mc.getConnection().sendChat(rewritten);
        } finally {
            resending = false;
        }
    };

    private String bypass(String message) {
        Matcher m = WORD.matcher(message);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String word = m.group();
            boolean hit = mode.getValue() == Mode.All ? word.length() > 2 : isFlagged(word);
            m.appendReplacement(out, Matcher.quoteReplacement(hit ? accentFirstVowel(word) : word));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean isFlagged(String word) {
        String lower = word.toLowerCase();
        for (String stem : STEMS)
            if (lower.contains(stem)) return true;
        return SHORT_WORDS.matcher(lower).matches();
    }

    private static String accentFirstVowel(String word) {
        for (int i = 0; i < word.length(); i++) {
            int v = VOWELS.indexOf(word.charAt(i));
            if (v >= 0)
                return word.substring(0, i) + ACCENTED.charAt(v) + word.substring(i + 1);
        }
        return word;
    }

    public enum Mode {
        Filtered, All
    }
}
