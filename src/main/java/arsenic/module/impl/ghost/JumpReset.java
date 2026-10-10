package arsenic.module.impl.ghost;

import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(name = "JumpReset", category = ModuleCategory.COMBAT)
public class JumpReset extends Module {
    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0.0, 1, 1, 0.01));
    public final BooleanProperty sound = new BooleanProperty("Sound", true);

    private static final long SWING_WINDOW_MS = 400;
    private static final double MELEE_RANGE = 4.5;
    // Same upward speed as a normal jump in 1.8
    private static final double JUMP_VELOCITY = 0.42;

    private static final long HURT_WINDOW_MS = 150;

    private final Map<Integer, Long> swings = new ConcurrentHashMap<>();
    private volatile long lastHurtAt;

    @Override
    protected void onDisable() {
        swings.clear();
        lastHurtAt = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        long now = System.currentTimeMillis();
        if (event.getPacket() instanceof S0BPacketAnimation) {
            S0BPacketAnimation swing = (S0BPacketAnimation) event.getPacket();
            if (swing.getAnimationType() == 0) {
                swings.put(swing.getEntityID(), now);
                if (swings.size() > 64)
                    swings.values().removeIf(t -> now - t > SWING_WINDOW_MS);
            }
        } else if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) event.getPacket();
            if (status.getOpCode() == 2 && status.getEntity(mc.theWorld) == mc.thePlayer)
                lastHurtAt = now;
        } else if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) event.getPacket();
            // Jump before the velocity packet is applied, so the knockback's own Y speed replaces the jump's. Jumping after
            // it leaves our Y speed higher than the server's, which Grim flags (AntiKB, then Simulation on the vertical drag).
            if (velocity.getEntityID() == mc.thePlayer.getEntityId()
                    && (velocity.getMotionX() != 0 || velocity.getMotionZ() != 0)
                    && now - lastHurtAt <= HURT_WINDOW_MS
                    && Math.random() <= chance.getValue().getInput()
                    && mc.thePlayer.onGround && !mc.thePlayer.isInWater()
                    && hitByPlayerMelee(now)) {
                jump();
                if (sound.getValue())
                    SoundUtils.playEvent("cmaj5", 1.5f);
            }
        }
    };

    private boolean hitByPlayerMelee(long hitAt) {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead)
                continue;
            Long swung = swings.get(player.getEntityId());
            if (swung == null || hitAt - swung > SWING_WINDOW_MS)
                continue;
            if (RotationUtils.getDistanceToEntityBox(player) > MELEE_RANGE)
                continue;
            if (isProjectileItem(player.getHeldItem()))
                continue;
            return true;
        }
        return false;
    }

    private static boolean isProjectileItem(ItemStack stack) {
        if (stack == null)
            return false;
        Item item = stack.getItem();
        return item instanceof ItemFishingRod || item instanceof ItemEgg || item instanceof ItemSnowball
                || item instanceof ItemBow || item instanceof ItemEnderPearl || item instanceof ItemPotion;
    }

    private void jump() {
        mc.thePlayer.motionY = JUMP_VELOCITY;
        mc.thePlayer.isAirBorne = true;
        SyntheticKeys.press(SyntheticKeys.Key.JUMP);
    }
}
