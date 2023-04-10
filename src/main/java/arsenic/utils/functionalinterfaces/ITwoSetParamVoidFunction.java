package arsenic.utils.functionalinterfaces;

@FunctionalInterface
public interface ITwoSetParamVoidFunction<T, E> {
    void function(T t, E e, Object... s);
}
