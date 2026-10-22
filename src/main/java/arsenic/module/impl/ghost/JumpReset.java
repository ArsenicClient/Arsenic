package arsenic.module.impl.ghost;

import arsenic.module.ModuleTier;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(name = "JumpReset", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
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
        if (event.getPacket() instanceof ClientboundSwingAnimationPacket swing) {
            if (swing.hand() == InteractionHand.MAIN_HAND) {
                swings.put(swing.entityId(), now);
                if (swings.size() > 64)
                    swings.values().removeIf(t -> now - t > SWING_WINDOW_MS);
            }
        } else if (event.getPacket() instanceof ClientboundDamageEventPacket damage) {
            // hurt used to be entity status 2; it has its own packet now
            if (damage.entityId() == mc.player.getId())
                lastHurtAt = now;
        } else if (event.getPacket() instanceof ClientboundSetEntityMotionPacket) {
            ClientboundSetEntityMotionPacket velocity = (ClientboundSetEntityMotionPacket) event.getPacket();
            // Jump before the velocity packet is applied, so the knockback's own Y speed replaces the jump's. Jumping after
            // it leaves our Y speed higher than the server's, which Grim flags (AntiKB, then Simulation on the vertical drag).
            if (velocity.id() == mc.player.getId()
                    && (velocity.movement().x != 0 || velocity.movement().z != 0)
                    && now - lastHurtAt <= HURT_WINDOW_MS
                    && Math.random() <= chance.getValue().getInput()
                    && mc.player.onGround() && !mc.player.isInWater()
                    && hitByPlayerMelee(now)) {
                jump();
                if (sound.getValue())
                    SoundUtils.playEvent("cmaj5", 1.5f);
            }
        }
    };

    private boolean hitByPlayerMelee(long hitAt) {
        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isRemoved())
                continue;
            Long swung = swings.get(player.getId());
            if (swung == null || hitAt - swung > SWING_WINDOW_MS)
                continue;
            if (RotationUtils.getDistanceToEntityBox(player) > MELEE_RANGE)
                continue;
            if (isProjectileItem(player.getMainHandItem()))
                continue;
            return true;
        }
        return false;
    }

    private static boolean isProjectileItem(ItemStack stack) {
        if (stack == null)
            return false;
        Item item = stack.getItem();
        return item instanceof FishingRodItem || item instanceof EggItem || item instanceof SnowballItem
                || item instanceof BowItem || item instanceof EnderpearlItem || item instanceof PotionItem;
    }

    private void jump() {
        net.minecraft.world.phys.Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, JUMP_VELOCITY, motion.z);
        mc.player.needsSync = true;
        SyntheticKeys.press(SyntheticKeys.Key.JUMP);
    }
}
