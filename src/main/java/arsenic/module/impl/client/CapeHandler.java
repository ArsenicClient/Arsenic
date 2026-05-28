package arsenic.module.impl.client;

import net.minecraft.util.ResourceLocation;

public class CapeHandler {

    private static CapeHandler instance;
    private static final ResourceLocation CAPE = new ResourceLocation("arsenic", "cape/default.png");

    public static CapeHandler getInstance() {
        if (instance == null) {
            instance = new CapeHandler();
        }
        return instance;
    }

    public void init() {
    }

    public ResourceLocation getCapeLocation() {
        return CAPE;
    }

    public boolean hasCape() {
        return true;
    }
}
