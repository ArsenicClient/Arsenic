package arsenic.inject;

import arsenic.lib.asm.ClassReader;
import arsenic.lib.asm.ClassWriter;
import arsenic.lib.asm.Opcodes;
import arsenic.lib.asm.Type;
import arsenic.lib.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Consumer;

import static arsenic.lib.asm.Opcodes.*;

/**
 * Applies the client's hooks to Minecraft classes that are already loaded, for the injected client. Mixins can only
 * change a class while it is being loaded, so every mixin injection point is repeated here as a plain bytecode edit
 * that calls the same static method in arsenic.runtime.hooks. Only method bodies change (no new fields, methods
 * or interfaces), which is all that class retransformation allows.
 *
 * Hook methods take the target's "this" (unless the target is static) followed by the target's arguments, except
 * redirects, which take the redirected call's receiver and arguments. Hooks are written with MCP names; {@link Names}
 * turns them into the names the running game uses (SRG on Forge, MCP on Lunar Client, obfuscated on vanilla), class
 * names and descriptors included.
 *
 * This class runs on the system class loader inside the game, so it must not touch Minecraft or Arsenic classes.
 */
public final class HookTransformer implements ClassFileTransformer {

    /** Turns MCP names into runtime names. Owners and descriptors passed in are MCP names. */
    public interface Names {
        String method(String owner, String name, String desc);

        String field(String owner, String name, String desc);

        /** Runtime internal name of a class. */
        String type(String internalName);

        /** A descriptor with its class names in runtime names. */
        String desc(String desc);
    }

    enum Kind {
        HEAD,            // void hook(self, args) at the start
        HEAD_CANCEL,     // boolean hook(self, args) at the start; true returns from the (void) target
        HEAD_RETURN,     // R hook(self, args) at the start; non-null is returned. For boolean targets: int, <0 continues
        ARG,             // T hook(self, args) at the start; the result replaces argument `index`
        RETURN,          // void hook(self, args) before every return
        TAIL,            // void hook(self, args) before the last return
        BEFORE_CALL,     // void hook(self, args) before a call
        AFTER_CALL,      // void hook(self, args) after a call
        MODIFY_RESULT,   // R hook(self, R) applied to the value a call returns
        REDIRECT,        // R hook(receiver, callArgs) replaces a call
        BEFORE_FIELD     // void hook(self, args) before a field access
    }

    static final class Hook {
        final Kind kind;
        final String owner, method, desc;
        final String hookOwner, hookName;
        String refOwner, refDeclOwner, refName, refDesc;
        boolean refStatic;
        // the same in the running game's names, filled in once every hook is registered
        String rOwner, rMethod, rDesc, rRefOwner, rRefName, rRefDesc, rHookDesc;
        int ordinal = -1;
        int index;

        Hook(Kind kind, String owner, String method, String desc, String hookOwner, String hookName) {
            this.kind = kind;
            this.owner = owner;
            this.method = method;
            this.desc = desc;
            this.hookOwner = hookOwner;
            this.hookName = hookName;
        }

        Hook ref(String owner, String name, String desc) {
            return ref(owner, owner, name, desc);
        }

        /** The call or field this hook is placed at. declOwner is the class that declares it, for the name lookup. */
        Hook ref(String owner, String declOwner, String name, String desc) {
            this.refOwner = owner;
            this.refDeclOwner = declOwner;
            this.refName = name;
            this.refDesc = desc;
            return this;
        }

        /** The redirected call is to a static method, so the hook has no receiver parameter. */
        Hook staticRef() {
            this.refStatic = true;
            return this;
        }

        Hook ordinal(int ordinal) {
            this.ordinal = ordinal;
            return this;
        }

        Hook index(int index) {
            this.index = index;
            return this;
        }

        @Override
        public String toString() {
            return owner.substring(owner.lastIndexOf('/') + 1) + "." + method + " " + kind
                    + (refName != null ? " " + refName + (ordinal >= 0 ? "#" + ordinal : "") : "") + " -> " + hookName;
        }
    }

    private static final String MC = "net/minecraft/";
    private static final String HOOKS = "arsenic/runtime/hooks/";
    private static final String MINECRAFT_HOOKS = HOOKS + "MinecraftHooks";
    private static final String PLAYER_HOOKS = HOOKS + "PlayerHooks";
    private static final String ENTITY_HOOKS = HOOKS + "EntityHooks";
    private static final String RENDER_HOOKS = HOOKS + "RenderHooks";
    private static final String GUI_HOOKS = HOOKS + "GuiHooks";
    private static final String MISC_HOOKS = HOOKS + "MiscHooks";

    private final Map<String, List<Hook>> hooks = new LinkedHashMap<>(); // by runtime class name
    private final Names names;
    private final boolean forge;
    private final ClassLoader gameLoader;
    private final Consumer<String> log;
    private final Set<String> applied = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> missed = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * @param forge      whether the game is Forge; some hooks sit in Forge's code there and in Minecraft's elsewhere
     * @param gameLoader the class loader of the Minecraft classes; classes of other loaders are left alone
     */
    public HookTransformer(Names names, boolean forge, ClassLoader gameLoader, Consumer<String> log) {
        this.names = names;
        this.forge = forge;
        this.gameLoader = gameLoader;
        this.log = log;
        List<Hook> all = new ArrayList<>();
        registerHooks(all);
        for (Hook hook : all) {
            hook.rOwner = names.type(hook.owner);
            hook.rMethod = names.method(hook.owner, hook.method, hook.desc);
            hook.rDesc = names.desc(hook.desc);
            if (hook.refName != null) {
                hook.rRefOwner = names.type(hook.refOwner);
                hook.rRefName = hook.kind == Kind.BEFORE_FIELD ? names.field(hook.refDeclOwner, hook.refName, hook.refDesc)
                        : names.method(hook.refDeclOwner, hook.refName, hook.refDesc);
                hook.rRefDesc = names.desc(hook.refDesc);
            }
            hook.rHookDesc = names.desc(hookDesc(hook));
            hooks.computeIfAbsent(hook.rOwner, k -> new ArrayList<>()).add(hook);
        }
    }

    private List<Hook> registering;

    private Hook add(Kind kind, String owner, String method, String desc, String hookOwner, String hookName) {
        Hook hook = new Hook(kind, MC + owner, method, desc, hookOwner, hookName);
        registering.add(hook);
        return hook;
    }

    // Mirrors the mixins in arsenic.injection.mixin, in the same order. SplashProgress and FMLHandshakeMessage are
    // left out: by the time the client is injected the splash screen and the mod list handshake are long over.
    private void registerHooks(List<Hook> into) {
        registering = into;
        String mc = "client/Minecraft";
        add(Kind.HEAD_RETURN, mc, "getFramebuffer", "()Lnet/minecraft/client/shader/Framebuffer;", MINECRAFT_HOOKS, "getFramebuffer");
        add(Kind.REDIRECT, mc, "runTick", "()V", MINECRAFT_HOOKS, "setKeyBindState")
                .ref(MC + "client/settings/KeyBinding", "setKeyBindState", "(IZ)V").staticRef();
        add(Kind.HEAD, mc, "runTick", "()V", MINECRAFT_HOOKS, "runTickHead");
        add(Kind.REDIRECT, mc, "runTick", "()V", MINECRAFT_HOOKS, "getEventKeyState")
                .ref("org/lwjgl/input/Keyboard", "getEventKeyState", "()Z").staticRef().ordinal(2);
        add(Kind.REDIRECT, mc, "runTick", "()V", MINECRAFT_HOOKS, "isPressed")
                .ref(MC + "client/settings/KeyBinding", "isPressed", "()Z");
        add(Kind.REDIRECT, mc, "runTick", "()V", MINECRAFT_HOOKS, "isKeyDown")
                .ref(MC + "client/settings/KeyBinding", "isKeyDown", "()Z");
        add(Kind.HEAD, mc, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", MINECRAFT_HOOKS, "displayGuiScreenHead");
        add(Kind.RETURN, mc, "displayGuiScreen", "(Lnet/minecraft/client/gui/GuiScreen;)V", MINECRAFT_HOOKS, "displayGuiScreenReturn");
        add(Kind.RETURN, mc, "rightClickMouse", "()V", MINECRAFT_HOOKS, "rightClickMouseReturn");
        add(Kind.HEAD, mc, "clickMouse", "()V", MINECRAFT_HOOKS, "clickMouseHead");
        add(Kind.BEFORE_CALL, mc, "clickMouse", "()V", MINECRAFT_HOOKS, "clickMouseBeforeSwing")
                .ref(MC + "client/entity/EntityPlayerSP", "swingItem", "()V");

        String sp = "client/entity/EntityPlayerSP";
        add(Kind.HEAD_CANCEL, sp, "onUpdateWalkingPlayer", "()V", PLAYER_HOOKS, "onUpdateWalkingPlayerHead");
        add(Kind.HEAD, sp, "onUpdate", "()V", PLAYER_HOOKS, "onUpdateHead");
        add(Kind.RETURN, sp, "onUpdate", "()V", PLAYER_HOOKS, "onUpdateReturn");
        add(Kind.HEAD, sp, "onLivingUpdate", "()V", PLAYER_HOOKS, "onLivingUpdateHead");
        add(Kind.HEAD_CANCEL, sp, "swingItem", "()V", PLAYER_HOOKS, "swingItemHead");
        add(Kind.RETURN, sp, "onUpdateWalkingPlayer", "()V", PLAYER_HOOKS, "onUpdateWalkingPlayerReturn");

        add(Kind.HEAD_CANCEL, "entity/player/EntityPlayer", "attackTargetEntityWithCurrentItem", "(Lnet/minecraft/entity/Entity;)V",
                PLAYER_HOOKS, "attackTargetEntityWithCurrentItem");

        String entity = "entity/Entity";
        add(Kind.HEAD_CANCEL, entity, "moveFlying", "(FFF)V", ENTITY_HOOKS, "moveFlyingHead");
        add(Kind.ARG, entity, "setAngles", "(FF)V", ENTITY_HOOKS, "setAnglesYaw").index(0);
        add(Kind.ARG, entity, "setAngles", "(FF)V", ENTITY_HOOKS, "setAnglesPitch").index(1);
        // the mixin modifies the second Vec3 rayTrace stores, which is the result of getLook
        add(Kind.MODIFY_RESULT, entity, "rayTrace", "(DF)Lnet/minecraft/util/MovingObjectPosition;", ENTITY_HOOKS, "rayTraceLook")
                .ref(MC + entity, "getLook", "(F)Lnet/minecraft/util/Vec3;");

        String living = "entity/EntityLivingBase";
        add(Kind.HEAD_CANCEL, living, "jump", "()V", PLAYER_HOOKS, "jumpHead");
        add(Kind.HEAD, living, "onLivingUpdate", "()V", PLAYER_HOOKS, "livingUpdateHead");

        String er = "client/renderer/EntityRenderer";
        add(Kind.BEFORE_FIELD, er, "renderWorldPass", "(IFJ)V", RENDER_HOOKS, "renderWorldPassHand")
                .ref(MC + er, "renderHand", "Z");
        add(Kind.HEAD, er, "renderWorld", "(FJ)V", RENDER_HOOKS, "renderWorldHead");
        add(Kind.HEAD, er, "updateCameraAndRender", "(FJ)V", RENDER_HOOKS, "updateCameraAndRenderHead");
        add(Kind.AFTER_CALL, er, "updateCameraAndRender", "(FJ)V", RENDER_HOOKS, "afterRenderGameOverlay")
                .ref(MC + "client/gui/GuiIngame", "renderGameOverlay", "(F)V");
        add(Kind.RETURN, er, "updateCameraAndRender", "(FJ)V", RENDER_HOOKS, "updateCameraAndRenderReturn");
        // Forge draws the open screen through ForgeHooksClient.drawScreen (hooked below); Minecraft calls it directly
        if (!forge)
            add(Kind.REDIRECT, er, "updateCameraAndRender", "(FJ)V", GUI_HOOKS, "drawScreen")
                    .ref(MC + "client/gui/GuiScreen", "drawScreen", "(IIF)V");
        add(Kind.HEAD_CANCEL, er, "getMouseOver", "(F)V", RENDER_HOOKS, "getMouseOver");
        add(Kind.HEAD_CANCEL, er, "hurtCameraEffect", "(F)V", RENDER_HOOKS, "hurtCameraEffectHead");
        // the mixin changes what getFOVModifier returns when useFOVSetting is true; these are the callers that pass true
        add(Kind.MODIFY_RESULT, er, "setupCameraTransform", "(FI)V", RENDER_HOOKS, "fovModifier")
                .ref(MC + er, "getFOVModifier", "(FZ)F");
        add(Kind.MODIFY_RESULT, er, "renderWorldPass", "(IFJ)V", RENDER_HOOKS, "fovModifier")
                .ref(MC + er, "getFOVModifier", "(FZ)F");
        add(Kind.MODIFY_RESULT, er, "renderCloudsCheck", "(Lnet/minecraft/client/renderer/RenderGlobal;FI)V", RENDER_HOOKS, "fovModifier")
                .ref(MC + er, "getFOVModifier", "(FZ)F");

        add(Kind.HEAD_CANCEL, "client/renderer/ItemRenderer", "renderItemInFirstPerson", "(F)V", RENDER_HOOKS, "renderItemInFirstPerson");

        String rle = "client/renderer/entity/RendererLivingEntity";
        String doRender = "(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V";
        String renderModel = "(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V";
        add(Kind.HEAD_RETURN, rle, "canRenderName", "(Lnet/minecraft/entity/EntityLivingBase;)Z", RENDER_HOOKS, "canRenderName");
        add(Kind.HEAD, rle, "doRender", doRender, RENDER_HOOKS, "doRenderHead");
        add(Kind.RETURN, rle, "doRender", doRender, RENDER_HOOKS, "doRenderReturn");
        add(Kind.HEAD, rle, "renderModel", renderModel, RENDER_HOOKS, "renderModelHead");
        add(Kind.RETURN, rle, "renderModel", renderModel, RENDER_HOOKS, "renderModelReturn");

        add(Kind.ARG, "client/gui/FontRenderer", "renderString", "(Ljava/lang/String;FFIZ)I", RENDER_HOOKS, "renderStringText").index(0);

        String ingame = "client/gui/GuiIngame";
        add(Kind.RETURN, ingame, "renderGameOverlay", "(F)V", RENDER_HOOKS, "renderGameOverlayReturn");
        add(Kind.HEAD_CANCEL, ingame, "renderScoreboard", "(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V",
                RENDER_HOOKS, "renderScoreboardHead");
        add(Kind.RETURN, ingame, "renderTooltip", "(Lnet/minecraft/client/gui/ScaledResolution;F)V", RENDER_HOOKS, "renderTooltipReturn");

        String sound = "client/audio/SoundManager";
        add(Kind.RETURN, sound, "playSound", "(Lnet/minecraft/client/audio/ISound;)V", MISC_HOOKS, "playSoundReturn");
        add(Kind.HEAD, sound, "stopSound", "(Lnet/minecraft/client/audio/ISound;)V", MISC_HOOKS, "stopSoundHead");
        add(Kind.HEAD, sound, "stopAllSounds", "()V", MISC_HOOKS, "stopAllSoundsHead");
        add(Kind.HEAD, sound, "pauseAllSounds", "()V", MISC_HOOKS, "pauseAllSoundsHead");
        add(Kind.HEAD, sound, "resumeAllSounds", "()V", MISC_HOOKS, "resumeAllSoundsHead");

        String chat = "client/gui/GuiChat";
        add(Kind.RETURN, chat, "keyTyped", "(CI)V", GUI_HOOKS, "chatKeyTypedReturn");
        add(Kind.HEAD_CANCEL, chat, "keyTyped", "(CI)V", GUI_HOOKS, "chatKeyTypedHead");
        add(Kind.RETURN, chat, "drawScreen", "(IIF)V", GUI_HOOKS, "chatDrawScreenReturn");

        String container = "client/gui/inventory/GuiContainer";
        add(Kind.TAIL, container, "initGui", "()V", GUI_HOOKS, "containerInitGuiTail");
        add(Kind.HEAD, container, "drawScreen", "(IIF)V", GUI_HOOKS, "containerDrawScreenHead");
        add(Kind.HEAD_CANCEL, container, "mouseClicked", "(III)V", GUI_HOOKS, "containerMouseClickedHead");

        String screen = "client/gui/GuiScreen";
        add(Kind.HEAD_CANCEL, screen, "drawWorldBackground", "(I)V", GUI_HOOKS, "drawWorldBackground");
        add(Kind.HEAD_CANCEL, screen, "drawBackground", "(I)V", GUI_HOOKS, "drawBackground");
        add(Kind.HEAD_CANCEL, screen, "sendChatMessage", "(Ljava/lang/String;Z)V", GUI_HOOKS, "sendChatMessage");

        String slot = "client/gui/GuiSlot";
        // added by Forge; without it lists keep Minecraft's dirt background
        if (forge)
            add(Kind.HEAD_CANCEL, slot, "drawContainerBackground", "(Lnet/minecraft/client/renderer/Tessellator;)V", GUI_HOOKS, "slotContainerBackground");
        add(Kind.HEAD_CANCEL, slot, "overlayBackground", "(IIII)V", GUI_HOOKS, "slotOverlayBackground");

        add(Kind.HEAD_CANCEL, "client/gui/GuiButton", "drawButton", "(Lnet/minecraft/client/Minecraft;II)V", GUI_HOOKS, "drawButton");

        String slider = "client/gui/GuiOptionSlider";
        add(Kind.REDIRECT, slider, "mouseDragged", "(Lnet/minecraft/client/Minecraft;II)V", GUI_HOOKS, "sliderKnob")
                .ref(MC + slider, MC + "client/gui/Gui", "drawTexturedModalRect", "(IIIIII)V").ordinal(0);
        add(Kind.REDIRECT, slider, "mouseDragged", "(Lnet/minecraft/client/Minecraft;II)V", GUI_HOOKS, "sliderKnobSecondHalf")
                .ref(MC + slider, MC + "client/gui/Gui", "drawTexturedModalRect", "(IIIIII)V").ordinal(1);

        add(Kind.HEAD_CANCEL, "client/gui/achievement/GuiAchievement", "updateAchievementWindow", "()V", GUI_HOOKS, "updateAchievementWindow");

        if (forge) {
            Hook forgeHead = new Hook(Kind.HEAD, "net/minecraftforge/client/ForgeHooksClient", "drawScreen",
                    "(Lnet/minecraft/client/gui/GuiScreen;IIF)V", GUI_HOOKS, "forgeDrawScreenHead");
            Hook forgeReturn = new Hook(Kind.RETURN, forgeHead.owner, forgeHead.method, forgeHead.desc, GUI_HOOKS, "forgeDrawScreenReturn");
            into.addAll(Arrays.asList(forgeHead, forgeReturn));
        }

        String net = "network/NetworkManager";
        String channelRead0 = "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V";
        add(Kind.HEAD_CANCEL, net, "sendPacket", "(Lnet/minecraft/network/Packet;)V", MISC_HOOKS, "sendPacketHead");
        add(Kind.HEAD_CANCEL, net, "channelRead0", channelRead0, MISC_HOOKS, "channelRead0Head");
        add(Kind.RETURN, net, "channelRead0", channelRead0, MISC_HOOKS, "channelRead0Return");

        add(Kind.HEAD_CANCEL, "client/multiplayer/PlayerControllerMP", "attackEntity",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V", PLAYER_HOOKS, "attackEntityHead");
        add(Kind.HEAD_RETURN, "client/entity/AbstractClientPlayer", "getLocationCape", "()Lnet/minecraft/util/ResourceLocation;",
                PLAYER_HOOKS, "getLocationCape");
        add(Kind.RETURN, "util/MovementInputFromOptions", "updatePlayerMoveState", "()V", PLAYER_HOOKS, "updatePlayerMoveStateReturn");
        add(Kind.HEAD, "world/World", "spawnEntityInWorld", "(Lnet/minecraft/entity/Entity;)Z", PLAYER_HOOKS, "spawnEntityInWorldHead");
    }

    /** Internal names of the classes this transformer changes. */
    public Set<String> targets() {
        return Collections.unmodifiableSet(hooks.keySet());
    }

    public Set<String> applied() {
        return applied;
    }

    public Set<String> missed() {
        return missed;
    }

    // ---- hook descriptors ----

    private static boolean isStatic(Hook hook) {
        return hook.owner.equals("net/minecraftforge/client/ForgeHooksClient");
    }

    /** (self, target args) with the given return type. */
    private static String selfDesc(Hook hook, Type ret) {
        List<Type> params = new ArrayList<>();
        if (!isStatic(hook))
            params.add(Type.getObjectType(hook.owner));
        params.addAll(Arrays.asList(Type.getArgumentTypes(hook.desc)));
        return Type.getMethodDescriptor(ret, params.toArray(new Type[0]));
    }

    /** The descriptor the hook method must have. */
    static String hookDesc(Hook hook) {
        Type targetRet = Type.getReturnType(hook.desc);
        switch (hook.kind) {
            case HEAD_CANCEL:
                return selfDesc(hook, Type.BOOLEAN_TYPE);
            case HEAD_RETURN:
                return selfDesc(hook, targetRet.getSort() == Type.BOOLEAN ? Type.INT_TYPE : targetRet);
            case ARG:
                return selfDesc(hook, Type.getArgumentTypes(hook.desc)[hook.index]);
            case MODIFY_RESULT: {
                Type ret = Type.getReturnType(hook.refDesc);
                return Type.getMethodDescriptor(ret, Type.getObjectType(hook.owner), ret);
            }
            case REDIRECT: {
                List<Type> params = new ArrayList<>();
                if (!hook.refStatic)
                    params.add(Type.getObjectType(hook.refOwner));
                params.addAll(Arrays.asList(Type.getArgumentTypes(hook.refDesc)));
                return Type.getMethodDescriptor(Type.getReturnType(hook.refDesc), params.toArray(new Type[0]));
            }
            default:
                return selfDesc(hook, Type.VOID_TYPE);
        }
    }

    /**
     * Checks that every hook method exists in the hook classes with the descriptor this transformer will call.
     * @return problems found, empty when everything matches
     */
    public List<String> verifyHooks(ClassLoader loader) {
        List<String> problems = new ArrayList<>();
        Map<String, Set<String>> declared = new HashMap<>();
        for (List<Hook> list : hooks.values()) {
            for (Hook hook : list) {
                Set<String> methods = declared.computeIfAbsent(hook.hookOwner, owner -> {
                    Set<String> set = new HashSet<>();
                    try {
                        Class<?> c = Class.forName(owner.replace('/', '.'), false, loader);
                        for (Method m : c.getDeclaredMethods())
                            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                                set.add(m.getName() + Type.getMethodDescriptor(m));
                    } catch (Throwable t) {
                        problems.add("cannot load " + owner + ": " + t);
                    }
                    return set;
                });
                if (!methods.contains(hook.hookName + hook.rHookDesc))
                    problems.add("missing hook " + hook.hookOwner + "." + hook.hookName + hook.rHookDesc);
            }
        }
        return problems;
    }

    // ---- transformation ----

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || loader != gameLoader || !hooks.containsKey(className))
            return null;
        try {
            return transform(className, classfileBuffer);
        } catch (Throwable t) {
            log.accept("Could not hook " + className + ": " + t);
            return null;
        }
    }

    public byte[] transform(String className, byte[] bytes) {
        List<Hook> classHooks = hooks.get(className);
        if (classHooks == null)
            return bytes;
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.EXPAND_FRAMES);
        boolean frames = (cn.version & 0xFFFF) >= Opcodes.V1_6;

        Map<MethodNode, List<Hook>> byMethod = new LinkedHashMap<>();
        for (Hook hook : classHooks) {
            MethodNode target = null;
            for (MethodNode mn : cn.methods)
                if (mn.name.equals(hook.rMethod) && mn.desc.equals(hook.rDesc))
                    target = mn;
            if (target == null) {
                missed.add(hook + " (no method " + hook.rMethod + hook.rDesc + ")");
                continue;
            }
            byMethod.computeIfAbsent(target, k -> new ArrayList<>()).add(hook);
        }

        for (Map.Entry<MethodNode, List<Hook>> e : byMethod.entrySet())
            apply(cn, e.getKey(), e.getValue(), frames);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);
        return writer.toByteArray();
    }

    private void apply(ClassNode cn, MethodNode mn, List<Hook> methodHooks, boolean frames) {
        InsnList insns = mn.instructions;
        // every match is found on the untouched method, so earlier edits cannot shift ordinals or add returns
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext())
            if (insn.getOpcode() >= IRETURN && insn.getOpcode() <= RETURN)
                returns.add(insn);

        Map<Hook, List<AbstractInsnNode>> refs = new HashMap<>();
        for (Hook hook : methodHooks) {
            if (hook.refName == null)
                continue;
            List<AbstractInsnNode> found = new ArrayList<>();
            boolean field = hook.kind == Kind.BEFORE_FIELD;
            String refName = hook.rRefName;
            int seen = 0;
            for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
                boolean match;
                if (field) {
                    match = insn instanceof FieldInsnNode && ((FieldInsnNode) insn).owner.equals(hook.rRefOwner)
                            && ((FieldInsnNode) insn).name.equals(refName) && ((FieldInsnNode) insn).desc.equals(hook.rRefDesc);
                } else {
                    match = insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(hook.rRefOwner)
                            && ((MethodInsnNode) insn).name.equals(refName) && ((MethodInsnNode) insn).desc.equals(hook.rRefDesc);
                }
                if (!match || !field && (insn.getOpcode() == INVOKESTATIC) != hook.refStatic)
                    continue;
                if (hook.ordinal < 0 || hook.ordinal == seen)
                    found.add(insn);
                seen++;
            }
            refs.put(hook, found);
        }

        InsnList head = new InsnList();
        for (Hook hook : methodHooks) {
            int count = 0;
            switch (hook.kind) {
                case HEAD:
                    loadSelfAndArgs(head, hook);
                    head.add(call(hook));
                    count = 1;
                    break;
                case ARG: {
                    loadSelfAndArgs(head, hook);
                    head.add(call(hook));
                    Type arg = Type.getArgumentTypes(hook.desc)[hook.index];
                    head.add(new VarInsnNode(arg.getOpcode(ISTORE), argSlot(hook, hook.index)));
                    count = 1;
                    break;
                }
                case HEAD_CANCEL: {
                    loadSelfAndArgs(head, hook);
                    head.add(call(hook));
                    LabelNode proceed = new LabelNode();
                    head.add(new JumpInsnNode(IFEQ, proceed));
                    head.add(new InsnNode(RETURN));
                    head.add(proceed);
                    if (frames)
                        head.add(frame(cn, mn, null));
                    count = 1;
                    break;
                }
                case HEAD_RETURN: {
                    loadSelfAndArgs(head, hook);
                    head.add(call(hook));
                    Type ret = Type.getReturnType(hook.desc);
                    LabelNode proceed = new LabelNode();
                    head.add(new InsnNode(DUP));
                    boolean bool = ret.getSort() == Type.BOOLEAN;
                    head.add(new JumpInsnNode(bool ? IFLT : IFNULL, proceed));
                    head.add(new InsnNode(bool ? IRETURN : ARETURN));
                    head.add(proceed);
                    if (frames)
                        head.add(frame(cn, mn, bool ? INTEGER : names.type(ret.getInternalName())));
                    head.add(new InsnNode(POP));
                    count = 1;
                    break;
                }
                case RETURN:
                    for (AbstractInsnNode ret : returns) {
                        InsnList list = new InsnList();
                        loadSelfAndArgs(list, hook);
                        list.add(call(hook));
                        insns.insertBefore(ret, list);
                    }
                    count = returns.size();
                    break;
                case TAIL:
                    if (!returns.isEmpty()) {
                        InsnList list = new InsnList();
                        loadSelfAndArgs(list, hook);
                        list.add(call(hook));
                        insns.insertBefore(returns.get(returns.size() - 1), list);
                        count = 1;
                    }
                    break;
                case BEFORE_CALL:
                case BEFORE_FIELD:
                    for (AbstractInsnNode at : refs.get(hook)) {
                        InsnList list = new InsnList();
                        loadSelfAndArgs(list, hook);
                        list.add(call(hook));
                        insns.insertBefore(at, list);
                    }
                    count = refs.get(hook).size();
                    break;
                case AFTER_CALL:
                    for (AbstractInsnNode at : refs.get(hook)) {
                        InsnList list = new InsnList();
                        loadSelfAndArgs(list, hook);
                        list.add(call(hook));
                        insns.insert(at, list);
                    }
                    count = refs.get(hook).size();
                    break;
                case MODIFY_RESULT:
                    // stack after the call: [result] -> [self, result] -> hook -> [result']
                    for (AbstractInsnNode at : refs.get(hook)) {
                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(ALOAD, 0));
                        list.add(new InsnNode(SWAP));
                        list.add(call(hook));
                        insns.insert(at, list);
                    }
                    count = refs.get(hook).size();
                    break;
                case REDIRECT:
                    for (AbstractInsnNode at : refs.get(hook))
                        insns.set(at, call(hook));
                    count = refs.get(hook).size();
                    break;
            }
            (count > 0 ? applied : missed).add(hook.toString() + (count > 1 ? " x" + count : ""));
        }

        if (head.size() > 0) {
            // a frame of our own right before an existing one would put two frames at one offset
            AbstractInsnNode first = insns.getFirst();
            while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode))
                first = first.getNext();
            if (first instanceof FrameNode && head.getLast() instanceof FrameNode)
                head.remove(head.getLast());
            insns.insert(head);
        }
    }

    private MethodInsnNode call(Hook hook) {
        return new MethodInsnNode(INVOKESTATIC, hook.hookOwner, hook.hookName, hook.rHookDesc, false);
    }

    private static int argSlot(Hook hook, int index) {
        int slot = isStatic(hook) ? 0 : 1;
        Type[] args = Type.getArgumentTypes(hook.desc);
        for (int i = 0; i < index; i++)
            slot += args[i].getSize();
        return slot;
    }

    private static void loadSelfAndArgs(InsnList list, Hook hook) {
        int slot = 0;
        if (!isStatic(hook))
            list.add(new VarInsnNode(ALOAD, slot++));
        for (Type arg : Type.getArgumentTypes(hook.desc)) {
            list.add(new VarInsnNode(arg.getOpcode(ILOAD), slot));
            slot += arg.getSize();
        }
    }

    /** The frame at the start of the method (its arguments as locals), with an optional single stack value. */
    private static FrameNode frame(ClassNode cn, MethodNode mn, Object stackTop) {
        List<Object> locals = new ArrayList<>();
        if ((mn.access & ACC_STATIC) == 0)
            locals.add(cn.name);
        for (Type arg : Type.getArgumentTypes(mn.desc)) {
            switch (arg.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT:
                    locals.add(INTEGER);
                    break;
                case Type.FLOAT:
                    locals.add(FLOAT);
                    break;
                case Type.LONG:
                    locals.add(LONG);
                    break;
                case Type.DOUBLE:
                    locals.add(DOUBLE);
                    break;
                case Type.ARRAY:
                    locals.add(arg.getDescriptor());
                    break;
                default:
                    locals.add(arg.getInternalName());
            }
        }
        Object[] stack = stackTop == null ? new Object[0] : new Object[]{stackTop};
        return new FrameNode(F_NEW, locals.size(), locals.toArray(), stack.length, stack);
    }
}
