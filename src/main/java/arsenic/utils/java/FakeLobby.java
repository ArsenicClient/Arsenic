package arsenic.utils.java;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Client-side fake players for profiling: a crowd walking in circles around the player with armour, held items and
 * nametags, like a busy lobby. The server never knows about them; leaving the world drops them.
 */
public final class FakeLobby {

    private static final int FIRST_ID = -10_000;
    private static final FakeLobby INSTANCE = new FakeLobby();
    private static final Item[] HELD = { Items.diamond_sword, Items.iron_sword, Items.bow, Items.golden_apple, null };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<Bot> bots = new ArrayList<>();
    private boolean subscribed;

    private static final class Bot {
        final EntityOtherPlayerMP entity;
        final double radius, speed;
        double angle;

        Bot(EntityOtherPlayerMP entity, double radius, double speed, double angle) {
            this.entity = entity;
            this.radius = radius;
            this.speed = speed;
            this.angle = angle;
        }
    }

    private FakeLobby() {}

    /** Replaces any fake players with {@code count} new ones around the player. @return how many were added */
    public static int spawn(int count) {
        return INSTANCE.doSpawn(count);
    }

    public static int clear() {
        return INSTANCE.doClear();
    }

    private int doSpawn(int count) {
        doClear();
        if (mc.theWorld == null || mc.thePlayer == null)
            return 0;
        Random random = new Random(42);
        for (int i = 0; i < count; i++) {
            EntityOtherPlayerMP bot = new EntityOtherPlayerMP(mc.theWorld,
                    new GameProfile(UUID.randomUUID(), "Bot" + String.format("%02d", i)));
            Item held = HELD[i % HELD.length];
            if (held != null)
                bot.setCurrentItemOrArmor(0, new ItemStack(held));
            bot.setCurrentItemOrArmor(1, new ItemStack(i % 2 == 0 ? Items.iron_boots : Items.diamond_boots));
            bot.setCurrentItemOrArmor(2, new ItemStack(Items.iron_leggings));
            bot.setCurrentItemOrArmor(3, new ItemStack(i % 3 == 0 ? Items.diamond_chestplate : Items.iron_chestplate));
            bot.setCurrentItemOrArmor(4, new ItemStack(Items.iron_helmet));
            Bot b = new Bot(bot, 3 + random.nextDouble() * 22, (0.02 + random.nextDouble() * 0.05) * (random.nextBoolean() ? 1 : -1),
                    random.nextDouble() * Math.PI * 2);
            place(b, true);
            mc.theWorld.addEntityToWorld(FIRST_ID - i, bot);
            bots.add(b);
        }
        if (!subscribed) {
            Arsenic.getArsenic().getEventManager().subscribe(this);
            subscribed = true;
        }
        return bots.size();
    }

    private int doClear() {
        int removed = bots.size();
        if (mc.theWorld != null)
            for (Bot b : bots)
                mc.theWorld.removeEntityFromWorld(b.entity.getEntityId());
        bots.clear();
        if (subscribed) {
            Arsenic.getArsenic().getEventManager().unsubscribe(this);
            subscribed = false;
        }
        return removed;
    }

    private void place(Bot b, boolean snap) {
        double x = mc.thePlayer.posX + Math.cos(b.angle) * b.radius;
        double z = mc.thePlayer.posZ + Math.sin(b.angle) * b.radius;
        double y = mc.thePlayer.posY;
        // walking along the circle: face the tangent
        float yaw = (float) Math.toDegrees(b.angle) + (b.speed > 0 ? 180f : 0f);
        if (snap) {
            b.entity.setPositionAndRotation(x, y, z, yaw, 0);
        } else {
            b.entity.setPositionAndRotation2(x, y, z, yaw, 0, 3, false);
        }
        b.entity.rotationYawHead = yaw;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.theWorld == null || mc.thePlayer == null || bots.isEmpty() || bots.get(0).entity.worldObj != mc.theWorld) {
            bots.clear();
            return;
        }
        for (int i = 0; i < bots.size(); i++) {
            Bot b = bots.get(i);
            b.angle += b.speed;
            place(b, false);
            b.entity.setSprinting(true);
            if ((mc.thePlayer.ticksExisted + i) % 12 == 0)
                b.entity.swingItem();
        }
    };
}
