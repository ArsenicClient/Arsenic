package arsenic.utils.functionalinterfaces;

@FunctionalInterface
public interface ITwoParamVoidFunction<T, E> {
    void function(T t, E e);
}
