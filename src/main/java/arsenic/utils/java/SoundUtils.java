package arsenic.utils.java;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundUtils {

    private static Clip clip;

    public static void playSound(String name) {
        try {
            if(clip != null)
                clip.close();
            clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(SoundUtils.class.getResource("/assets/arsenic/sounds/" + name + ".wav")));
            clip.start();
        } catch (Exception e) {
            System.out.println("Error with playing sound.");
        }
    }
}
