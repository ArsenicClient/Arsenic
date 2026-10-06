package arsenic.gui.click.impl;

import arsenic.addon.AddonManager;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * What the ClickGUI shows in its main card while the Addon Manager is open. Two views, switched with the Packs and
 * Addons buttons in the header: all packs, or all addons. Clicking a pack (left or right) opens it and lists its
 * addons. Cards are laid out in two columns and scrolled exactly like a module category; the ClickGUI's search box
 * filters whatever list is showing.
 */
public class AddonPageComponent {

    /** Width of a card in the full-width layout, in the units of {@link UITheme#space}. */
    private static final float CARD_STEPS = 35.5f;

    private enum View { PACKS, ADDONS }

    private final Supplier<String> query;
    private final Runnable clearQuery;
    private View view = View.PACKS;
    private String openPackId;

    private List<AddonManager.PackInfo> packs = new ArrayList<>();
    private List<AddonManager.Info> addons = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    private List<AddonCardComponent> left = new ArrayList<>(), right = new ArrayList<>();
    private String laidOutFor;
    private String lastQuery;
    private boolean resetScroll = true;
    private float scroll, targetScroll, maxHeight, lastMaxScroll;

    /** Hit areas of the Packs, Addons and (inside a pack) Back buttons. */
    private final float[][] tabRects = new float[3][4];

    public AddonPageComponent(Supplier<String> query, Runnable clearQuery) {
        this.query = query;
        this.clearQuery = clearQuery;
    }

    /** Scroll back to the top the next time the list is laid out (used when the page is opened). */
    public void reset() {
        resetScroll = true;
    }

    /** Re-reads what is on disk (call when the page opens and after any change). */
    public void refresh() {
        AddonManager manager = Arsenic.getArsenic().getAddonManager();
        packs = manager.listPacks();
        addons = manager.listLoose();
        for (AddonManager.PackInfo pack : packs)
            addons.addAll(pack.addons);
        addons.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        errors = new ArrayList<>(manager.getErrors());

        if (openPackId != null && findPack(openPackId) == null)
            openPackId = null;
        laidOutFor = null;
    }

    private AddonManager.PackInfo findPack(String id) {
        for (AddonManager.PackInfo pack : packs)
            if (pack.meta.id.equals(id))
                return pack;
        return null;
    }

    private void openPack(String id) {
        clearQuery.run();
        openPackId = id;
        resetScroll = true;
        laidOutFor = null;
    }

    private void layout(String q) {
        List<AddonCardComponent> cards = new ArrayList<>();
        AddonManager.PackInfo open = openPackId == null ? null : findPack(openPackId);

        boolean searching = !q.isEmpty();
        if (searching) {
            // searching looks through everything: every pack and every addon, whichever page is open
            for (AddonManager.PackInfo pack : packs) {
                final String id = pack.meta.id;
                cards.add(AddonCardComponent.pack(pack, () -> openPack(id)));
            }
            for (AddonManager.Info info : addons)
                cards.add(AddonCardComponent.addon(info));
        } else if (open != null) {
            // a pack's own page: the pack itself on top (install or remove all of it), then its addons
            cards.add(AddonCardComponent.pack(open, null));
            for (AddonManager.Info info : open.addons)
                cards.add(AddonCardComponent.addon(info));
        } else if (view == View.PACKS) {
            for (AddonManager.PackInfo pack : packs) {
                final String id = pack.meta.id;
                cards.add(AddonCardComponent.pack(pack, () -> openPack(id)));
            }
        } else {
            // addons that are not part of a pack; pack members are on their pack's page
            for (AddonManager.Info info : addons)
                if (info.pack == null)
                    cards.add(AddonCardComponent.addon(info));
        }

        if (!searching)
            for (String error : errors)
                cards.add(AddonCardComponent.error(error.split("\n")[0]));

        left = new ArrayList<>();
        right = new ArrayList<>();
        for (AddonCardComponent card : cards) {
            if (searching && (card.isError() || !card.matches(q)))
                continue;
            card.setWidthSteps(CARD_STEPS);
            ((left.size() + right.size()) % 2 == 0 ? left : right).add(card);
        }
        laidOutFor = q;
        // only a different list sends you back to the top; reloading after enabling or disabling keeps your place
        if (resetScroll || !q.equals(lastQuery)) {
            scroll = 0;
            targetScroll = 0;
        }
        resetScroll = false;
        lastQuery = q;
    }

    private void relayoutIfNeeded() {
        String q = query.get().trim().toLowerCase(Locale.ROOT);
        if (!q.equals(laidOutFor))
            layout(q);
    }

    /** The Packs and Addons buttons, drawn in the header where the logo is in the module view. */
    public void drawTabs(float x, float top, float bottom, float maxRight, RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        float h = (bottom - top) * 0.58f;
        float mid = (top + bottom) / 2f;
        float pad = h * 0.55f;
        String[] labels = {"Packs", "Addons"};
        boolean[] active = {view == View.PACKS, view == View.ADDONS && openPackId == null};

        float cx = x;
        for (int i = 0; i < 2; i++) {
            float w = fr.getWidth(labels[i]) + pad * 2f;
            float x1 = cx, x2 = cx + w, y1 = mid - h / 2f, y2 = mid + h / 2f;
            tabRects[i][0] = x1;
            tabRects[i][1] = y1;
            tabRects[i][2] = x2;
            tabRects[i][3] = y2;
            boolean over = MathUtils.inside(ri.getMouseX(), ri.getMouseY(), x1, y1, x2, y2);
            float radius = h / 2f;

            if (active[i]) {
                DrawUtils.drawGradientRoundedRect(x1, y1, x2, y2, radius,
                        UITheme.accent(), UITheme.accent(), UITheme.accentAlt(), UITheme.accentAlt());
                DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, UITheme.alpha(ThemeManager.getWhite(), over ? 150 : 60));
                fr.drawString(labels[i], (x1 + x2) / 2f, mid, ThemeManager.getWhite(), fr.CENTREX, fr.CENTREY);
            } else {
                UITheme.surface(x1, y1, x2, y2, radius, UITheme.alpha(0x000000, over ? 190 : 150), UITheme.Elevation.RAISED, 0.6f);
                DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, UITheme.alpha(ThemeManager.getWhite(), over ? 150 : 70));
                fr.drawString(labels[i], (x1 + x2) / 2f, mid,
                        over ? ThemeManager.getWhite() : UITheme.alpha(ThemeManager.getWhite(), 200), fr.CENTREX, fr.CENTREY);
            }
            cx = x2 + pad * 0.6f;
        }

        // inside a pack: a back button, then which pack this is
        java.util.Arrays.fill(tabRects[2], 0f);
        AddonManager.PackInfo open = openPackId == null ? null : findPack(openPackId);
        if (open != null) {
            String label = "Back";
            float w = fr.getWidth(label) + pad * 2f;
            float x1 = cx, x2 = cx + w, y1 = mid - h / 2f, y2 = mid + h / 2f;
            tabRects[2][0] = x1;
            tabRects[2][1] = y1;
            tabRects[2][2] = x2;
            tabRects[2][3] = y2;
            boolean over = MathUtils.inside(ri.getMouseX(), ri.getMouseY(), x1, y1, x2, y2);
            UITheme.surface(x1, y1, x2, y2, h / 2f, UITheme.alpha(0x000000, over ? 190 : 150), UITheme.Elevation.RAISED, 0.6f);
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, h / 2f, 1f, UITheme.alpha(UITheme.accent(), over ? 220 : 120));
            fr.drawString(label, (x1 + x2) / 2f, mid, over ? ThemeManager.getWhite() : UITheme.alpha(ThemeManager.getWhite(), 220), fr.CENTREX, fr.CENTREY);
            cx = x2 + pad * 0.6f;
            fr.drawString(open.meta.name, cx + pad * 0.4f, mid, ThemeManager.getTextSecondary(), fr.CENTREY);
            cx += pad * 0.4f + fr.getWidth(open.meta.name);
        }

        // where installed addons end up, to the right of the buttons (cut short if the room runs out)
        String note = "Installed addons can be found in the ClickGUI.";
        float room = maxRight - (cx + pad);
        while (note.length() > 4 && fr.getWidth(note) > room)
            note = note.substring(0, note.length() - 1);
        if (room > fr.getWidth("Inst"))
            fr.drawString(note, cx + pad, mid, ThemeManager.getTextMuted(), fr.CENTREY);
        RenderUtils.resetColorText();
    }

    public boolean clickTabs(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0)
            return false;
        for (int i = 0; i < 3; i++) {
            float[] r = tabRects[i];
            if (r[2] <= r[0] || !MathUtils.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]))
                continue;
            SoundUtils.chordCategory();
            // Back leaves the pack and returns to the list it was opened from
            if (i < 2)
                view = i == 0 ? View.PACKS : View.ADDONS;
            openPackId = null;
            resetScroll = true;
            laidOutFor = null;
            return true;
        }
        return false;
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        relayoutIfNeeded();
        // content may have got shorter (an addon disappeared): keep the scroll inside the new range
        targetScroll = Math.max(targetScroll, -lastMaxScroll);
        scroll = Math.max(scroll, -lastMaxScroll);
        maxHeight = 0;
        scroll += (targetScroll - scroll) * GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;
        drawSection(left, pi, ri);
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        drawSection(right, pi, ri);
    }

    private void drawSection(List<AddonCardComponent> cards, PosInfo pi, RenderInfo ri) {
        pi.moveY(scroll);
        float top = pi.getY();
        float gap = ri.getGuiScreen().width / 100f;
        pi.moveY(gap);
        for (AddonCardComponent card : cards)
            pi.moveY(card.updateComponent(pi, ri) + gap);
        float used = pi.getY() - top;
        if (used > maxHeight)
            maxHeight = used;
    }

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        for (AddonCardComponent card : new ArrayList<>(left))
            card.handleClick(mouseX, mouseY, mouseButton);
        for (AddonCardComponent card : new ArrayList<>(right))
            card.handleClick(mouseX, mouseY, mouseButton);
    }

    public void scroll(int amount) {
        targetScroll += amount;
        targetScroll = Math.max(Math.min(0, targetScroll), -maxHeight);
    }

    public void subtractFromMaxScrollHeight(float f) {
        maxHeight = Math.max(0, maxHeight - f);
        lastMaxScroll = maxHeight;
    }

    public void drawScrollbar(float x, float y, float barHeight, RenderInfo ri) {
        if (maxHeight <= 0)
            return;
        float thumbH = barHeight * (barHeight / (barHeight + maxHeight));
        float thumbY = y + (scroll / -maxHeight) * (barHeight - thumbH);
        DrawUtils.drawRoundedRect(x - 3, y, x - 1, y + barHeight, 1f, ThemeManager.getScrollbarTrack());
        DrawUtils.drawRoundedRect(x - 3, thumbY, x - 1, thumbY + thumbH, 1f, ThemeManager.getScrollbarThumb());
    }
}
