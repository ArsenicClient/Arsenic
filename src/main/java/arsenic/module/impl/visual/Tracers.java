package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import arsenic.utils.render.RenderUtils;
import net.minecraft.util.MathHelper;

import java.awt.*;

@ModuleInfo(name = "Tracers", category = ModuleCategory.WORLD)
public class Tracers extends Module {
    public BooleanProperty colorByDistance = new BooleanProperty("Color by distance", false);
    @PropertyInfo(reliesOn = "Color by distance", value = "false")
    public ColourProperty color = new ColourProperty("Color:", Color.green.getRGB());
    public DoubleProperty lineWidth = new DoubleProperty("Line Width", new DoubleValue(1.0, 5.0, 1.0, 0.1));
    public BooleanProperty bedWars = new BooleanProperty("BedWars", false);

    private final float maxDistance = 40.0f;
    private final float minDistance = 10.0f;

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        for (EntityPlayer entity : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (entity == mc.thePlayer)
                continue;
            if (Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isBot(entity))
                continue;

            if (colorByDistance.getValue()) {
                float distance = mc.thePlayer.getDistanceToEntity(entity);
                float clampedDistance = MathHelper.clamp_float(distance, minDistance, maxDistance);
                float t = (clampedDistance - minDistance) / (maxDistance - minDistance);

                Color color = getColorForDistance(t);
                RenderUtils.drawLineToEntity(entity, color.getRed(), color.getGreen(), color.getBlue(), 255, lineWidth.getValue().getInput());
                continue;
            }

            Color color = new Color(bedWars.getValue() ? getBedWarsColor(entity) : this.color.getValue());
            RenderUtils.drawLineToEntity(entity, color.getRed(), color.getGreen(), color.getBlue(), 255, lineWidth.getValue().getInput());
        }
    };

    private Color getColorForDistance(float t) {
        if (t < 0.5) {
            return new Color(
                    1.0f,
                    t * 2.0f,
                    0.0f
            );
        } else {
            return new Color(
                    (1.0f - (t - 0.5f) * 2.0f),
                    1.0f,
                    0.0f
            );
        }
    }

    public int getBedWarsColor(EntityPlayer entityPlayer) {
        ItemStack stack = entityPlayer.getCurrentArmor(2);
        if (stack == null)
            return color.getValue();
        NBTTagCompound nbttagcompound = stack.getTagCompound();
        if (nbttagcompound != null) {
            NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundTag("display");
            if (nbttagcompound1 != null && nbttagcompound1.hasKey("color", 3)) {
                return nbttagcompound1.getInteger("color");
            }
        }

        return color.getValue();
    }
}
