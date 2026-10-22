package arsenic.addon;

/** Supplies class bytes exactly as they exist at runtime. */
public interface ClassSource {

    /** @return the class file bytes for an internal name like "net/minecraft/client/Minecraft", or null. */
    byte[] getBytes(String internalName);
}
