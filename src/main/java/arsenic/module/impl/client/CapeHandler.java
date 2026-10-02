package arsenic.module.impl.client;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

public class CapeHandler {

    private static CapeHandler instance;
    private static final Identifier CAPE_ID = Identifier.fromNamespaceAndPath("arsenic", "cape/default");
    private static final ClientAsset.Texture CAPE = new ClientAsset.ResourceTexture(
            CAPE_ID, Identifier.fromNamespaceAndPath("arsenic", "cape/default.png"));

    public static CapeHandler getInstance() {
        if (instance == null) {
            instance = new CapeHandler();
        }
        return instance;
    }

    public void init() {
    }

    public ClientAsset.Texture getCape() {
        return CAPE;
    }

    public boolean hasCape() {
        return true;
    }
}
