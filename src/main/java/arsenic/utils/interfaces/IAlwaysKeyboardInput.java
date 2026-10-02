package arsenic.utils.interfaces;

public interface IAlwaysKeyboardInput {
    void setNotAlwaysRecieveInput();

    /** A key press; {@code key} is an SDL scancode (see {@code InputConstants}). */
    boolean recieveInput(int key);

    /** A typed character. Text arrives separately from key presses on modern Minecraft. */
    default boolean recieveChar(char c) {
        return false;
    }
}
