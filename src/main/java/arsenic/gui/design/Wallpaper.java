package arsenic.gui.design;

import arsenic.gui.click.GuiStyle;
import arsenic.utils.java.FileUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Wallpapers drawn behind the click GUI: the built-in {@link WallpaperPresets} plus any png/jpg/gif
 * dropped in {@code ~/Arsenic/wallpapers}. The selected name is stored in {@link GuiStyle}.
 * Images are decoded / generated on a background thread, so picking one never freezes the game.
 * Animated GIFs are composited frame by frame, then uploaded a few frames per render tick.
 */
public final class Wallpaper {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_SIZE = 2560;

    // ---- animated GIF limits (keep heap and VRAM use reasonable) ----
    private static final int GIF_MAX_SIDE = 1280;
    private static final int GIF_MAX_FRAMES = 240;
    /** Total pixels over all frames (~32M px = ~128 MB while decoding, same on the GPU). */
    private static final long GIF_PIXEL_BUDGET = 32_000_000L;
    private static final int MIN_DELAY_MS = 20;
    private static final int UPLOADS_PER_FRAME = 6;

    private static final class Loaded {
        final String name;
        final List<BufferedImage> frames;
        final int[] delays;
 Loaded(String name, List<BufferedImage> frames, int[] delays) {
     this.name = name;
     this.frames = frames;
     this.delays = delays;
        }
    }

    /** GL texture ids, one per frame; only the first {@code uploaded} are valid. */
    private static int[] textures = new int[0];
    private static int uploaded;
    private static int[] delays = new int[0];
    private static int totalMs;
    private static List<BufferedImage> toUpload;
    private static String loadedName = null;
    private static int imgW, imgH;
    private static long animStart;

    private static volatile String pendingName = null;
    private static volatile Loaded pending = null;
    private static volatile String failedName = null;
    private static long retryAt;

    private Wallpaper() {}

    public static File folder() {
        File dir = new File(FileUtils.getArsenicFolderDirAsFile(), "wallpapers");
        if (!dir.exists())
     dir.mkdirs();
        return dir;
    }

    private static boolean isImageFile(String lower) {
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    /** Built-in presets first, then the user's images. */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
 for (String n : WallpaperPresets.NAMES)
     out.add(WallpaperPresets.PREFIX + n);
        List<String> files = new ArrayList<>();
        File[] all = folder().listFiles();
        if (all != null)
     for (File f : all)
  if (f.isFile() && isImageFile(f.getName().toLowerCase(Locale.ROOT)))
      files.add(f.getName());
        files.sort(String.CASE_INSENSITIVE_ORDER);
 out.addAll(files);
 return out;
    }

    public static boolean isActive() {
 return !GuiStyle.get().getWallpaper().isEmpty();
    }

    /** None -> each preset -> each file -> None. */
    public static void cycle() {
        List<String> all = list();
        int i = all.indexOf(GuiStyle.get().getWallpaper());
 GuiStyle.get().setWallpaper(i + 1 >= all.size() ? "" : all.get(i + 1));
 retryAt = 0;
    }

    public static String label() {
        String w = GuiStyle.get().getWallpaper();
        if (w.isEmpty())
     return "None";
        if (WallpaperPresets.isPreset(w))
     return WallpaperPresets.displayName(w);
        int dot = w.lastIndexOf('.');
 String n = dot > 0 ? w.substring(0, dot) : w;
 n = n.length() > 18 ? n.substring(0, 17) + "…" : n;
 return w.toLowerCase(Locale.ROOT).endsWith(".gif") ? n + " (GIF)" : n;
    }

    public static void openFolder() {
        File dir = folder();
 String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
     if (os.contains("mac"))
  Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
     else if (os.contains("win"))
         Runtime.getRuntime().exec(new String[]{"explorer", dir.getAbsolutePath()});
     else
         org.lwjgl.Sys.openURL("file://" + dir.getAbsolutePath());
        } catch (Throwable ignored) {
 }
    }

    /**
     * True when a texture can be drawn. While a new wallpaper loads, the previous one stays on
     * screen; with nothing loaded yet this returns false and the normal backdrop is used.
     */
    public static boolean ready() {
        String name = GuiStyle.get().getWallpaper();
        if (name.isEmpty())
     return false;
        if (name.equals(loadedName)) {
     uploadSome();
     return uploaded > 0;
        }

        Loaded done = pending;
        if (done != null && done.name.equals(name)) {
     pending = null;
            pendingName = null;
     upload(done);
     return uploaded > 0;
        }
        if (name.equals(failedName)) {
     if (System.currentTimeMillis() < retryAt)
  return uploaded > 0;
            failedName = null;
        }
        if (!name.equals(pendingName))
     startLoad(name);
        return uploaded > 0;
    }

    private static void startLoad(final String name) {
 pendingName = name;
 pending = null;
        Thread t = new Thread(() -> {
            try {
  Loaded l;
  if (WallpaperPresets.isPreset(name))
      l = single(name, preset(name));
  else if (name.toLowerCase(Locale.ROOT).endsWith(".gif"))
      l = readGif(name, new File(folder(), name));
         else
      l = single(name, readFile(name));
         if (name.equals(pendingName))
      pending = l;
     } catch (Throwable e) {
         if (name.equals(pendingName)) {
      failedName = name;
      pendingName = null;
  }
     }
        }, "Arsenic-Wallpaper");
        t.setDaemon(true);
        t.start();
        retryAt = System.currentTimeMillis() + 3000L;
    }

    private static Loaded single(String name, BufferedImage img) {
        List<BufferedImage> frames = new ArrayList<>(1);
        frames.add(img);
        return new Loaded(name, frames, new int[]{0});
    }

    /** Presets are painted once, then kept as a png in wallpapers/.cache so the next load is instant. */
    private static BufferedImage preset(String name) {
 File cache = new File(new File(folder(), ".cache"), WallpaperPresets.displayName(name).replace(' ', '_') + "-v1.png");
 try {
     if (cache.isFile()) {
  BufferedImage img = ImageIO.read(cache);
         if (img != null)
      return img;
     }
 } catch (Throwable ignored) {
        }
        BufferedImage img = WallpaperPresets.render(name);
        try {
     cache.getParentFile().mkdirs();
     ImageIO.write(img, "png", cache);
        } catch (Throwable ignored) {
 }
        return img;
    }

    private static BufferedImage readFile(String name) throws Exception {
        BufferedImage img = ImageIO.read(new File(folder(), name));
        if (img == null)
     throw new IllegalStateException("unreadable image");
        return scaled(img, MAX_SIZE / (float) Math.max(img.getWidth(), img.getHeight()));
    }

    /** ARGB copy of {@code img}, shrunk by {@code s} when s < 1. */
    private static BufferedImage scaled(BufferedImage img, float s) {
        s = Math.min(1f, s);
        int w = img.getWidth(), h = img.getHeight();
        int nw = Math.max(1, Math.round(w * s)), nh = Math.max(1, Math.round(h * s));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    // ================================================================== GIF decoding

    private static Element child(IIOMetadataNode root, String tag) {
        NodeList l = root.getElementsByTagName(tag);
        return l.getLength() > 0 ? (Element) l.item(0) : null;
    }

    private static int intAttr(Element e, String attr, int def) {
        if (e == null)
     return def;
        try {
     return Integer.parseInt(e.getAttribute(attr));
        } catch (NumberFormatException ex) {
     return def;
        }
    }

    /**
     * Decodes every frame of a GIF onto a full-size canvas (GIF frames are often partial
     * rectangles), honouring each frame's offset, delay and disposal method.
     */
    private static Loaded readGif(String name, File file) throws Exception {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext())
     throw new IllegalStateException("no gif reader");
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
     if (in == null)
         throw new IllegalStateException("unreadable gif");
            reader.setInput(in, false);
     int count = Math.min(reader.getNumImages(true), GIF_MAX_FRAMES);
            if (count <= 0)
         throw new IllegalStateException("empty gif");

     int cw = -1, ch = -1;
     IIOMetadata streamMeta = reader.getStreamMetadata();
     if (streamMeta != null) {
  IIOMetadataNode root = (IIOMetadataNode) streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
         Element screen = child(root, "LogicalScreenDescriptor");
  cw = intAttr(screen, "logicalScreenWidth", -1);
         ch = intAttr(screen, "logicalScreenHeight", -1);
     }

     List<BufferedImage> frames = new ArrayList<>(count);
            List<Integer> frameDelays = new ArrayList<>(count);
     BufferedImage canvas = null;
     float s = 1f;

     for (int i = 0; i < count; i++) {
         BufferedImage frame;
  IIOMetadataNode meta;
         try {
      frame = reader.read(i);
      meta = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
  } catch (Throwable truncated) {
      if (frames.isEmpty())
   throw truncated;
      break;   // keep what decoded fine
  }
         Element desc = child(meta, "ImageDescriptor");
  Element gce = child(meta, "GraphicControlExtension");
         int left = intAttr(desc, "imageLeftPosition", 0);
         int top = intAttr(desc, "imageTopPosition", 0);
  int delay = intAttr(gce, "delayTime", 10) * 10;
         String disposal = gce != null ? gce.getAttribute("disposalMethod") : "none";

  if (canvas == null) {
      if (cw <= 0 || ch <= 0) {
   cw = frame.getWidth() + left;
   ch = frame.getHeight() + top;
      }
      canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
      float sideScale = GIF_MAX_SIDE / (float) Math.max(cw, ch);
      float budgetScale = (float) Math.sqrt(GIF_PIXEL_BUDGET / (double) count / ((double) cw * ch));
      s = Math.min(1f, Math.min(sideScale, budgetScale));
  }

  BufferedImage previous = "restoreToPrevious".equals(disposal) ? scaled(canvas, 1f) : null;
         Graphics2D g = canvas.createGraphics();
         g.drawImage(frame, left, top, null);
         g.dispose();

  frames.add(scaled(canvas, s));
  // browsers treat 0/10 ms as "as fast as reasonable" = 100 ms
         frameDelays.add(delay <= 10 ? 100 : Math.max(MIN_DELAY_MS, delay));

  if ("restoreToBackgroundColor".equals(disposal)) {
      Graphics2D clear = canvas.createGraphics();
      clear.setComposite(AlphaComposite.Clear);
      clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
      clear.dispose();
         } else if (previous != null) {
      canvas = previous;
         }
     }

     int[] d = new int[frameDelays.size()];
     for (int i = 0; i < d.length; i++)
         d[i] = frameDelays.get(i);
     if (d.length == 1)
         d[0] = 0;   // single-frame gif: static image
     return new Loaded(name, frames, d);
        } finally {
     reader.dispose();
 }
    }

    // ================================================================== GPU upload

    private static void upload(Loaded done) {
 release();
 loadedName = done.name;
        toUpload = done.frames;
        textures = new int[done.frames.size()];
        delays = done.delays;
        totalMs = 0;
 for (int d : delays)
            totalMs += d;
 imgW = done.frames.get(0).getWidth();
        imgH = done.frames.get(0).getHeight();
        uploadSome();
    }

    /** Uploads a few pending frames per call so a long GIF never stalls a single frame. */
    private static void uploadSome() {
        if (toUpload == null)
     return;
        try {
     int end = Math.min(textures.length, uploaded + UPLOADS_PER_FRAME);
            for (; uploaded < end; uploaded++) {
         int id = TextureUtil.glGenTextures();
         textures[uploaded] = id;
         TextureUtil.uploadTextureImageAllocate(id, toUpload.get(uploaded), true, true);
  toUpload.set(uploaded, null);   // free the heap copy right away
     }
     if (uploaded >= textures.length) {
  toUpload = null;
         animStart = System.currentTimeMillis();
     }
 } catch (Throwable t) {
     String name = loadedName;
            release();
     loadedName = null;
            failedName = name;
     retryAt = System.currentTimeMillis() + 3000L;
        }
    }

    private static void release() {
        for (int i = 0; i < uploaded; i++)
     TextureUtil.deleteTexture(textures[i]);
        textures = new int[0];
        uploaded = 0;
 toUpload = null;
        delays = new int[0];
 totalMs = 0;
    }

    /** Frame to show now: the first one until every frame is on the GPU, then loop on the delays. */
    private static int currentFrame() {
 if (uploaded < textures.length || textures.length <= 1 || totalMs <= 0)
     return 0;
 long t = (System.currentTimeMillis() - animStart) % totalMs;
 for (int i = 0; i < delays.length; i++) {
            t -= delays[i];
     if (t < 0)
  return i;
        }
        return delays.length - 1;
    }

    /** Draws the wallpaper ("cover" fit) with its opacity and dim veil. Returns false if nothing was drawn. */
    public static boolean draw() {
 if (!ready())
     return false;
        int width = mc.displayWidth, height = mc.displayHeight;
        float scale = Math.max(width / (float) imgW, height / (float) imgH);
        float dw = imgW * scale, dh = imgH * scale;
        float x = (width - dw) / 2f, y = (height - dh) / 2f;
 float dim = GuiStyle.get().getWallpaperDim() / 100f;
 float alpha = GuiStyle.get().getWallpaperOpacity() / 100f;

 GlStateManager.matrixMode(GL11.GL_PROJECTION);
 GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
 GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
 GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
     GlStateManager.enableTexture2D();
     GlStateManager.disableAlpha();
     if (alpha < 0.999f) {
         GlStateManager.enableBlend();
  GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
     } else {
  GlStateManager.disableBlend();
     }
     GlStateManager.color(1f, 1f, 1f, alpha);
     GlStateManager.bindTexture(textures[currentFrame()]);
     GL11.glBegin(GL11.GL_QUADS);
     GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x, y);
     GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x, y + dh);
     GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + dw, y + dh);
            GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + dw, y);
     GL11.glEnd();

     if (dim > 0.001f) {
  GlStateManager.disableTexture2D();
         GlStateManager.enableBlend();
  GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GlStateManager.color(0f, 0f, 0f, dim * (0.4f + 0.6f * alpha));
  GL11.glBegin(GL11.GL_QUADS);
         GL11.glVertex2f(0, 0);
         GL11.glVertex2f(0, height);
         GL11.glVertex2f(width, height);
         GL11.glVertex2f(width, 0);
  GL11.glEnd();
         GlStateManager.enableTexture2D();
     }
        } finally {
     GlStateManager.enableBlend();
     GlStateManager.enableAlpha();
     GlStateManager.color(1f, 1f, 1f, 1f);
     GlStateManager.matrixMode(GL11.GL_PROJECTION);
     GlStateManager.popMatrix();
     GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
 }
        return true;
    }
}
