package arsenic.utils.java;

public abstract class UtilityClass {

    protected UtilityClass() {
        throw new RuntimeException("Instantiation of Utility class " + this.getClass().getSimpleName());
    }

}
