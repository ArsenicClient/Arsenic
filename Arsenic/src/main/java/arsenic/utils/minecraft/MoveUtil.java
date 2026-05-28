package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class MoveUtil extends UtilityClass {
    public static final double WALK_SPEED = .221;
    public static final double WEB_SPEED = .105 / WALK_SPEED;
    public static final double SWIM_SPEED = .115f / WALK_SPEED;
    public static final double SNEAK_SPEED = .3f;
    public static final double SPRINTING_SPEED = 1.3f;
    public static final double[] DEPTH_STRIDER = {
            1.f, .1645f / SWIM_SPEED / WALK_SPEED, .1995f / SWIM_SPEED / WALK_SPEED, 1.f / SWIM_SPEED
    };

    public static boolean isMoving() {
        return mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0;
    }

    public static boolean isInLiquid() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
    }

    public static boolean enoughMovementForSprinting() {
        return Math.abs(mc.thePlayer.moveForward) >= .8f || Math.abs(mc.thePlayer.moveStrafing) >= .8f;
    }

    public static void strafe(double speed) {
        float direction = (float) Math.toRadians(getDirection());

        if (isMoving()) {
            mc.thePlayer.motionX = -Math.sin(direction) * speed;
            mc.thePlayer.motionZ = Math.cos(direction) * speed;
        } else {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }

    public static void horitzontalClip(float amount) {
        float direction = (float) Math.toRadians(MoveUtil.getDirection());
        double deltaX = -amount * Math.sin(direction);
        double deltaZ = amount * Math.cos(direction);

        mc.thePlayer.setPosition(mc.thePlayer.posX + deltaX, mc.thePlayer.posY, mc.thePlayer.posZ + deltaZ);
    }

    public static float getDirection() {
        float direction = mc.thePlayer.rotationYaw;

        if (mc.thePlayer.moveForward > 0) {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 45;
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 45;
            }
        } else if (mc.thePlayer.moveForward < 0) {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 135;
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 135;
            } else {
                direction -= 180;
            }
        } else {
            if (mc.thePlayer.moveStrafing > 0) {
                direction -= 90;
            } else if (mc.thePlayer.moveStrafing < 0) {
                direction += 90;
            }
        }

        return direction;
    }

    public static float getMovementYaw() {
        float n = 0.0f;
        final double n2 = mc.thePlayer.movementInput.moveForward;
        final double n3 = mc.thePlayer.movementInput.moveStrafe;
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
        return mc.thePlayer.rotationYaw + n;
    }

    public static double getBaseSpeed() {
        double speed;
        boolean useModifiers = false;

        if (MoveUtil.isInLiquid()) {
            speed = SWIM_SPEED * WALK_SPEED;

            final int level = EnchantmentHelper.getDepthStriderModifier(mc.thePlayer);

            if (level > 0) {
                speed *= DEPTH_STRIDER[level];
                useModifiers = true;
            }
        } else if (mc.thePlayer.isSneaking()) {
            speed = SNEAK_SPEED * WALK_SPEED;
        } else {
            speed = WALK_SPEED;
            useModifiers = true;
        }

        if (useModifiers) {
            if (enoughMovementForSprinting())
                speed *= SPRINTING_SPEED;

            if (mc.thePlayer.isPotionActive(Potion.moveSpeed))
                speed *= 1 + (.2 * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1));

            if (mc.thePlayer.isPotionActive(Potion.moveSlowdown))
                speed = .29;
        }

        return speed;
    }

    public static float getPerfectValue(float noSpeed, float speed1, float speed2) {
        float speed = 0;

        for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
            if (effect.getPotionID() == 1) {
                int amplifier = effect.getAmplifier();
                switch (amplifier) {
                    case 1:
                        speed = speed2;
                        break;
                    case 0:
                        speed = speed1;
                        break;
                    default:
                        speed = 0;
                        break;
                }
            }
        }

        if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            speed = noSpeed;
        }

        return speed;
    }

    public static float getSpeed() {
        return (float) Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
    }

    public static void stop() {
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;
    }
}
