package arsenic.utils.functionalinterfaces;

@FunctionalInterface
public interface IOneParamVoidFunction<E> {
    void doThing(E e);
}
