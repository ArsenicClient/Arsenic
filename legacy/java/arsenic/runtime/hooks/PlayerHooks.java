package arsenic.runtime.hooks;

import arsenic.runtime.Platform;
import arsenic.event.impl.*;
import arsenic.runtime.Access;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.client.Cape;
import arsenic.module.impl.client.CapeHandler;
import arsenic.module.impl.ghost.HitSelect;
import arsenic.module.impl.movement.NoJumpDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import org.lwjgl.input.Mouse;

import static arsenic.main.MinecraftAPI.mouseDownLastTick;

/** Player, living entity and player controller hooks. */
public final class PlayerHooks {

    private static final Access.FieldRef JUMP_TICKS = Access.field(EntityLivingBase.class, "jumpTicks");
    private static final Access.MethodRef GET_JUMP_UPWARDS_MOTION = Access.method(EntityLivingBase.class, "getJumpUpwardsMotion");

    // the local player is the only EntityPlayerSP, so its pre-update state can live here
    private static double cachedX, cachedY, cachedZ;
    private static boolean cachedOnGround;
    private static float cachedRotationPitch, cachedRotationYaw;

    private PlayerHooks() {}

    /** EntityPlayerSP.onUpdateWalkingPlayer HEAD. @return true to cancel */
    public static boolean onUpdateWalkingPlayerHead(EntityPlayerSP self) {
        cachedX = self.posX;
        cachedY = self.posY;
        cachedZ = self.posZ;

        cachedOnGround = self.onGround;

        cachedRotationYaw = self.rotationYaw;
        cachedRotationPitch = self.rotationPitch;

        EventUpdate event = new EventUpdate.Pre(self.posX, self.posY, self.posZ, self.rotationYaw, self.rotationPitch, self.onGround);
        Arsenic.getInstance().getEventManager().post(event);
        if (event.isCancelled())
            return true;

        self.posX = event.getX();
        self.posY = event.getY();
        self.posZ = event.getZ();

        self.onGround = event.isOnGround();

        self.rotationYaw = event.getYaw();
        self.rotationPitch = event.getPitch();
        return false;
    }

    /** EntityPlayerSP.onUpdateWalkingPlayer RETURN. */
    public static void onUpdateWalkingPlayerReturn(EntityPlayerSP self) {
        self.posX = cachedX;
        self.posY = cachedY;
        self.posZ = cachedZ;

        self.onGround = cachedOnGround;

        self.rotationYaw = cachedRotationYaw;
        self.rotationPitch = cachedRotationPitch;

        Arsenic.getInstance().getEventManager()
                .post(new EventUpdate.Post(self.posX, self.posY, self.posZ, self.rotationYaw, self.rotationPitch, self.onGround));
    }

    /** EntityPlayerSP.onUpdate HEAD. */
    public static void onUpdateHead(EntityPlayerSP self) {
        Arsenic.getInstance().getEventManager().post(new EventTick());
        for (int i = 0; i < 3; i++) {
            if (Mouse.isButtonDown(i) && !mouseDownLastTick[i]) {
                mouseDownLastTick[i] = true;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Down(i));
            } else if (!Mouse.isButtonDown(i) && mouseDownLastTick[i]) {
                mouseDownLastTick[i] = false;
                Arsenic.getArsenic().getEventManager().post(new EventMouse.Up(i));
            }
        }
    }

    /** EntityPlayerSP.onUpdate RETURN. */
    public static void onUpdateReturn(EntityPlayerSP self) {
        Arsenic.getInstance().getEventManager().post(new EventTick.Post());
    }

    /** EntityPlayerSP.onLivingUpdate HEAD. */
    public static void onLivingUpdateHead(EntityPlayerSP self) {
        Arsenic.getInstance().getEventManager().post(new EventLiving());
    }

    /** EntityPlayerSP.swingItem HEAD. @return true to cancel */
    public static boolean swingItemHead(EntityPlayerSP self) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY)
            return false;

        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        return hitSelect != null && hitSelect.shouldBlock(mc.objectMouseOver.entityHit);
    }

    /** EntityLivingBase.jump HEAD. @return true to cancel */
    public static boolean jumpHead(EntityLivingBase self) {
        float upwards = GET_JUMP_UPWARDS_MOTION.invoke(self);
        final EventJump e = new EventJump(self.rotationYaw, upwards);
        Arsenic.getInstance().getEventManager().post(e);

        if (e.isCancelled())
            return false;

        self.motionY = e.getMotion();
        if (self.isPotionActive(Potion.jump)) {
            self.motionY += ((float) (self.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);
        }

        if (self.isSprinting()) {
            float f = e.getYaw() * 0.017453292F;
            self.motionX -= MathHelper.sin(f) * 0.2F;
            self.motionZ += MathHelper.cos(f) * 0.2F;
        }

        self.isAirBorne = true;
        return true;
    }

    /** EntityLivingBase.onLivingUpdate HEAD. */
    public static void livingUpdateHead(EntityLivingBase self) {
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(NoJumpDelay.class).isEnabled())
            JUMP_TICKS.setInt(self, 0);
    }

    /** PlayerControllerMP.attackEntity HEAD. @return true to cancel */
    public static boolean attackEntityHead(PlayerControllerMP self, EntityPlayer playerIn, Entity targetEntity) {
        HitSelect hitSelect = Arsenic.getArsenic().getModuleManager().getModuleByClass(HitSelect.class);
        return hitSelect != null && hitSelect.shouldBlock(targetEntity);
    }

    /** AbstractClientPlayer.getLocationCape HEAD. Null keeps vanilla. */
    public static ResourceLocation getLocationCape(AbstractClientPlayer self) {
        if (Minecraft.getMinecraft().thePlayer != self) return null;
        ModuleManager moduleManager = Arsenic.getInstance().getModuleManager();
        Cape cape = moduleManager.getModuleByClass(Cape.class);
        if (cape.isEnabled()) {
            CapeHandler capeHandler = CapeHandler.getInstance();
            if (capeHandler.hasCape())
                return capeHandler.getCapeLocation();
        }
        return null;
    }

    /** MovementInputFromOptions.updatePlayerMoveState RETURN. */
    public static void updatePlayerMoveStateReturn(MovementInputFromOptions self) {
        EventMovementInput event = new EventMovementInput(self.moveForward, self.moveStrafe, self.jump);
        Arsenic.getArsenic().getEventManager().post(event);
        if (event.isCancelled()) {
            self.moveStrafe = 0.0F;
            self.moveForward = 0.0F;
            return;
        }

        self.moveForward = event.getSpeed();
        self.moveStrafe = event.getStrafe();
        self.jump = event.isJumping();
    }

    /** World.spawnEntityInWorld HEAD. */
    public static void spawnEntityInWorldHead(World self, Entity entityIn) {
        if (entityIn instanceof EntityPlayer)
            Arsenic.getArsenic().getEventManager().post(new EventPlayerJoinWorld((EntityPlayer) entityIn, entityIn.getEntityWorld()));
    }

    /** EntityPlayer.attackTargetEntityWithCurrentItem HEAD: replaces the vanilla attack. @return true (always cancels) */
    public static boolean attackTargetEntityWithCurrentItem(EntityPlayer self, Entity target) {
        Arsenic.getInstance().getEventManager().post(new EventAttack(target));
        if (!Platform.isForge() || ForgeHooks.onPlayerAttackTarget(self, target)) {
            if (target.canAttackWithItem() && !target.hitByEntity(self)) {
                float f = (float) self.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
                float f1;
                if (target instanceof EntityLivingBase) {
                    f1 = EnchantmentHelper.func_152377_a(self.getHeldItem(), ((EntityLivingBase) target).getCreatureAttribute());
                } else {
                    f1 = EnchantmentHelper.func_152377_a(self.getHeldItem(), EnumCreatureAttribute.UNDEFINED);
                }

                int i = EnchantmentHelper.getKnockbackModifier(self);
                if (self.isSprinting()) {
                    ++i;
                }

                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = self.fallDistance > 0.0F && !self.onGround && !self.isOnLadder() && !self.isInWater()
                            && !self.isPotionActive(Potion.blindness) && self.ridingEntity == null
                            && target instanceof EntityLivingBase;
                    if (flag && f > 0.0F) {
                        f *= 1.5F;
                    }

                    f += f1;
                    boolean flag1 = false;
                    int j = EnchantmentHelper.getFireAspectModifier(self);
                    if (target instanceof EntityLivingBase && j > 0 && !target.isBurning()) {
                        flag1 = true;
                        target.setFire(1);
                    }

                    double d0 = target.motionX;
                    double d1 = target.motionY;
                    double d2 = target.motionZ;
                    boolean flag2 = target.attackEntityFrom(DamageSource.causePlayerDamage(self), f);
                    if (flag2) {
                        if (i > 0) {
                            target.addVelocity(
                                    -MathHelper.sin(self.rotationYaw * 3.1415927F / 180.0F) * (float) i * 0.5F, 0.1D,
                                    MathHelper.cos(self.rotationYaw * 3.1415927F / 180.0F) * (float) i * 0.5F);

                            self.motionX *= 0.6D;
                            self.motionZ *= 0.6D;
                            self.setSprinting(false);
                        }

                        if (target instanceof EntityPlayerMP && target.velocityChanged) {
                            ((EntityPlayerMP) target).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(target));
                            target.velocityChanged = false;
                            target.motionX = d0;
                            target.motionY = d1;
                            target.motionZ = d2;
                        }

                        if (flag) {
                            self.onCriticalHit(target);
                        }

                        if (f1 > 0.0F) {
                            self.onEnchantmentCritical(target);
                        }

                        if (f >= 18.0F) {
                            self.triggerAchievement(AchievementList.overkill);
                        }

                        self.setLastAttacker(target);
                        if (target instanceof EntityLivingBase) {
                            EnchantmentHelper.applyThornEnchantments((EntityLivingBase) target, self);
                        }

                        EnchantmentHelper.applyArthropodEnchantments(self, target);
                        ItemStack itemstack = self.getCurrentEquippedItem();
                        Entity entity = target;
                        if (target instanceof EntityDragonPart) {
                            IEntityMultiPart ientitymultipart = ((EntityDragonPart) target).entityDragonObj;
                            if (ientitymultipart instanceof EntityLivingBase) {
                                entity = (EntityLivingBase) ientitymultipart;
                            }
                        }

                        if (itemstack != null && entity instanceof EntityLivingBase) {
                            itemstack.hitEntity((EntityLivingBase) entity, self);
                            if (itemstack.stackSize <= 0) {
                                self.destroyCurrentEquippedItem();
                            }
                        }

                        if (target instanceof EntityLivingBase) {
                            self.addStat(StatList.damageDealtStat, Math.round(f * 10.0F));
                            if (j > 0) {
                                target.setFire(j * 4);
                            }
                        }

                        self.addExhaustion(0.3F);
                    } else if (flag1) {
                        target.extinguish();
                    }
                }
            }
        }
        return true;
    }
}
