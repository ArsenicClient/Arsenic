package arsenic.runtime.hooks;

import arsenic.event.impl.EventPacket;
import arsenic.runtime.Access;
import arsenic.main.Arsenic;
import arsenic.utils.render.capture.SoundCapture;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S29PacketSoundEffect;

import java.util.Locale;
import java.util.Map;

/** Network and sound hooks. */
public final class MiscHooks {

    private static final Access.FieldRef PLAYING_SOUNDS = Access.field(SoundManager.class, "playingSounds");
    private static final Access.FieldRef INV_PLAYING_SOUNDS = Access.field(SoundManager.class, "invPlayingSounds");
    private static final Access.FieldRef PLAYING_SOUND_POOL_ENTRIES = Access.field(SoundManager.class, "playingSoundPoolEntries");

    private MiscHooks() {}

    // ---- NetworkManager ----

    /** sendPacket(Packet) HEAD. @return true to cancel */
    public static boolean sendPacketHead(NetworkManager self, Packet packet) {
        EventPacket e = new EventPacket.OutGoing(packet);
        Arsenic.getArsenic().getEventManager().post(e);
        return e.isCancelled();
    }

    /** channelRead0 HEAD. @return true to cancel */
    public static boolean channelRead0Head(NetworkManager self, ChannelHandlerContext ctx, Packet packet) {
        // servers can probe for the client by playing sounds from our resource domain
        if (packet instanceof S29PacketSoundEffect) {
            String name = ((S29PacketSoundEffect) packet).getSoundName();
            if (name != null && name.trim().toLowerCase(Locale.ROOT).startsWith("arsenic:"))
                return true;
        }
        EventPacket e = new EventPacket.Incoming.Pre(packet);
        Arsenic.getArsenic().getEventManager().post(e);
        return e.isCancelled();
    }

    /** channelRead0 RETURN. */
    public static void channelRead0Return(NetworkManager self, ChannelHandlerContext ctx, Packet packet) {
        Arsenic.getArsenic().getEventManager().post(new EventPacket.Incoming.Post(packet));
    }

    // ---- SoundManager: feeds the Recorder's SoundCapture with what the sound system is told to play ----

    /** playSound RETURN: the sound and its chosen variant are only known once it has been handed over. */
    public static void playSoundReturn(SoundManager self, ISound sound) {
        if (!SoundCapture.isActive())
            return;
        Map<ISound, String> invPlayingSounds = INV_PLAYING_SOUNDS.get(self);
        Map<ISound, SoundPoolEntry> playingSoundPoolEntries = PLAYING_SOUND_POOL_ENTRIES.get(self);
        String channel = invPlayingSounds.get(sound);
        SoundPoolEntry entry = playingSoundPoolEntries.get(sound);
        if (channel != null && entry != null)
            SoundCapture.onPlay(channel, sound, entry);
    }

    /** stopSound HEAD. */
    public static void stopSoundHead(SoundManager self, ISound sound) {
        if (!SoundCapture.isActive())
            return;
        Map<ISound, String> invPlayingSounds = INV_PLAYING_SOUNDS.get(self);
        String channel = invPlayingSounds.get(sound);
        if (channel != null)
            SoundCapture.onStop(channel);
    }

    /** stopAllSounds HEAD. */
    public static void stopAllSoundsHead(SoundManager self) {
        if (!SoundCapture.isActive())
            return;
        Map<String, ISound> playingSounds = PLAYING_SOUNDS.get(self);
        for (String channel : playingSounds.keySet())
            SoundCapture.onStop(channel);
    }

    /** pauseAllSounds HEAD. */
    public static void pauseAllSoundsHead(SoundManager self) {
        SoundCapture.onPause();
    }

    /** resumeAllSounds HEAD. */
    public static void resumeAllSoundsHead(SoundManager self) {
        SoundCapture.onResume();
    }
}
