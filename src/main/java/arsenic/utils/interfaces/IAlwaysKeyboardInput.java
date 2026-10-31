package arsenic.utils.interfaces;

public interface IAlwaysKeyboardInput {
    void setNotAlwaysRecieveInput();
    boolean recieveInput(int key);

    /** True while this component wants the middle and side mouse buttons as input too (a key bind being picked). */
    default boolean acceptsMouseButtons() {
        return false;
    }
}
