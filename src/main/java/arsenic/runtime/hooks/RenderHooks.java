package arsenic.runtime.hooks;

import arsenic.runtime.Platform;
import arsenic.event.impl.EventFov;
import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderThirdPerson;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.runtime.Access;
import arsenic.main.Arsenic;
import arsenic.module.impl.ghost.Reach;
import arsenic.module.impl.player.NameHider;
import arsenic.module.impl.visual.HUD;
import arsenic.module.impl.visual.Nametags;
import arsenic.module.impl.visual.NoHurtCam;
import arsenic.module.impl.visual.PostProcessing;
import arsenic.module.impl.visual.RotationView;
import arsenic.module.impl.visual.custommainmenu.ScreenTransition;
import arsenic.utils.render.ChamsRenderer;
import arsenic.utils.render.capture.RenderTargets;
import arsenic.utils.render.capture.SilentView;
import com.google.common.base.Predicates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.*;

import java.util.List;
import java.util.Objects;

/** Rendering hooks: EntityRenderer, ItemRenderer, RendererLivingEntity, FontRenderer and GuiIngame. */
public final class RenderHooks {

    private static final Access.FieldRef POINTED_ENTITY = Access.field(EntityRenderer.class, "pointedEntity");

    private static final Access.FieldRef EQUIPPED_PROGRESS = Access.field(ItemRenderer.class, "equippedProgress");
    private static final Access.FieldRef PREV_EQUIPPED_PROGRESS = Access.field(ItemRenderer.class, "prevEquippedProgress");
    private static final Access.FieldRef ITEM_TO_RENDER = Access.field(ItemRenderer.class, "itemToRender");
    private static final Access.MethodRef ROTATE_AROUND_X_AND_Y = Access.method(ItemRenderer.class, "func_178101_a", float.class, float.class);
    private static final Access.MethodRef SET_LIGHT_MAP_FROM_PLAYER = Access.method(ItemRenderer.class, "func_178109_a", AbstractClientPlayer.class);
    private static final Access.MethodRef ROTATE_WITH_PLAYER_ROTATIONS = Access.method(ItemRenderer.class, "func_178110_a", EntityPlayerSP.class, float.class);
    private static final Access.MethodRef RENDER_ITEM_MAP = Access.method(ItemRenderer.class, "renderItemMap", AbstractClientPlayer.class, float.class, float.class, float.class);
    private static final Access.MethodRef TRANSFORM_FIRST_PERSON_ITEM = Access.method(ItemRenderer.class, "transformFirstPersonItem", float.class, float.class);
    private static final Access.MethodRef PERFORM_DRINKING = Access.method(ItemRenderer.class, "func_178104_a", AbstractClientPlayer.class, float.class);
    private static final Access.MethodRef DO_BOW_TRANSFORMATIONS = Access.method(ItemRenderer.class, "func_178098_a", float.class, AbstractClientPlayer.class);
    private static final Access.MethodRef DO_ITEM_USED_TRANSFORMATIONS = Access.method(ItemRenderer.class, "func_178105_d", float.class);

    // third person rotation swap of the local player, undone at RETURN
    private static EventRenderThirdPerson thirdPersonEvent;
    private static float cYawH, cPYawH, cYawO, cPYawO, cPitch, cPPitch;

    private RenderHooks() {}

    // ---- EntityRenderer ----

    /** renderWorldPass, before the hand is drawn. */
    public static void renderWorldPassHand(EntityRenderer self, int pass, float partialTicks, long finishTimeNano) {
        if (SilentView.isRendering())
            return;
        boolean redirected = RenderTargets.beginVisuals();
        try {
            Arsenic.getArsenic().getEventManager().getBus().post(new EventRenderWorldLast(Minecraft.getMinecraft().renderGlobal, partialTicks));
        } finally {
            RenderTargets.endVisuals(redirected);
        }
    }

    /** renderWorld HEAD. */
    public static void renderWorldHead(EntityRenderer self, float partialTicks, long finishTimeNano) {
        RotationView rotationView = Arsenic.getArsenic().getModuleManager().getModuleByClass(RotationView.class);
        if ((rotationView.isEnabled() && rotationView.wantsFrame()) || RenderTargets.recordsSilentView())
            SilentView.render(partialTicks, finishTimeNano);
    }

    /** updateCameraAndRender HEAD. */
    public static void updateCameraAndRenderHead(EntityRenderer self, float partialTicks, long nanoTime) {
        RenderTargets.onFrameStart();
    }

    /** updateCameraAndRender, after GuiIngame.renderGameOverlay. */
    public static void afterRenderGameOverlay(EntityRenderer self, float partialTicks, long nanoTime) {
        RenderTargets.onFrameEnd(partialTicks, true);
    }

    /** updateCameraAndRender RETURN: frames with no HUD pass (F1 or no world). */
    public static void updateCameraAndRenderReturn(EntityRenderer self, float partialTicks, long nanoTime) {
        RenderTargets.onFrameEnd(partialTicks, false);
    }

    /** hurtCameraEffect HEAD. @return true to cancel */
    public static boolean hurtCameraEffectHead(EntityRenderer self, float partialTicks) {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(NoHurtCam.class).isEnabled();
    }

    /**
     * The camera field of view (getFOVModifier with useFOVSetting true): addons can change it through EventFov
     * (CustomFov, Zoom). Not applied while SilentView renders its own pass. @return the field of view to use
     */
    public static float fovModifier(EntityRenderer self, float fov) {
        if (SilentView.isRendering())
            return fov;
        EventFov event = new EventFov(fov);
        Arsenic.getArsenic().getEventManager().getBus().post(event);
        return event.isModified() ? event.getFov() : fov;
    }

    /** getMouseOver HEAD: replaces the vanilla method. @return true (always cancels) */
    public static boolean getMouseOver(EntityRenderer self, float partialTicks) {
        // the silent pass runs the whole renderWorld; keep the crosshair target from the real rotation
        if (SilentView.isRendering())
            return true;
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();
        if (entity != null && mc.theWorld != null) {
            Reach reachMod = Arsenic.getArsenic().getModuleManager().getModuleByClass(Reach.class);
            mc.mcProfiler.startSection("pick");
            mc.pointedEntity = null;
            double d0 = mc.playerController.getBlockReachDistance();
            mc.objectMouseOver = entity.rayTrace(Math.max(d0, reachMod.getReach()), partialTicks);
            double d1 = d0;
            Vec3 vec3 = entity.getPositionEyes(partialTicks);
            boolean flag = false;
            if (mc.playerController.extendedReach()) {
                d0 = 6.0D;
                d1 = 6.0D;
            } else if (d0 > reachMod.getReach()) {
                flag = true;
            }

            if (mc.objectMouseOver != null) {
                d1 = mc.objectMouseOver.hitVec.distanceTo(vec3);
            }

            Vec3 vec31 = getLook(entity, partialTicks);
            Vec3 vec32 = vec3.addVector(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0);
            Entity pointedEntity = null;
            Vec3 vec33 = null;
            float f = 1.0F;
            List<Entity> list = mc.theWorld.getEntitiesInAABBexcluding(entity, entity.getEntityBoundingBox().addCoord(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0).expand(f, f, f), Predicates.and(EntitySelectors.NOT_SPECTATING, p_apply_1_ -> p_apply_1_ != null && p_apply_1_.canBeCollidedWith()));
            double d2 = d1;

            for (Entity entity1 : list) {
                float f1 = entity1.getCollisionBorderSize();
                AxisAlignedBB axisalignedbb = entity1.getEntityBoundingBox().expand(f1, f1, f1);
                MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32);
                if (axisalignedbb.isVecInside(vec3)) {
                    if (d2 >= 0.0D) {
                        pointedEntity = entity1;
                        vec33 = movingobjectposition == null ? vec3 : movingobjectposition.hitVec;
                        d2 = 0.0D;
                    }
                } else if (movingobjectposition != null) {
                    double d3 = vec3.distanceTo(movingobjectposition.hitVec);
                    if (d3 < d2 || d2 == 0.0D) {
                        // canRiderInteract is Forge's; Minecraft never lets the rider interact
                        if (entity1 == entity.ridingEntity && !(Platform.isForge() && entity.canRiderInteract())) {
                            if (d2 == 0.0D) {
                                pointedEntity = entity1;
                                vec33 = movingobjectposition.hitVec;
                            }
                        } else {
                            pointedEntity = entity1;
                            vec33 = movingobjectposition.hitVec;
                            d2 = d3;
                        }
                    }
                }
            }

            if (pointedEntity != null && flag && vec3.distanceTo(vec33) > (reachMod.getReach())) {
                pointedEntity = null;
                mc.objectMouseOver = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, Objects.requireNonNull(vec33), null, new BlockPos(vec33));
            }

            if (pointedEntity != null && (d2 < d1 || mc.objectMouseOver == null)) {
                mc.objectMouseOver = new MovingObjectPosition(pointedEntity, vec33);
                if (pointedEntity instanceof EntityLivingBase || pointedEntity instanceof EntityItemFrame) {
                    mc.pointedEntity = pointedEntity;
                }
            }
            POINTED_ENTITY.set(self, pointedEntity);
            mc.mcProfiler.endSection();
        }
        return true;
    }

    private static Vec3 getLook(Entity entity, float partialTicks) {
        EventLook eventLook = new EventLook(entity.rotationYaw, entity.rotationPitch);
        Arsenic.getArsenic().getEventManager().post(eventLook);
        if (!eventLook.hasBeenModified())
            return entity.getLook(partialTicks);
        return EntityHooks.vectorForRotation(eventLook.getPitch(), eventLook.getYaw());
    }

    // ---- ItemRenderer ----

    private static void doSwordBlockAnimation() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    /** ItemRenderer.renderItemInFirstPerson HEAD. @return true to cancel */
    public static boolean renderItemInFirstPerson(ItemRenderer self, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            float prevEquipped = PREV_EQUIPPED_PROGRESS.getFloat(self);
            float f = 1.0F - (prevEquipped + (EQUIPPED_PROGRESS.getFloat(self) - prevEquipped) * partialTicks);
            EntityPlayerSP player = mc.thePlayer;
            float swingProgress = player.getSwingProgress(partialTicks);
            float f2 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
            float f3 = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
            ROTATE_AROUND_X_AND_Y.invoke(self, f2, f3);
            SET_LIGHT_MAP_FROM_PLAYER.invoke(self, player);
            ROTATE_WITH_PLAYER_ROTATIONS.invoke(self, player, partialTicks);
            GlStateManager.enableRescaleNormal();
            GlStateManager.pushMatrix();

            ItemStack itemToRender = ITEM_TO_RENDER.get(self);
            if (itemToRender != null) {
                if (itemToRender.getItem() instanceof ItemMap) {
                    RENDER_ITEM_MAP.invoke(self, player, f2, f, swingProgress);
                } else if (player.getItemInUseCount() > 0) {
                    EnumAction action = itemToRender.getItemUseAction();
                    switch (action) {
                        case NONE:
                            TRANSFORM_FIRST_PERSON_ITEM.invoke(self, f, 0.0F);
                            break;
                        case EAT:
                        case DRINK:
                            PERFORM_DRINKING.invoke(self, player, partialTicks);
                            TRANSFORM_FIRST_PERSON_ITEM.invoke(self, f, swingProgress);
                            break;
                        case BLOCK:
                            TRANSFORM_FIRST_PERSON_ITEM.invoke(self, 0.0f, swingProgress);
                            GlStateManager.translate(0, 0.2, 0);
                            doSwordBlockAnimation();
                            break;
                        case BOW:
                            TRANSFORM_FIRST_PERSON_ITEM.invoke(self, f, swingProgress);
                            DO_BOW_TRANSFORMATIONS.invoke(self, partialTicks, player);
                    }
                } else {
                    DO_ITEM_USED_TRANSFORMATIONS.invoke(self, swingProgress);
                    TRANSFORM_FIRST_PERSON_ITEM.invoke(self, f, swingProgress);
                }

                self.renderItem(player, itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
            }

            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        } catch (Exception e) {
            System.out.println("exception" + e.getMessage());
        }

        return mc.thePlayer.inventory.getCurrentItem() != null;
    }

    // ---- RendererLivingEntity ----

    /** canRenderName HEAD. @return 0 to return false, -1 to keep vanilla */
    public static int canRenderName(RendererLivingEntity<?> self, EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer) || entity == Minecraft.getMinecraft().thePlayer)
            return -1;
        Nametags nametags = Arsenic.getArsenic().getModuleManager().getModuleByClass(Nametags.class);
        return nametags != null && nametags.isEnabled() ? 0 : -1;
    }

    /** doRender HEAD. */
    public static void doRenderHead(RendererLivingEntity<?> self, EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
        ChamsRenderer.pre(entity);
        if (entity != Minecraft.getMinecraft().thePlayer)
            return;
        thirdPersonEvent = new EventRenderThirdPerson(entity.rotationYaw, entity.rotationPitch, entity.prevRotationYaw, entity.prevRotationPitch);
        Arsenic.getArsenic().getEventManager().post(thirdPersonEvent);
        if (!thirdPersonEvent.getAccepted())
            return;
        cYawH = entity.rotationYawHead;
        cPYawH = entity.prevRotationYawHead;
        cYawO = entity.renderYawOffset;
        cPYawO = entity.prevRenderYawOffset;
        cPitch = entity.rotationPitch;
        cPPitch = entity.prevRotationPitch;
        entity.rotationYawHead = thirdPersonEvent.getYaw();
        entity.prevRotationYawHead = thirdPersonEvent.getPrevYaw();
        entity.renderYawOffset = thirdPersonEvent.getYaw();
        entity.prevRenderYawOffset = thirdPersonEvent.getPrevYaw();
        entity.rotationPitch = thirdPersonEvent.getPitch();
        entity.prevRotationPitch = thirdPersonEvent.getPrevPitch();
    }

    /** doRender RETURN. */
    public static void doRenderReturn(RendererLivingEntity<?> self, EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
        ChamsRenderer.post(entity);
        if (entity != Minecraft.getMinecraft().thePlayer || thirdPersonEvent == null || !thirdPersonEvent.getAccepted())
            return;
        entity.rotationYawHead = cYawH;
        entity.prevRotationYawHead = cPYawH;
        entity.renderYawOffset = cYawO;
        entity.prevRenderYawOffset = cPYawO;
        entity.rotationPitch = cPitch;
        entity.prevRotationPitch = cPPitch;
    }

    /** renderModel HEAD. */
    public static void renderModelHead(RendererLivingEntity<?> self, EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ChamsRenderer.beginModel(entity);
    }

    /** renderModel RETURN. */
    public static void renderModelReturn(RendererLivingEntity<?> self, EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                         float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ChamsRenderer.endModel();
    }

    // ---- FontRenderer ----

    /** FontRenderer.renderString HEAD: replaces the text argument. */
    public static String renderStringText(FontRenderer self, String text, float x, float y, int color, boolean dropShadow) {
        return NameHider.format(text);
    }

    // ---- GuiIngame ----

    /** renderGameOverlay RETURN. */
    public static void renderGameOverlayReturn(GuiIngame self, float partialTicks) {
        if (SilentView.isRenderingHud())
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            ScaledResolution sr = new ScaledResolution(mc);
            ScreenTransition.drawOverlay(sr.getScaledWidth(), sr.getScaledHeight());
        }
    }

    /** renderScoreboard HEAD. @return true to cancel */
    public static boolean renderScoreboardHead(GuiIngame self, ScoreObjective objective, ScaledResolution sr) {
        if (SilentView.isRenderingHud())
            return false;
        HUD hud = Arsenic.getArsenic().getModuleManager().getModuleByClass(HUD.class);
        return hud.replacesVanillaScoreboard();
    }

    /** renderTooltip RETURN. */
    public static void renderTooltipReturn(GuiIngame self, ScaledResolution sr, float partialTicks) {
        // the silent recording only gets the vanilla HUD
        if (SilentView.isRenderingHud())
            return;
        boolean redirected = RenderTargets.beginVisuals();
        try {
            if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
                PostProcessing postProcessing = Arsenic.getArsenic().getModuleManager().getModuleByClass(PostProcessing.class);
                if (postProcessing.isEnabled()) {
                    postProcessing.blurScreen();
                }
            }
            Arsenic.getInstance().getEventManager().post(new EventRender2D(partialTicks, sr));
        } finally {
            RenderTargets.endVisuals(redirected);
        }
    }
}
