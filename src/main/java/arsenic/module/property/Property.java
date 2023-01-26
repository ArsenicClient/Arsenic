package arsenic.module.property;

import arsenic.utils.interfaces.IContainable;

public abstract class Property<T> implements IContainable {

    protected T value;

    public Property(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
    
}