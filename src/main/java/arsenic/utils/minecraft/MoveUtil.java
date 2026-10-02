package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public class MoveUtil extends UtilityClass {

    public static final double WALK_SPEED = .221;
    public static final double WEB_SPEED = .105 / WALK_SPEED;
    public static final double SWIM_SPEED = .115f / WALK_SPEED;
    public static final double SNEAK_SPEED = .3f;
    public static final double SPRINTING_SPEED = 1.3f;
    public static final double[] DEPTH_STRIDER = {
            1.f, .1645f / SWIM_SPEED / WALK_SPEED, .1995f / SWIM_SPEED / WALK_SPEED, 1.f / SWIM_SPEED
    };

    /** 1.8's moveForward / moveStrafing are LivingEntity#zza / #xxa. */
    public static boolean isMoving() {
        return mc.player.zza != 0 || mc.player.xxa != 0;
    }

    public static boolean isInLiquid() {
        return mc.player.isInWater() || mc.player.isInLava();
    }

    public static boolean enoughMovementForSprinting() {
        return Math.abs(mc.player.zza) >= .8f || Math.abs(mc.player.xxa) >= .8f;
    }

    public static void strafe(double speed) {
        float direction = (float) Math.toRadians(getDirection());
        Vec3 motion = mc.player.getDeltaMovement();
        if (isMoving()) {
            mc.player.setDeltaMovement(-Math.sin(direction) * speed, motion.y, Math.cos(direction) * speed);
        } else {
            mc.player.setDeltaMovement(0, motion.y, 0);
        }
    }

    public static void horitzontalClip(float amount) {
        float direction = (float) Math.toRadians(MoveUtil.getDirection());
        double deltaX = -amount * Math.sin(direction);
        double deltaZ = amount * Math.cos(direction);
        mc.player.setPos(mc.player.getX() + deltaX, mc.player.getY(), mc.player.getZ() + deltaZ);
    }

    public static float getDirection() {
        float direction = mc.player.getYRot();
        float forward = mc.player.zza, strafe = mc.player.xxa;
        if (forward > 0) {
            if (strafe > 0) {
                direction -= 45;
            } else if (strafe < 0) {
                direction += 45;
            }
        } else if (forward < 0) {
            if (strafe > 0) {
                direction -= 135;
            } else if (strafe < 0) {
                direction += 135;
            } else {
                direction -= 180;
            }
        } else {
            if (strafe > 0) {
                direction -= 90;
            } else if (strafe < 0) {
                direction += 90;
            }
        }
        return direction;
    }

    public static float getMovementYaw() {
        float n = 0.0f;
        final double n2 = mc.player.input.getMoveVector().y;
        final double n3 = mc.player.input.getMoveVector().x;
        if (n2 == 0.0) {
            if (n3 == 0.0) {
                n = 180.0f;
            } else if (n3 > 0.0) {
                n = 90.0f;
            } else if (n3 < 0.0) {
                n = -90.0f;
            }
        } else if (n2 > 0.0) {
            if (n3 == 0.0) {
                n = 180.0f;
            } else if (n3 > 0.0) {
                n = 135.0f;
            } else if (n3 < 0.0) {
                n = -135.0f;
            }
        } else if (n2 < 0.0) {
            if (n3 == 0.0) {
                n = 0.0f;
            } else if (n3 > 0.0) {
                n = 45.0f;
            } else if (n3 < 0.0) {
                n = -45.0f;
            }
        }
        return mc.player.getYRot() + n;
    }

    public static double getBaseSpeed() {
        double speed;
        boolean useModifiers = false;
        if (MoveUtil.isInLiquid()) {
            speed = SWIM_SPEED * WALK_SPEED;
            final int level = Math.min(3, ItemUtils.enchantLevel(Enchantments.DEPTH_STRIDER, mc.player.getItemBySlot(EquipmentSlot.FEET)));
            if (level > 0) {
                speed *= DEPTH_STRIDER[level];
                useModifiers = true;
            }
        } else if (mc.player.isShiftKeyDown()) {
            speed = SNEAK_SPEED * WALK_SPEED;
        } else {
            speed = WALK_SPEED;
            useModifiers = true;
        }
        if (useModifiers) {
            if (enoughMovementForSprinting())
                speed *= SPRINTING_SPEED;
            MobEffectInstance speedEffect = mc.player.getEffect(MobEffects.SPEED);
            if (speedEffect != null)
                speed *= 1 + (.2 * (speedEffect.getAmplifier() + 1));
            if (mc.player.hasEffect(MobEffects.SLOWNESS))
                speed = .29;
        }
        return speed;
    }

    public static float getPerfectValue(float noSpeed, float speed1, float speed2) {
        MobEffectInstance effect = mc.player.getEffect(MobEffects.SPEED);
        if (effect == null)
            return noSpeed;
        return switch (effect.getAmplifier()) {
            case 0 -> speed1;
            case 1 -> speed2;
            default -> 0;
        };
    }

    public static float getSpeed() {
        Vec3 motion = mc.player.getDeltaMovement();
        return (float) Math.hypot(motion.x, motion.z);
    }

    public static void stop() {
        mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
    }
}
