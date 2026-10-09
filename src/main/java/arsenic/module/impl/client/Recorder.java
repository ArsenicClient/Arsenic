package arsenic.module.impl.client;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.RotationView;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.render.capture.FfmpegRecorder;
import arsenic.utils.render.capture.FrameSink;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import arsenic.utils.render.capture.SoundCapture;
import arsenic.utils.render.capture.WindowOutput;

import net.minecraft.client.renderer.OpenGlHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@ModuleInfo(name = "Recorder", category = ModuleCategory.CLIENT, description = "Records the game with ffmpeg, or shows it in a window for OBS")
public class Recorder extends Module {

    public final EnumProperty<Output> output = new EnumProperty<>("Output", Output.File);
    public final DoubleProperty fps = new DoubleProperty("FPS", new DoubleValue(24, 120, 60, 1));
    public final DoubleProperty quality = new DoubleProperty("CRF", new DoubleValue(14, 35, 20, 1));
    public final EnumProperty<Perspective> perspective = new EnumProperty<>("Perspective", Perspective.Camera);
    public final BooleanProperty hideVisuals = new BooleanProperty("Hide Visuals", true);
    public final BooleanProperty sounds = new BooleanProperty("Sounds", true);

    private FrameSink recorder;
    private SoundCapture soundCapture;

    public Recorder() {
        // the silent view is a clean world pass, so there are no visuals to hide
        hideVisuals.setVisible(() -> perspective.getValue() == Perspective.Camera);
        // a live window is not encoded, and OBS captures the game audio itself
        quality.setVisible(() -> output.getValue() == Output.File);
        sounds.setVisible(() -> output.getValue() == Output.File);
    }

    @Override
    protected void onEnable() {
        // also stops a saved "enabled" state from starting a recording at launch
        if (mc.theWorld == null) {
            notify(NotificationType.WARNING, "Join a world to start recording");
            setEnabled(false);
            return;
        }
        if (output.getValue() == Output.Window) {
            WindowOutput window = new WindowOutput("Arsenic Recorder", (int) fps.getValue().getInput());
            window.start();
            attach(window);
            return;
        }
        File dir = new File(arsenic.utils.java.FileUtils.getArsenicFolderDirAsFile(), "Recordings");
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File output = new File(dir, name + ".mp4");
        // with sound the video is muxed into the output once the audio track has been mixed
        File video = sounds.getValue() ? new File(dir, name + ".video.mp4") : output;
        File bundled = new File(arsenic.utils.java.FileUtils.getArsenicFolderDirAsFile(), "ffmpeg.exe");
        String ffmpeg = bundled.isFile() ? bundled.getAbsolutePath() : "ffmpeg";

        SoundCapture capture = sounds.getValue() ? new SoundCapture(perspective.getValue() == Perspective.Silent) : null;
        FfmpegRecorder rec = new FfmpegRecorder(ffmpeg, video, mc.displayWidth, mc.displayHeight,
                (int) fps.getValue().getInput(), (int) quality.getValue().getInput());
        try {
            rec.start(failure -> finished(failure, capture, ffmpeg, video, output));
        } catch (IOException e) {
            notify(NotificationType.ERROR, "Could not start ffmpeg - put it on PATH or at Arsenic/ffmpeg.exe");
            setEnabled(false);
            return;
        }
        soundCapture = capture;
        if (capture != null)
            capture.begin(rec.getStartNanos());
        attach(rec);
    }

    private void attach(FrameSink sink) {
        recorder = sink;
        RenderTargets.attachRecorder(sink, hideVisuals.getValue(), perspective.getValue() == Perspective.Silent);
        if (perspective.getValue() == Perspective.Silent && !OpenGlHelper.isFramebufferEnabled())
            notify(NotificationType.WARNING, "Framebuffers are off (OptiFine Fast Render or Antialiasing?) - recording the camera instead");
    }

    @Override
    protected void onDisable() {
        if (recorder == null)
            return;
        if (soundCapture != null) {
            soundCapture.finish();
            soundCapture = null;
        }
        recorder.stop();
        recorder = null;
        RenderTargets.detachRecorder();
        if (!Arsenic.getArsenic().getModuleManager().getModuleByClass(RotationView.class).isEnabled())
            SilentView.release();
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        // ffmpeg died, the game window was resized or the output window was closed; the writer thread reports ffmpeg failures
        if (recorder != null && !recorder.isRunning())
            setEnabled(false);
    };

    /** Runs on the recorder's writer thread once ffmpeg has exited. */
    private void finished(String failure, SoundCapture capture, String ffmpeg, File video, File output) {
        String message = failure;
        if (capture != null) {
            if (failure == null) {
                mc.addScheduledTask(() -> notify(NotificationType.INFO, "Mixing audio..."));
                String audioFailure = capture.mux(ffmpeg, video, output);
                if (audioFailure != null)
                    message = "Audio failed (" + audioFailure + "), saved " + output.getName() + " without it";
            }
            // keep the video-only file when there is nothing better
            if (output.isFile() && message == null)
                video.delete();
            else if (video.isFile() && (output.delete() || !output.exists()))
                video.renameTo(output);
        }
        String text = message;
        NotificationType type = failure != null ? NotificationType.ERROR : message != null ? NotificationType.WARNING : NotificationType.INFO;
        mc.addScheduledTask(() -> notify(type,
                text == null ? "Saved " + output.getName() : text));
    }

    private void notify(NotificationType type, String message) {
        NotificationManager.show(new Notification(type, "Recorder", message, 2));
    }

    public enum Output {
        File,  // an .mp4 in Arsenic/Recordings
        Window // a live window for OBS Window Capture
    }

    public enum Perspective {
        Camera, // what you see
        Silent  // the world from your silent (server-side) rotation, with the vanilla HUD but no client visuals
    }
}
