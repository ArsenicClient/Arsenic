package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.awt.*;

@ModuleInfo(name = "CPSAssist", category = ModuleCategory.GHOST)
public class CPSAssist extends Module {

    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0, 100, 80, 1));
    public final BooleanProperty leftClick = new BooleanProperty("Left Click", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);
    public final BooleanProperty onlyWhileTargeting = new BooleanProperty("Only While Targeting", false);
    public final BooleanProperty rightClick = new BooleanProperty("Right Click", false);
    public final BooleanProperty blocksOnly = new BooleanProperty("Blocks Only", true);
    public final BooleanProperty above5 = new BooleanProperty("Above 5 CPS", false);

    private Robot bot;
    private boolean engagedLeft;
    private boolean engagedRight;

    @Override
    protected void onEnable() {
        try {
            this.bot = new Robot();
        } catch (AWTException e) {
            setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        this.engagedLeft = false;
        this.engagedRight = false;
        this.bot = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventMouse.Down> onMouseDown = event -> {
        if (chance.getValue().getInput() == 0) return;

        double ch;
        if (event.button == 0 && leftClick.getValue()) {
            if (engagedLeft) {
                engagedLeft = false;
                return;
            }

            if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon()) return;

            if (onlyWhileTargeting.getValue() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null))
                return;

            if (chance.getValue().getInput() != 100) {
                ch = Math.random();
                if (ch >= chance.getValue().getInput() / 100.0) {
                    fix(0);
                    return;
                }
            }

            bot.mouseRelease(16);
            bot.mousePress(16);
            engagedLeft = true;
        } else if (event.button == 1 && rightClick.getValue()) {
            if (engagedRight) {
                engagedRight = false;
                return;
            }

            if (blocksOnly.getValue()) {
                ItemStack item = mc.thePlayer.getHeldItem();
                if (item == null || !(item.getItem() instanceof ItemBlock)) {
                    fix(1);
                    return;
                }
            }

            if (chance.getValue().getInput() != 100) {
                ch = Math.random();
                if (ch >= chance.getValue().getInput() / 100.0) {
                    fix(1);
                    return;
                }
            }

            bot.mouseRelease(4);
            bot.mousePress(4);
            engagedRight = true;
        }
    };

    private void fix(int t) {
        if (t == 0 && engagedLeft && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            bot.mouseRelease(16);
        } else if (t == 1 && engagedRight && !org.lwjgl.input.Mouse.isButtonDown(1)) {
            bot.mouseRelease(4);
        }
    }
}
