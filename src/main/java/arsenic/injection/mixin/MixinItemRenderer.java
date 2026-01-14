package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemRenderer.class, priority = 1111)
public abstract class MixinItemRenderer {
    @Final
    @Shadow
    private Minecraft mc;
    @Shadow
    private float equippedProgress;

    @Shadow
    protected abstract void transformFirstPersonItem(float equippedProgress, float swingProgress);

    @Shadow
    protected abstract void renderItemMap(AbstractClientPlayer clientPlayer, float pitch, float equipmentProgress, float swingProgress);

    @Shadow
    private float prevEquippedProgress;

    @Shadow
    private ItemStack itemToRender;

    @Shadow
    protected abstract void func_178101_a(float p_rotateArroundXAndY_1_, float p_rotateArroundXAndY_2_);

    @Shadow
    public abstract void renderItem(EntityLivingBase entityIn, ItemStack heldStack, ItemCameraTransforms.TransformType transform);

    private void doSwordBlockAnimation() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    public void renderItemInFirstPerson(float partialTicks, CallbackInfo ci) {
        try {
            float f = 1.0F - (this.prevEquippedProgress + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
            EntityPlayerSP player = this.mc.thePlayer;
            float swingProgress = player.getSwingProgress(partialTicks);
            float f2 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
            float f3 = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
            this.func_178101_a(f2, f3);
            this.func_178109_a(player);
            this.func_178110_a(player, partialTicks);
            GlStateManager.enableRescaleNormal();
            GlStateManager.pushMatrix();

            if (this.itemToRender != null) {
                if (this.itemToRender.getItem() instanceof ItemMap) {
                    this.renderItemMap(player, f2, f, swingProgress);
                } else if (player.getItemInUseCount() > 0) {
                    EnumAction action = this.itemToRender.getItemUseAction();
                    switch (action) {
                        case NONE:
                            this.transformFirstPersonItem(f, 0.0F);
                            break;
                        case EAT:
                        case DRINK:
                            this.func_178104_a(player, partialTicks);
                            this.transformFirstPersonItem(f, swingProgress);
                            break;
                        case BLOCK:
                            this.transformFirstPersonItem(0.0f, swingProgress);
                            GlStateManager.translate(0, 0.2, 0);
                            this.doSwordBlockAnimation();
                            break;
                        case BOW:
                            this.transformFirstPersonItem(f, swingProgress);
                            this.func_178098_a(partialTicks, player);
                    }
                } else {
                    this.func_178105_d(swingProgress);
                    this.transformFirstPersonItem(f, swingProgress);
                }

                this.renderItem(player, this.itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
            } else if (!player.isInvisible()) {
                //this.renderPlayerArms(player);
            }

            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        } catch (Exception e) {
            System.out.println("exception" + e.getMessage());
        }

        if (mc.thePlayer.inventory.getCurrentItem() != null) {
            ci.cancel();
        }
    }

    @Shadow
    protected abstract void func_178109_a(AbstractClientPlayer p_setLightMapFromPlayer_1_);

    @Shadow
    protected abstract void func_178110_a(EntityPlayerSP p_rotateWithPlayerRotations_1_, float p_rotateWithPlayerRotations_2_);

    @Shadow
    protected abstract void func_178104_a(AbstractClientPlayer p_performDrinking_1_, float p_performDrinking_2_);

    @Shadow
    protected abstract void func_178098_a(float p_doBowTransformations_1_, AbstractClientPlayer p_doBowTransformations_2_);

    @Shadow
    protected abstract void func_178105_d(float p_doItemUsedTransformations_1_);

    @Shadow
    protected abstract void renderPlayerArms(AbstractClientPlayer p_renderPlayerArm_1_);
}