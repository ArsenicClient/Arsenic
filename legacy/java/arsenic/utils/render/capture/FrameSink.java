package arsenic.utils.render.capture;

/** Where {@link RenderTargets} sends captured frames: a video file or a live window. */
public interface FrameSink {

    /** Reads the bound framebuffer. Must run on the render thread. */
    void capture();

    /** Stops taking frames and releases GL resources. Must run on the render thread. */
    void stop();

    boolean isRunning();
}
