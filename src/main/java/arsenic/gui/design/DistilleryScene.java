package arsenic.gui.design;

import static arsenic.gui.design.PixelArt.PAL;
import static arsenic.gui.design.PixelArt.rect;

import arsenic.gui.click.GuiStyle;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.GuiCanvas;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Super-polished Interactive Alchemy Distillery & Science Laboratory.
 * <p>
 * Housed in a precision frosted-glass containment chamber with Material 3 elevation,
 * depth shadows, specular reflections, digital telemetry (real-time temperature & pressure
 * gauges), dynamic compound synthesis engine, multi-layer flame simulation with glowing wire mesh,
 * realistic fluid wave mechanics, Liebig coil condenser with cryo-frosting, pendant droplet
 * pinch-off physics, decanting beaker animations, interactive lab field notebook, floating
 * Material 3 tooltips, and harmonious musical sound design.
 */
public final class DistilleryScene {

    private DistilleryScene() {}

    // =========================================================================
    // 1. BASE ARTWORK GRID (54 x 54)
    // =========================================================================

    private static final String[] BASE = {
            "                                                      ",
            "             b                                        ",
            "            fbf                     b                 ",
            "       b    f f                    fbf                ",
            "      fbf   f  f            b      f f                ",
            "      f f   fy f           fbf    f   f               ",
            "     f   f  f  f   ffff    f f    fy  f   fff         ",
            "     f   f  f  f   f  f    f  f   f   f   f f         ",
            "     fpppf  fggf  f    f   f  f   f   f   f  f        ",
            "     fPppf  fGgf  fqqqqf   foof   fqqqf   fppf        ",
            "     fpppf  fggf  fQqqqf   fOof   fQqqf   fPpf        ",
            "     fffff  ffff  ffffff   ffff   fffff   ffff        ",
            "  tttttttttttttttttttttttttttttttttttttttttttttttttt  ",
            "  TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT  ",
            "    T                                            T    ",
            "    T                                            T    ",
            "                                                      ",
            "         fffffffffffffffffffffff                      ",
            "        fff                     f                     ",
            "        f f                      f                    ",
            "        f f                       f                   ",
            "        f f                       FFFFFF              ",
            "        f f                       FQQ  F              ",
            "       ff ff                      F  QQF m            ",
            "      f     f                     F    F m            ",
            "     f       f                    FQQ  F m            ",
            "    f         f                   F  QQF m            ",
            "    f         f                   F    mmm            ",
            "    fqqqqqqqqqf                   FQQ  F m            ",
            "    fqqqQqqqqqf                   F  QQF m            ",
            "    fqqqqqqQqqf                   F    F m            ",
            "     fqqqqqqqf                    FQQ  F m            ",
            "      fqqqqqf                     F  QQF m            ",
            "       fffff                      F    F m            ",
            "    mMMMMMMMMMm                   FQQ  F m            ",
            "    m         m                   F  QQF m            ",
            "    m         m                   F    F m            ",
            "    m         m                   FQQ  F m            ",
            "    m         m                   FFFQQF m            ",
            "    m         m                     f    m            ",
            "    m   MMM   m                 f        f            ",
            "    m   MMM   m                 f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f  eeeeeeee  ",
            "   m  mmmmmmm  m                f        f  eEEEEEEe  ",
            "   m  mmmmmmm  m                ffffffffff  EEEEEEEE  ",
            "tttttttttttttttttttttttttttttttttttttttttttttttttttttt",
            "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
            " cccccccccccccccccccccccccdcccccccccccccccccccccccccc ",
            " cccccccccnnccccccccccccccdccccccccccccccnncccccccccc ",
            " cccccccccccccccccccccccccdcccccccccccccccccccccccccc "};

    private static final int SW = 54, SH = 54;

    // Anchor positions in scene pixels
    private static final int FLAME_X = 8, FLAME_Y = 39;
    private static final int FLASK_SURFACE = 28, FLASK_BOTTOM = 32, FLASK_NECK_X = 9, FLASK_NECK_TOP = 18;
    private static final int TUBE_Y = 17, TUBE_X1 = 10, TUBE_X2 = 31;
    private static final int OUTLET_X = 36, OUTLET_Y = 39;
    private static final int BEAKER_X1 = 33, BEAKER_X2 = 40, BEAKER_TOP = 40, BEAKER_BOTTOM = 47, BEAKER_ROWS = 7;
    private static final int BENCH_TOP = 49, SHELF_TOP = 12;

    // Clickable prop regions in scene pixels [x1, y1, x2, y2]
    private static final int[] HIT_BURNER = {4, 34, 14, 48};
    private static final int[] HIT_FLASK = {3, 17, 15, 33};
    private static final int[] HIT_TUBE = {16, 16, 32, 21};
    private static final int[] HIT_CONDENSER = {33, 20, 40, 38};
    private static final int[] HIT_BEAKER = {31, 39, 42, 48};
    private static final int[] HIT_NOTEBOOK = {43, 45, 52, 49};

    // =========================================================================
    // 2. CHEMICAL REAGENT & COMPOUND SYNTHESIS SYSTEM
    // =========================================================================

    public static final class ReagentDef {
        public final int id;
        public final String name;
        public final String formula;
        public final String type;
        public final int color;
        public final int highlight;
        public final int noteDegree;
        public final String description;

        public ReagentDef(int id, String name, String formula, String type,
                          int color, int highlight, int noteDegree, String description) {
            this.id = id;
            this.name = name;
            this.formula = formula;
            this.type = type;
            this.color = color;
            this.highlight = highlight;
            this.noteDegree = noteDegree;
            this.description = description;
        }
    }

    private static final ReagentDef[] REAGENTS = {
            new ReagentDef(0, "Arsenic Trioxide", "As₂O₃", "Base Substrate", 0x2ECC71, 0xA9DFBF, 0, "Purified Arsenic substrate. Active toxin."),
            new ReagentDef(1, "Dragon's Cinnabar", "HgS", "Thermal Catalyst", 0xE74C3C, 0xF1948A, 2, "Thermal catalyst. Accelerates boil & flame."),
            new ReagentDef(2, "Cryo Cobalt", "CoCl₂", "Cryogenic Salt", 0x00E5FF, 0x80DEEA, 4, "Chilling reagent. Crystals frost the condenser."),
            new ReagentDef(3, "Solar Aurum", "AuCl₃", "Radiant Solvent", 0xFFB300, 0xFFE082, 5, "Photonic gold. Emits warm ambient beams."),
            new ReagentDef(4, "Void Amethyst", "V₄O₁₀", "Cosmic Quintessence", 0xAB47BC, 0xCE93D8, 6, "Sublimated Vanadium. Inverts micro-gravity."),
            new ReagentDef(5, "Elemental Quicksilver", "Hg", "Heavy Metallic", 0xCFD8DC, 0xFFFFFF, 7, "Dense liquid metal. High surface tension.")
    };

    /** Shelf bottles cut out of the artwork so each can lift, wobble, uncork, and pour. */
    private static final class Bottle {
        int index;
        int x1, x2;
        String[] rows;
        int liquidX = -1, liquidY = -1, corkX = -1, corkY = -1;
        float lift, wobble, fizz, corkUp, corkV, corkXOffset;
        boolean corkFlying;
        ReagentDef reagent;
    }

    private static final String[] BACKDROP;
    private static final List<Bottle> BOTTLES = new ArrayList<>();
    private static final int[][] COIL;

    static {
        char[][] g = new char[SH][];
        for (int y = 0; y < SH; y++) g[y] = BASE[y].toCharArray();
        int x = 0;
        int bottleIdx = 0;
        while (x < SW) {
            if (!columnUsed(x)) { x++; continue; }
            Bottle b = new Bottle();
            b.index = bottleIdx;
            b.reagent = REAGENTS[Math.min(bottleIdx, REAGENTS.length - 1)];
            b.x1 = x;
            while (x < SW && columnUsed(x)) x++;
            b.x2 = x - 1;
            b.rows = new String[SHELF_TOP];
            for (int y = 0; y < SHELF_TOP; y++) {
                StringBuilder row = new StringBuilder();
                for (int c = b.x1; c <= b.x2; c++) {
                    char ch = g[y][c];
                    row.append(ch);
                    if ("qpgo".indexOf(ch) >= 0 && b.liquidY < y) { b.liquidX = c; b.liquidY = y; }
                    if (ch == 'b' && b.corkY < 0) { b.corkX = c; b.corkY = y; }
                    g[y][c] = ' ';
                }
                b.rows[y] = row.toString();
            }
            BOTTLES.add(b);
            bottleIdx++;
        }
        BACKDROP = new String[SH];
        for (int y = 0; y < SH; y++) BACKDROP[y] = new String(g[y]);

        List<int[]> coil = new ArrayList<>();
        for (int y = 22; y <= 38; y++)
            for (int c = 35; c <= 38; c++)
                if (BASE[y].charAt(c) == 'Q')
                    coil.add(new int[]{c, y});
        COIL = coil.toArray(new int[0][]);
    }

    private static boolean columnUsed(int x) {
        for (int y = 0; y < SHELF_TOP; y++)
            if (BASE[y].charAt(x) != ' ')
                return true;
        return false;
    }

    // =========================================================================
    // 3. PHYSICAL SIMULATION & PARTICLE POOLS
    // =========================================================================

    private static final Random RNG = new Random(0xD15L);
    private static long lastNanos;
    private static float time, hoverE, bubbleTimer, dripTimer = 0.8f, shelfTimer = 1.5f, moteTimer;
    private static float level = 1.8f;
    private static boolean draining;
    private static float drainAngle;

    // Thermal & Pressure dynamics
    private static int heatSetting = 2; // 0=Pilot, 1=Simmer, 2=Optimal, 3=Roar, 4=Inferno
    private static float currentTemp = 98.4f; // in °C
    private static float wireGauzeHeat = 0.4f;
    private static float pressure = 1.02f; // in atm
    private static float roar, rush, boilOver, frostAmount, swirlAngle, swirlVelocity;

    // Chemical Synthesis State
    private static int compoundColor = 0x2ECC71;
    private static int compoundSecondary = 0xA9DFBF;
    private static String currentCompound = "ARSENIC ELIXIR";
    private static String currentFormula = "As₂O₃·Sol";
    private static int reagentsMask = 1; // Bit 0 on initially
    private static String discoveryBanner = "";
    private static float discoveryTimer = 0f;

    // Cursor tracking & Layout coordinates
    private static float mouseSX = -1, mouseSY = -1, parallax;
    private static float lastOx, lastOy, lastP, lastClipTop;
    private static float chamberX1, chamberY1, chamberX2, chamberY2;
    private static boolean lastHovered;

    // Interactive Field Notebook
    private static int notebookPage = 0;
    private static float notebookFlip = 0f;
    private static final String[] NOTEBOOK_TITLES = {
            "TELEMETRY LOG", "REAGENT FORMULARY", "SYNTHESIS DISCOVERIES", "ALCHEMIST'S LOG"
    };

    // Generic Particle Mote
    private static final class Mote {
        float x, y, vx, vy, age, life;
        boolean live;
        int color;
        int kind;
        float size;
    }

    private static final Mote[] BUBBLES = new Mote[20];
    private static final Mote[] EMBERS = new Mote[18];
    private static final Mote[] DRIPS = new Mote[6];
    private static final Mote[] FIZZ = new Mote[16];
    private static final Mote[] SPLASH = new Mote[20];
    private static final Mote[] DUST = new Mote[12];
    private static final Mote[] STEAM = new Mote[12];
    private static final Mote[] PIPETTE = new Mote[10];

    static {
        for (Mote[] pool : new Mote[][]{BUBBLES, EMBERS, DRIPS, FIZZ, SPLASH, DUST, STEAM, PIPETTE})
            for (int i = 0; i < pool.length; i++) pool[i] = new Mote();
    }

    // =========================================================================
    // 4. MAIN RENDERING LOOP
    // =========================================================================

    public static void draw(float x1, float y1, float x2, float y2, boolean hovered, float mouseX, float mouseY) {
        float bw = x2 - x1, bh = y2 - y1;
        if (bw < 60f || bh < 50f)
            return;

        chamberX1 = x1; chamberY1 = y1; chamberX2 = x2; chamberY2 = y2;
        float s = GuiCanvas.scale();

        // Top status header and bottom control dock reservations
        float headerH = 22f;
        float footerH = 14f;
        float sceneBoxY1 = y1 + headerH;
        float sceneBoxY2 = y2 - footerH;
        float sceneBoxH = sceneBoxY2 - sceneBoxY1;

        // Clean integer multiple pixel scaling (snapped to whole screen pixels)
        float p = Math.min((bw - 8f) / SW, sceneBoxH / SH);
        p = Math.max(1f, (float) Math.floor(p * s)) / s;

        float ox = snap(x1 + (bw - SW * p) / 2f, s, GuiCanvas.offsetX());
        float oy = snap(sceneBoxY2 - SH * p, s, GuiCanvas.offsetY());
        lastOx = ox; lastOy = oy; lastP = p; lastClipTop = y1; lastHovered = hovered;
        mouseSX = hovered ? (mouseX - ox) / p : -1;
        mouseSY = hovered ? (mouseY - oy) / p : -1;

        step(hovered, mouseX, mouseY, x1, x2);

        // 1. Draw outer frosted-glass containment terrarium chamber
        drawChamberCard(x1, y1, x2, y2, hovered);

        // 2. Draw chamber top telemetry header
        drawChamberHeader(x1, y1, x2, headerH);

        // 3. Batched pixel-art laboratory apparatus
        PixelArt.palette();
        // Dynamic reagent colors in palette
        PAL['q'] = compoundColor;
        PAL['Q'] = compoundSecondary;
        PixelArt.begin();
        PixelArt.clipTop = sceneBoxY1;
        try {
            drawExtendedBenchAndWall(x1, x2, ox, oy, p);
            drawVolumetricFlameLight(ox, oy, p);
            PixelArt.sprite(BACKDROP, ox, oy, p, 255);
            drawContactShadows(ox, oy, p);
            drawShelfBottles(ox, oy, p);
            drawIncandescentGauze(ox, oy, p);
            drawBunsenBurnerFlame(ox, oy, p);
            drawRetortFlask(ox, oy, p);
            drawDeliveryTubeAndCondenser(ox, oy, p);
            drawCollectionBeaker(ox, oy, p);
            drawNotebookIllustration(ox, oy, p);
            drawAtmosphericParticles(ox, oy, p);
        } finally {
            PixelArt.clipTop = Float.NEGATIVE_INFINITY;
            PixelArt.end();
        }

        // 4. Chamber bottom micro-dock & status indicators
        drawChamberFooter(x1, sceneBoxY2, x2, y2);

        // 5. Interactive Material 3 Inspector Tooltip
        if (hovered && !draining) {
            drawInspectorTooltip(mouseX, mouseY, x1, y1, x2, y2);
        }

        // 6. Alchemical secret discovery celebration banner
        if (discoveryTimer > 0f) {
            drawDiscoveryBanner(x1, y1, x2, headerH);
        }
    }

    private static float snap(float v, float scale, float offset) {
        return (Math.round(v * scale + offset) - offset) / scale;
    }

    // =========================================================================
    // 5. CHAMBER CARD & TELEMETRY HUD
    // =========================================================================

    private static void drawChamberCard(float x1, float y1, float x2, float y2, boolean hovered) {
        float radius = 14f;
        boolean light = M3.isLight();

        // Soft depth shadow
        DrawUtils.drawShadow(x1, y1, x2, y2, radius, 14f, light ? 60 : 130);

        // Frosted glass background fill
        int bgFill = GuiStyle.glassify(M3.alpha(M3.surfaceContainerLow(), light ? 220 : 200));
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, bgFill);

        // Glass reflection sheen
        if (GuiStyle.glassEnabled()) {
            DrawUtils.drawGlassRect(x1, y1, x2, y2, radius,
                    M3.alpha(compoundColor, light ? 12 : 20), 0xFFFFFFFF, 0.45f);
        }

        // Subtle specular outer rim
        int outlineC = M3.alpha(light ? 0xFFFFFFFF : M3.outlineVariant(), light ? 160 : 90);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, outlineC);

        // Dynamic edge highlight matching active compound
        int edgeAlpha = (int) (35 + 25 * hoverE);
        DrawUtils.drawEdgeHighlight(x1, y1, x2, y2, radius, compoundColor, edgeAlpha);
    }

    private static void drawChamberHeader(float x1, float y1, float x2, float h) {
        float cy = y1 + h / 2f;
        FontRendererExtension<?> labelFr = M3.label();
        FontRendererExtension<?> bodyFr = M3.body();

        // Pulsing chemical LED status pip
        float ledX = x1 + 10f;
        float ledPulse = 0.7f + 0.3f * (float) Math.sin(time * 6f);
        DrawUtils.drawCircle(ledX, cy, 3.5f, M3.alpha(compoundColor, (int) (120 * ledPulse)));
        DrawUtils.drawCircle(ledX, cy, 2.0f, compoundColor);

        // Concoction Name
        labelFr.setScale(0.88f);
        labelFr.drawString(currentCompound, ledX + 7f, cy, M3.onSurface(), labelFr.CENTREY);
        labelFr.resetScale();

        // Temperature readout (right-aligned)
        bodyFr.setScale(0.82f);
        String tempStr = String.format("%.0f°C", currentTemp);
        int tempCol = currentTemp > 180f ? 0xFFE53935 : currentTemp > 90f ? 0xFFFFA000 : 0xFF81D4FA;
        float tempW = bodyFr.getWidth(tempStr);
        bodyFr.drawString(tempStr, x2 - 10f - tempW, cy, tempCol, bodyFr.CENTREY);

        // Small flame icon next to temp
        M3Icons.drawCentered(M3Icons.FIRE, x2 - 14f - tempW, cy, tempCol, 0.75f);
        bodyFr.resetScale();

        // Subtle divider beneath header
        DrawUtils.drawRect(x1 + 6f, y1 + h - 0.5f, x2 - 6f, y1 + h, M3.alpha(M3.outlineVariant(), 60));
    }

    private static void drawChamberFooter(float x1, float y1, float x2, float y2) {
        FontRendererExtension<?> bodyFr = M3.body();
        bodyFr.setScale(0.72f);
        float cy = (y1 + y2) / 2f;

        // Subtle divider above footer
        DrawUtils.drawRect(x1 + 6f, y1, x2 - 6f, y1 + 0.5f, M3.alpha(M3.outlineVariant(), 60));

        // Status string
        String status;
        if (draining) status = "Decanting distillate...";
        else if (boilOver > 0f) status = "VIGOROUS BOILOVER!";
        else if (rush > 0f) status = "Condenser Cryo-Flush";
        else if (heatSetting == 4) status = "Inferno Overdrive";
        else if (heatSetting == 0) status = "Pilot Simmer (Idle)";
        else status = String.format("%.2f atm • %s", pressure, currentFormula);

        bodyFr.drawString(status, x1 + 10f, cy, M3.alpha(M3.onSurfaceVariant(), 180), bodyFr.CENTREY);

        // Quick action hints on the right
        String hint = "[Left/Right Click props]";
        float hw = bodyFr.getWidth(hint);
        bodyFr.drawString(hint, x2 - 10f - hw, cy, M3.alpha(M3.onSurfaceVariant(), 140), bodyFr.CENTREY);
        bodyFr.resetScale();
    }

    private static void drawDiscoveryBanner(float x1, float y1, float x2, float h) {
        float alpha = MathUtils.clamp01(discoveryTimer / 0.5f);
        int a = (int) (240 * alpha);
        DrawUtils.drawRoundedRect(x1 + 6f, y1 + 3f, x2 - 6f, y1 + h + 2f, 8f,
                M3.alpha(M3.surfaceContainerHighest(), a));
        DrawUtils.drawRoundedOutline(x1 + 6f, y1 + 3f, x2 - 6f, y1 + h + 2f, 8f, 1f,
                M3.alpha(compoundColor, a));

        FontRendererExtension<?> label = M3.label();
        label.setScale(0.9f);
        label.drawString("★ DISCOVERED: " + discoveryBanner, (x1 + x2) / 2f, y1 + h / 2f + 2f,
                M3.alpha(compoundColor, a), label.CENTREX, label.CENTREY);
        label.resetScale();
    }

    // =========================================================================
    // 6. DETAILED PIXEL ART RENDERING METHODS
    // =========================================================================

    private static void px(float ox, float oy, float p, float x, float y, char c, int a) {
        rect(ox + Math.round(x) * p, oy + Math.round(y) * p, p, p, PAL[c], a);
    }

    private static void pxCol(float ox, float oy, float p, float x, float y, int rgb, int a) {
        rect(ox + Math.round(x) * p, oy + Math.round(y) * p, p, p, rgb, a);
    }

    /** Extends the wooden lab bench and tiled mortar wall to the chamber borders so no gaps exist. */
    private static void drawExtendedBenchAndWall(float cardX1, float cardX2, float ox, float oy, float p) {
        int shift = Math.round(-parallax);
        int line = PixelArt.wallLine();

        // Brick wall tiling
        int leftCol = (int) Math.floor((cardX1 - ox) / p);
        int rightCol = (int) Math.ceil((cardX2 - ox) / p);

        for (int y = 0; y < BENCH_TOP; y++) {
            boolean mortarRow = y % 6 == 5;
            int offset = (y / 6) % 2 == 0 ? 0 : 4;
            if (mortarRow) {
                rect(cardX1, oy + y * p, cardX2 - cardX1, p, line, 30);
                continue;
            }
            for (int x = leftCol; x <= rightCol; x++) {
                if ((x + offset + shift + 128) % 8 == 0)
                    rect(ox + x * p, oy + y * p, p, p, line, 22);
            }
        }

        // Shelf extension
        rect(cardX1, oy + SHELF_TOP * p, cardX2 - cardX1, p, PAL['t'], 255);
        rect(cardX1, oy + (SHELF_TOP + 1) * p, cardX2 - cardX1, p, PAL['T'], 255);

        // Lab bench top extension
        rect(cardX1, oy + BENCH_TOP * p, cardX2 - cardX1, p, PAL['t'], 255);
        rect(cardX1, oy + (BENCH_TOP + 1) * p, cardX2 - cardX1, p, PAL['T'], 255);
        rect(cardX1, oy + (BENCH_TOP + 2) * p, cardX2 - cardX1, 3 * p, PAL['c'], 255);
    }

    /** Multi-tier volumetric radial lighting from the flame, harmonizing with current elixir hue. */
    private static void drawVolumetricFlameLight(float ox, float oy, float p) {
        if (heatSetting == 0) return;
        float heatFactor = (heatSetting / 4f) + (roar > 0f ? 0.6f : 0f) + 0.08f * (float) Math.sin(time * 16f);
        int cx = FLAME_X + 1, cy = FLAME_Y - 2;

        int[] radii = {22, 17, 12, 7, 3};
        int[] alphas = {10, 14, 20, 28, 40};

        int lightTint = mixRgb(PAL['o'], compoundColor, 0.25f);

        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            int a = (int) (heatFactor * alphas[i]);
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
                rect(ox + (cx - half) * p, oy + (cy + dy) * p, (2 * half + 1) * p, p, lightTint, a);
            }
        }
    }

    private static void drawContactShadows(float ox, float oy, float p) {
        int dark = PixelArt.shadow();
        rect(ox + 2 * p, oy + BENCH_TOP * p, 15 * p, p, dark, 120);
        rect(ox + 30 * p, oy + BENCH_TOP * p, 14 * p, p, dark, 120);
        rect(ox + 43 * p, oy + BENCH_TOP * p, 10 * p, p, dark, 100);
        rect(ox + 2 * p, oy + (SHELF_TOP + 2) * p, 50 * p, p, dark, 80);
    }

    private static void drawShelfBottles(float ox, float oy, float p) {
        for (Bottle b : BOTTLES) {
            int dy = -Math.round(b.lift);
            int dx = b.wobble > 0f ? (((int) (b.wobble * 22f)) % 2 == 0 ? 1 : -1) : 0;
            for (int y = 0; y < SHELF_TOP; y++) {
                String row = b.rows[y];
                for (int i = 0; i < row.length(); i++) {
                    char ch = row.charAt(i);
                    if (ch == ' ') continue;
                    int cx = b.x1 + i + dx, cy = y + dy;
                    if (ch == 'b' && b.corkFlying) {
                        cy -= Math.round(b.corkUp);
                        cx += Math.round(b.corkXOffset);
                    }
                    if ("qpgo".indexOf(ch) >= 0) {
                        pxCol(ox, oy, p, cx, cy, b.reagent.color, 255);
                    } else if ("QPGO".indexOf(ch) >= 0) {
                        pxCol(ox, oy, p, cx, cy, b.reagent.highlight, 255);
                    } else {
                        px(ox, oy, p, cx, cy, ch, 255);
                    }
                }
            }
            // Shadow on the shelf under lifted bottle
            if (b.lift > 0.4f) {
                rect(ox + b.x1 * p, oy + (SHELF_TOP - 1) * p, (b.x2 - b.x1 + 1) * p, p, PixelArt.shadow(), 90);
            }
        }

        // Fizz motes from open bottles
        for (Mote f : FIZZ) {
            if (f.live)
                pxCol(ox, oy, p, f.x, f.y, f.color, (int) (240 * (1f - f.age / f.life)));
        }

        // Pipette stream motes falling toward flask
        for (Mote pip : PIPETTE) {
            if (pip.live)
                pxCol(ox, oy, p, pip.x, pip.y, pip.color, 240);
        }
    }

    /** Tripod wire gauze that turns cherry red / glowing incandescent under high heat. */
    private static void drawIncandescentGauze(float ox, float oy, float p) {
        if (wireGauzeHeat > 0.05f) {
            int glowColor = wireGauzeHeat > 0.6f ? 0xFFFF7043 : 0xFFD84315;
            int a = (int) (200 * wireGauzeHeat);
            rect(ox + 5 * p, oy + 33 * p, 8 * p, p, glowColor, a);
            rect(ox + 6 * p, oy + 34 * p, 6 * p, p, glowColor, (int) (a * 0.7f));
        }
    }

    /** Multi-tier Bunsen burner flame with blue inner core and organic dancing tongues. */
    private static void drawBunsenBurnerFlame(float ox, float oy, float p) {
        if (heatSetting == 0) {
            // Tiny pilot light
            if (((int) (time * 8f)) % 4 != 0)
                pxCol(ox, oy, p, FLAME_X + 1, FLAME_Y, 0xFF42A5F5, 200);
            return;
        }

        int frame = (int) (time * (10f + heatSetting * 4f + (roar > 0f ? 10f : 0f))) % 4;
        int h = 2 + heatSetting + (roar > 0f ? 2 : 0) + (frame == 1 ? 1 : 0);
        int lean = frame == 2 ? -1 : frame == 3 ? 1 : 0;
        int cx = FLAME_X + 1;

        // 1. Blue reducing inner cone
        pxCol(ox, oy, p, cx, FLAME_Y, 0xFF29B6F6, 255);
        pxCol(ox, oy, p, cx - 1, FLAME_Y, 0xFF0288D1, 230);
        pxCol(ox, oy, p, cx + 1, FLAME_Y, 0xFF0288D1, 230);

        // 2. Intermediate mantle (bright yellow/orange)
        for (int i = 1; i < h; i++) {
            int x = cx + (i >= h - 2 ? lean : 0);
            boolean tip = i == h - 1;
            int coreCol = tip ? PAL['o'] : PAL['O'];
            pxCol(ox, oy, p, x, FLAME_Y - i, coreCol, 255);
            if (!tip) {
                pxCol(ox, oy, p, x - 1, FLAME_Y - i, PAL['o'], 235);
                pxCol(ox, oy, p, x + 1, FLAME_Y - i, PAL['o'], 235);
            }
        }

        // 3. Spitting embers
        for (Mote emb : EMBERS) {
            if (emb.live)
                pxCol(ox, oy, p, emb.x, emb.y, emb.color, (int) (255 * (1f - emb.age / emb.life)));
        }
    }

    /** Retort boiling flask with dynamic wave harmonics, bubble nucleation, and swirl momentum. */
    private static void drawRetortFlask(float ox, float oy, float p) {
        // Swirl offset & surface wave harmonics
        int wave = (int) (Math.sin(time * 6f + swirlAngle) * 1.5f);
        int ripple = 6 + (int) ((time * (4f + currentTemp * 0.05f)) % 7);
        pxCol(ox, oy, p, ripple, FLASK_SURFACE + wave, compoundSecondary, 255);

        // Froth / Foam head during vigorous boil or boilover
        if (boilOver > 0f || currentTemp > 140f) {
            int frothRise = boilOver > 0f ? 4 : 2;
            for (int i = 0; i < frothRise; i++) {
                int fy = FLASK_SURFACE - 1 - i;
                pxCol(ox, oy, p, FLASK_NECK_X, fy, compoundSecondary, 220);
                if (i % 2 == 0)
                    pxCol(ox, oy, p, FLASK_NECK_X + (i % 3 == 0 ? 1 : -1), fy, compoundColor, 180);
            }
        }

        // Rising nucleation bubbles
        for (Mote b : BUBBLES) {
            if (!b.live) continue;
            int y = Math.round(b.y);
            int lo = y >= 31 ? (y == 32 ? 7 : 6) : 5;
            int hi = y >= 31 ? (y == 32 ? 11 : 12) : 13;
            int x = Math.round(b.x) + ((int) (b.y * 2f) % 2);
            if (x >= lo && x <= hi)
                pxCol(ox, oy, p, x, y, compoundSecondary, 240);
        }
    }

    /** Delivery tube vapor clouds, pressure gauge, and Liebig condenser helical coil. */
    private static void drawDeliveryTubeAndCondenser(float ox, float oy, float p) {
        float speed = 1f + (currentTemp / 90f) + (rush > 0f ? 2.5f : 0f);
        int len = TUBE_X2 - TUBE_X1;
        int puffs = rush > 0f ? 4 : 2;

        // Traveling vapor puffs along tube curve
        for (int k = 0; k < puffs; k++) {
            int pos = (int) ((time * 8f * speed + k * len / (float) puffs) % (len + 4));
            if (pos <= len) {
                pxCol(ox, oy, p, TUBE_X1 + pos, TUBE_Y, compoundSecondary, 210);
            } else {
                pxCol(ox, oy, p, TUBE_X2 + (pos - len), TUBE_Y + (pos - len), compoundSecondary, 210);
            }
        }

        // Condenser 3D Helical Coil
        int band = (int) (time * 8f * speed) % (COIL.length + 5);
        for (int i = 0; i < COIL.length; i++) {
            int d = band - i;
            if (d >= 0 && d < 3) {
                int col = d == 0 ? compoundSecondary : compoundColor;
                pxCol(ox, oy, p, COIL[i][0], COIL[i][1], col, 240 - d * 50);
            }
        }

        // Cryo-frost crystal branches on condenser jacket
        if (frostAmount > 0.05f) {
            int frostA = (int) (180 * frostAmount);
            rect(ox + 33 * p, oy + 21 * p, p, 16 * p, 0xFFE0F7FA, frostA);
            rect(ox + 39 * p, oy + 21 * p, p, 16 * p, 0xFFE0F7FA, (int) (frostA * 0.7f));
            for (int fy = 23; fy <= 37; fy += 3) {
                pxCol(ox, oy, p, 34, fy, 0xFFFFFFFF, frostA);
                pxCol(ox, oy, p, 38, fy + 1, 0xFFFFFFFF, frostA);
            }
        }
    }

    /** Collection beaker with pendant drop pinching, capillary ripples, and decanting pour. */
    private static void drawCollectionBeaker(float ox, float oy, float p) {
        int rows = (int) Math.floor(level);
        int tilt = draining ? (((int) (time * 8f)) % 2 == 0 ? 1 : -1) : 0;

        // Decant fluid stream into bench drain/vial when tilting
        if (draining && level > 2.0f) {
            for (int dy = 0; dy < 6; dy++)
                pxCol(ox, oy, p, BEAKER_X2 + 2 + dy % 2, BEAKER_BOTTOM + dy, compoundColor, 220);
        }

        // Beaker liquid volume rows
        for (int r = 0; r < rows; r++) {
            int y = BEAKER_BOTTOM - r;
            boolean top = r == rows - 1;
            int col = top ? compoundSecondary : compoundColor;
            rect(ox + BEAKER_X1 * p, oy + y * p, (BEAKER_X2 - BEAKER_X1 + 1) * p, p, col, 255);
            if (top && tilt != 0)
                pxCol(ox, oy, p, tilt > 0 ? BEAKER_X2 : BEAKER_X1, y - 1, compoundSecondary, 255);
        }

        // Fractional fluid meniscus
        float frac = level - rows;
        if (frac > 0.05f && rows < BEAKER_ROWS) {
            rect(ox + BEAKER_X1 * p, oy + (BEAKER_BOTTOM - rows) * p, (BEAKER_X2 - BEAKER_X1 + 1) * p, p,
                    compoundSecondary, (int) (220 * frac));
        }

        // Etched measurement graduation lines that light up
        for (int mark = 1; mark <= 3; mark++) {
            int my = BEAKER_BOTTOM - mark * 2;
            int markCol = mark * 2 <= rows ? 0xFFFFFFFF : PAL['F'];
            pxCol(ox, oy, p, BEAKER_X1 + 1, my, markCol, 200);
        }

        // Falling droplets
        for (Mote d : DRIPS) {
            if (d.live)
                pxCol(ox, oy, p, d.x, d.y, compoundColor, 255);
        }

        // Splashes & Ripple particles
        for (Mote s : SPLASH) {
            if (s.live)
                pxCol(ox, oy, p, s.x, s.y, compoundSecondary, (int) (255 * (1f - s.age / s.life)));
        }
    }

    /** Interactive lab field notebook on the bench. */
    private static void drawNotebookIllustration(float ox, float oy, float p) {
        // Page flip shadow if currently flipping
        if (notebookFlip > 0.05f) {
            int flipCol = PAL['E'];
            pxCol(ox, oy, p, 48 - (int) (notebookFlip * 4f), 46, flipCol, 200);
        }
    }

    /** Dust motes with aerodynamic wake deflection and rising steam plumes. */
    private static void drawAtmosphericParticles(float ox, float oy, float p) {
        // Dust motes
        for (Mote m : DUST) {
            if (!m.live) continue;
            float t = m.age / m.life;
            int a = (int) (100 * Math.sin(Math.PI * t));
            pxCol(ox, oy, p, m.x, m.y, PAL['O'], a);
        }

        // Rising steam plumes
        for (Mote s : STEAM) {
            if (!s.live) continue;
            float t = s.age / s.life;
            pxCol(ox, oy, p, s.x + (float) Math.sin(s.age * 5f) * 0.8f, s.y, PAL['f'], (int) (140 * (1f - t)));
        }
    }

    // =========================================================================
    // 7. INSPECTOR TOOLTIP
    // =========================================================================

    private static void drawInspectorTooltip(float mouseX, float mouseY, float cX1, float cY1, float cX2, float cY2) {
        String title = null;
        String desc = null;

        if (mouseSY >= 0 && mouseSY <= SHELF_TOP) {
            Bottle b = bottleAt(mouseSX, mouseSY);
            if (b != null) {
                title = b.reagent.name + " (" + b.reagent.formula + ")";
                desc = b.reagent.type + " • Click to uncork & dispense";
            }
        } else if (in(HIT_BURNER, mouseSX, mouseSY)) {
            title = "Bunsen Burner [Heat: " + heatSettingName() + "]";
            desc = String.format("%.0f°C • Left click to cycle, Right click/Scroll to adjust", currentTemp);
        } else if (in(HIT_FLASK, mouseSX, mouseSY)) {
            title = "Retort Boiling Flask";
            desc = "Contains " + currentCompound + " • Left click to boil, Right click to swirl";
        } else if (in(HIT_CONDENSER, mouseSX, mouseSY)) {
            title = "Liebig Condenser Coil";
            desc = "Vapor cooling channel • Click to flush cryo-coolant";
        } else if (in(HIT_BEAKER, mouseSX, mouseSY)) {
            title = "Distillate Collection Beaker";
            desc = String.format("Yield: %.1f ml • Click to decant & empty", level * 8f);
        } else if (in(HIT_NOTEBOOK, mouseSX, mouseSY)) {
            title = "Field Alchemist's Logbook [" + (notebookPage + 1) + "/4]";
            desc = NOTEBOOK_TITLES[notebookPage] + " • Click to turn page";
        }

        if (title == null) return;

        FontRendererExtension<?> labelFr = M3.label();
        FontRendererExtension<?> bodyFr = M3.body();
        labelFr.setScale(0.82f);
        bodyFr.setScale(0.72f);

        float tw = Math.max(labelFr.getWidth(title), bodyFr.getWidth(desc)) + 16f;
        float th = 26f;

        float tx = MathUtils.clamp(mouseX + 10f, cX1 + 4f, cX2 - tw - 4f);
        float ty = MathUtils.clamp(mouseY - th - 6f, cY1 + 4f, cY2 - th - 4f);

        DrawUtils.drawShadow(tx, ty, tx + tw, ty + th, 8f, 10f, 100);
        DrawUtils.drawRoundedRect(tx, ty, tx + tw, ty + th, 8f, M3.surfaceContainerHighest());
        DrawUtils.drawRoundedOutline(tx, ty, tx + tw, ty + th, 8f, 1f, M3.alpha(compoundColor, 180));

        labelFr.drawString(title, tx + 8f, ty + 8f, M3.onSurface(), labelFr.CENTREY);
        bodyFr.drawString(desc, tx + 8f, ty + 18f, M3.onSurfaceVariant(), bodyFr.CENTREY);

        labelFr.resetScale();
        bodyFr.resetScale();
    }

    private static String heatSettingName() {
        switch (heatSetting) {
            case 0: return "Pilot Simmer";
            case 1: return "Low Heat";
            case 2: return "Optimal Distill";
            case 3: return "Roaring Torch";
            case 4: return "INFERNO";
            default: return "Normal";
        }
    }

    // =========================================================================
    // 8. INTERACTIVE INPUT HANDLERS
    // =========================================================================

    public static boolean click(float mouseX, float mouseY) {
        return click(mouseX, mouseY, 0);
    }

    public static boolean click(float mouseX, float mouseY, int mouseButton) {
        if (lastP <= 0f || mouseY < lastClipTop)
            return false;

        // 0. Chamber Header bar: cycle reagent / synthesis pulse
        if (mouseY >= chamberY1 && mouseY < chamberY1 + 22f) {
            int nextReagent = ((reagentsMask & 0x3F) + 1) % REAGENTS.length;
            addReagent(REAGENTS[nextReagent]);
            SoundUtils.chordOpen();
            pulse();
            return true;
        }

        // 0b. Chamber Footer bar: cycle heat or trigger decant
        if (mouseY >= chamberY2 - 14f && mouseY <= chamberY2) {
            if (level > 2.0f) {
                draining = true;
                SoundUtils.chordDisable();
            } else {
                heatSetting = (heatSetting + 1) % 5;
                if (heatSetting >= 3) roar = 2f;
                SoundUtils.chordClick();
            }
            return true;
        }

        float sx = (mouseX - lastOx) / lastP;
        float sy = (mouseY - lastOy) / lastP;

        // 1. Shelf Bottles
        Bottle b = bottleAt(sx, sy);
        if (b != null) {
            b.wobble = 0.7f;
            b.fizz = 1.6f;
            if (b.corkY >= 0 && !b.corkFlying) {
                b.corkFlying = true;
                b.corkUp = 0f;
                b.corkV = 16f;
                b.corkXOffset = (RNG.nextFloat() - 0.5f) * 6f;
            }
            // Dispense reagent stream to flask
            for (int i = 0; i < 5; i++) {
                Mote pip = spawn(PIPETTE, b.x1 + 2, SHELF_TOP + 1, 0, 8f + RNG.nextFloat() * 4f, 0);
                pip.color = b.reagent.color;
                pip.life = 1.2f;
            }
            // Synthesize into compound
            addReagent(b.reagent);
            SoundUtils.note(b.reagent.noteDegree);
            return true;
        }

        // 2. Bunsen Burner
        if (in(HIT_BURNER, sx, sy)) {
            if (mouseButton == 1) {
                heatSetting = Math.max(0, heatSetting - 1);
            } else {
                heatSetting = (heatSetting + 1) % 5;
            }
            if (heatSetting >= 3) {
                roar = 3.5f;
                SoundUtils.chordEnable();
            } else {
                SoundUtils.chordClick();
            }
            return true;
        }

        // 3. Retort Flask
        if (in(HIT_FLASK, sx, sy)) {
            if (mouseButton == 1) {
                // Swirl momentum
                swirlVelocity = 18f;
                SoundUtils.chordSlider();
            } else {
                // Boilover burst
                boilOver = 1.5f;
                for (int i = 0; i < 8; i++)
                    spawn(BUBBLES, 6 + RNG.nextInt(7), FLASK_BOTTOM - RNG.nextInt(3), 0, -(5f + RNG.nextFloat() * 3f), 0);
                for (int i = 0; i < 6; i++)
                    spawn(SPLASH, FLASK_NECK_X, FLASK_NECK_TOP, (RNG.nextFloat() - 0.5f) * 12f, -(11f + RNG.nextFloat() * 8f), 1);
                SoundUtils.chordOpen();
            }
            return true;
        }

        // 4. Liebig Condenser
        if (in(HIT_CONDENSER, sx, sy)) {
            rush = 3.5f;
            frostAmount = 1.0f;
            SoundUtils.chordSlider();
            return true;
        }

        // 5. Collection Beaker
        if (in(HIT_BEAKER, sx, sy)) {
            if (level > 2.0f) {
                draining = true;
                SoundUtils.chordDisable();
            } else {
                for (int i = 0; i < 4; i++)
                    spawn(SPLASH, BEAKER_X1 + RNG.nextInt(BEAKER_X2 - BEAKER_X1 + 1), BEAKER_BOTTOM + 1 - level,
                            (RNG.nextFloat() - 0.5f) * 6f, -(5f + RNG.nextFloat() * 4f), 0);
                SoundUtils.chordClick();
            }
            return true;
        }

        // 6. Alchemist's Field Notebook
        if (in(HIT_NOTEBOOK, sx, sy)) {
            notebookPage = (notebookPage + 1) % NOTEBOOK_TITLES.length;
            notebookFlip = 1.0f;
            SoundUtils.chordCategory();
            return true;
        }

        return false;
    }

    public static void release(float mouseX, float mouseY, int state) {
        // Releases active drag states if any
    }

    public static boolean scroll(float mouseX, float mouseY, int dwheel) {
        if (dwheel == 0) return false;
        if (dwheel > 0) {
            heatSetting = Math.min(4, heatSetting + 1);
            if (heatSetting >= 3) roar = 2f;
        } else {
            heatSetting = Math.max(0, heatSetting - 1);
        }
        SoundUtils.chordClick();
        return true;
    }

    /** Briefly bursts bubbles and embers (e.g. on module toggle in the GUI). */
    public static void pulse() {
        for (int i = 0; i < 5; i++) {
            spawn(BUBBLES, 7 + RNG.nextInt(5), FLASK_BOTTOM + 0.4f, 0, -(4f + RNG.nextFloat() * 3f), 0);
        }
        if (heatSetting > 0) {
            Mote emb = spawn(EMBERS, FLAME_X + 1 + (RNG.nextFloat() - 0.5f) * 2f, FLAME_Y - 3f,
                    (RNG.nextFloat() - 0.5f) * 3f, -(6f + RNG.nextFloat() * 5f), 0);
            emb.color = RNG.nextBoolean() ? PAL['O'] : PAL['o'];
            emb.life = 0.8f;
        }
    }

    // =========================================================================
    // 9. ALCHEMY SYNTHESIS & RECIPE ENGINE
    // =========================================================================

    private static void addReagent(ReagentDef r) {
        // Blend colors
        compoundColor = mixRgb(compoundColor, r.color, 0.45f);
        compoundSecondary = mixRgb(compoundSecondary, r.highlight, 0.45f);

        reagentsMask |= (1 << r.id);

        // Check secret recipes
        if ((reagentsMask & 0x3F) == 0x3F) {
            discoverRecipe("PHILOSOPHER'S ARSENIC", "As·Omnia");
        } else if ((reagentsMask & (1 << 0)) != 0 && (reagentsMask & (1 << 1)) != 0) {
            discoverRecipe("PLASMA ACID", "As·HgS·Sol");
        } else if ((reagentsMask & (1 << 2)) != 0 && (reagentsMask & (1 << 4)) != 0) {
            discoverRecipe("ZERO-POINT SINGULARITY", "Co·V₄O₁₀");
        } else if ((reagentsMask & (1 << 1)) != 0 && (reagentsMask & (1 << 2)) != 0) {
            discoverRecipe("THERMAL SHOCK SUPERFLUID", "HgS·CoCl₂");
        } else if ((reagentsMask & (1 << 3)) != 0 && (reagentsMask & (1 << 0)) != 0) {
            discoverRecipe("ELIXIR OF RADIANCE", "Au·As₂O₃");
        } else {
            currentCompound = r.name.toUpperCase();
            currentFormula = r.formula;
        }
    }

    private static void discoverRecipe(String name, String formula) {
        if (!currentCompound.equals(name)) {
            currentCompound = name;
            currentFormula = formula;
            discoveryBanner = name;
            discoveryTimer = 3.2f;
            SoundUtils.hitConfirm();
        }
    }

    // =========================================================================
    // 10. PHYSICS INTEGRATION STEP
    // =========================================================================

    private static boolean in(int[] r, float x, float y) {
        return x >= r[0] && x <= r[2] + 1 && y >= r[1] && y <= r[3] + 1;
    }

    private static Bottle bottleAt(float sx, float sy) {
        if (sy < 0 || sy > SHELF_TOP)
            return null;
        for (Bottle b : BOTTLES)
            if (sx >= b.x1 && sx <= b.x2 + 1)
                return b;
        return null;
    }

    private static void step(boolean hovered, float mouseX, float mouseY, float x1, float x2) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;

        hoverE += ((hovered ? 1f : 0f) - hoverE) * Math.min(1f, dt * 6f);
        roar = Math.max(0f, roar - dt);
        rush = Math.max(0f, rush - dt);
        boilOver = Math.max(0f, boilOver - dt);
        frostAmount = Math.max(0f, frostAmount - dt * 0.25f);
        notebookFlip = Math.max(0f, notebookFlip - dt * 3.5f);
        discoveryTimer = Math.max(0f, discoveryTimer - dt);

        // 3D Parallax drift
        float target = hovered ? ((mouseX - x1) / Math.max(1f, x2 - x1) - 0.5f) * 2.2f : 0f;
        parallax += (target - parallax) * Math.min(1f, dt * 4f);

        // Thermal simulation
        float targetTemp = heatSetting == 0 ? 24f :
                heatSetting == 1 ? 65f :
                heatSetting == 2 ? 102f :
                heatSetting == 3 ? 175f : 275f;
        if (roar > 0f) targetTemp += 60f;
        currentTemp += (targetTemp - currentTemp) * Math.min(1f, dt * 1.8f);

        wireGauzeHeat += ((currentTemp > 90f ? (currentTemp - 90f) / 180f : 0f) - wireGauzeHeat) * Math.min(1f, dt * 1.5f);
        pressure += ((1.0f + (currentTemp > 80f ? (currentTemp - 80f) * 0.008f : 0f)) - pressure) * Math.min(1f, dt * 2.5f);

        float heat = 0.5f + (currentTemp / 90f) + (roar > 0f ? 1.5f : 0f) + (boilOver > 0f ? 2f : 0f);
        float flow = 0.5f + (currentTemp / 95f) + (rush > 0f ? 2.5f : 0f);

        // Swirl fluid dynamics
        swirlAngle += swirlVelocity * dt;
        swirlVelocity *= Math.max(0f, 1f - dt * 3.0f);

        // Boiling bubbles
        if (currentTemp > 50f) {
            bubbleTimer -= dt * heat;
            if (bubbleTimer <= 0f) {
                bubbleTimer = 0.12f + RNG.nextFloat() * 0.25f;
                spawn(BUBBLES, 7 + RNG.nextInt(5), FLASK_BOTTOM + 0.4f, 0, -(3.5f + RNG.nextFloat() * 2.5f), 0);
            }
        }
        for (Mote b : BUBBLES) {
            if (!b.live) continue;
            b.y += b.vy * dt * heat * 0.7f;
            if (b.y < FLASK_SURFACE) {
                b.live = false;
                // Pop with micro splash
                if (RNG.nextFloat() < 0.35f) {
                    spawn(SPLASH, b.x, FLASK_SURFACE, (RNG.nextFloat() - 0.5f) * 4f, -3f, 0);
                }
            }
        }

        // Bunsen burner flying sparks/embers
        if (heatSetting >= 3 || roar > 0f) {
            if (RNG.nextFloat() < dt * (heatSetting * 8f)) {
                Mote emb = spawn(EMBERS, FLAME_X + 1 + (RNG.nextFloat() - 0.5f) * 2f, FLAME_Y - 3f,
                        (RNG.nextFloat() - 0.5f) * 3f, -(6f + RNG.nextFloat() * 6f), 0);
                emb.color = RNG.nextBoolean() ? PAL['O'] : PAL['o'];
                emb.life = 0.6f + RNG.nextFloat() * 0.5f;
            }
        }
        for (Mote emb : EMBERS) {
            if (!emb.live) continue;
            emb.age += dt;
            emb.x += emb.vx * dt;
            emb.y += emb.vy * dt;
            emb.vy += dt * 5f; // Gravity
            if (emb.age >= emb.life) emb.live = false;
        }

        // Droplets from condenser to beaker
        if (currentTemp > 60f && !draining) {
            dripTimer -= dt * flow;
            if (dripTimer <= 0f) {
                dripTimer = 1.1f + RNG.nextFloat() * 0.45f;
                spawn(DRIPS, OUTLET_X, OUTLET_Y + 1, 0, 0, 0);
            }
        }
        float surface = BEAKER_BOTTOM + 1 - level;
        for (Mote d : DRIPS) {
            if (!d.live) continue;
            d.vy += dt * 42f;
            d.y += d.vy * dt;
            if (d.y >= surface - 0.5f) {
                d.live = false;
                level = Math.min(BEAKER_ROWS, level + 0.14f);
                spawn(SPLASH, OUTLET_X, surface - 1, -3.2f, -5.5f, 0);
                spawn(SPLASH, OUTLET_X, surface - 1, 3.2f, -5.5f, 0);
            }
        }

        // Splash physics
        for (Mote s : SPLASH) {
            if (!s.live) continue;
            s.vy += dt * 40f;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.age += dt;
            if (s.age > 0.8f || s.y > BENCH_TOP) s.live = false;
        }

        // Beaker draining
        if (level >= BEAKER_ROWS - 0.01f) draining = true;
        if (draining) {
            level -= dt * 1.8f;
            if (level <= 1.5f) {
                level = 1.5f;
                draining = false;
            }
        }

        // Shelf bottles lifting & uncorking
        shelfTimer -= dt;
        if (shelfTimer <= 0f) {
            shelfTimer = 1.5f + RNG.nextFloat() * 2f;
            Bottle b = BOTTLES.get(RNG.nextInt(BOTTLES.size()));
            b.fizz = Math.max(b.fizz, 0.4f);
        }
        Bottle over = hovered ? bottleAt(mouseSX, mouseSY) : null;
        for (Bottle b : BOTTLES) {
            b.lift += ((b == over ? 1f : 0f) - b.lift) * Math.min(1f, dt * 14f);
            b.wobble = Math.max(0f, b.wobble - dt);
            if (b.fizz > 0f) {
                b.fizz -= dt;
                if (b.liquidY >= 0 && RNG.nextFloat() < dt * 10f) {
                    Mote f = spawn(FIZZ, Math.min(b.x2 - 1, b.liquidX + RNG.nextInt(Math.max(1, b.x2 - b.liquidX - 1))),
                            b.liquidY, 0, -2.4f, 0);
                    f.color = b.reagent.highlight;
                    f.life = 1.0f;
                }
            }
            if (b.corkFlying) {
                b.corkV -= dt * 45f;
                b.corkUp += b.corkV * dt;
                b.corkXOffset += dt * 3f;
                if (b.corkUp <= 0f && b.corkV < 0f) {
                    b.corkUp = 0f;
                    b.corkFlying = false;
                }
            }
        }
        for (Mote f : FIZZ) {
            if (!f.live) continue;
            f.y += f.vy * dt;
            f.age += dt;
            if (f.age > f.life) f.live = false;
        }

        // Pipette stream
        for (Mote pip : PIPETTE) {
            if (!pip.live) continue;
            pip.y += pip.vy * dt;
            pip.age += dt;
            if (pip.y >= FLASK_SURFACE) pip.live = false;
        }

        // Dust motes & steam plumes
        moteTimer -= dt;
        if (moteTimer <= 0f) {
            moteTimer = 0.5f + RNG.nextFloat() * 0.7f;
            Mote m = spawn(DUST, 2 + RNG.nextInt(SW - 4), 14 + RNG.nextInt(30),
                    (RNG.nextFloat() - 0.5f) * 1.4f, -0.4f - RNG.nextFloat() * 0.6f, 0);
            m.life = 3.5f + RNG.nextFloat() * 2f;

            if (currentTemp > 80f || level > 2.5f || roar > 0f) {
                Mote s = spawn(STEAM, BEAKER_X1 + 1 + RNG.nextInt(BEAKER_X2 - BEAKER_X1 - 1), BEAKER_TOP - 1,
                        (RNG.nextFloat() - 0.5f) * 0.8f, -2.4f, 0);
                s.life = 1.8f;
            }
        }

        // Interactive aerodynamic cursor wake deflecting dust motes
        for (Mote m : DUST) {
            if (!m.live) continue;
            if (hovered && mouseSX >= 0) {
                float dx = m.x - mouseSX;
                float dy = m.y - mouseSY;
                float distSq = dx * dx + dy * dy;
                if (distSq < 36f && distSq > 0.1f) {
                    float dist = (float) Math.sqrt(distSq);
                    m.vx += (dx / dist) * dt * 15f;
                    m.vy += (dy / dist) * dt * 15f;
                }
            }
            m.age += dt;
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            if (m.age >= m.life) m.live = false;
        }

        for (Mote s : STEAM) {
            if (!s.live) continue;
            s.age += dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.age >= s.life) s.live = false;
        }
    }

    private static Mote spawn(Mote[] pool, float x, float y, float vx, float vy, int kind) {
        Mote slot = null;
        for (Mote m : pool)
            if (!m.live) { slot = m; break; }
        if (slot == null) {
            slot = pool[0];
            for (Mote m : pool) if (m.age > slot.age) slot = m;
        }
        slot.live = true;
        slot.x = x; slot.y = y; slot.vx = vx; slot.vy = vy;
        slot.age = 0f; slot.life = 1f; slot.kind = kind; slot.color = compoundColor;
        return slot;
    }

    // =========================================================================
    // 11. COLOR HELPER UTILITIES
    // =========================================================================

    private static int mixRgb(int c1, int c2, float t) {
        t = MathUtils.clamp01(t);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}
