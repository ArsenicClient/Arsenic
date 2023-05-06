package arsenic.utils.functionalinterfaces;

@FunctionalInterface
public interface ITwoParamFunction<T> {

    T function(T t, T e);
}
