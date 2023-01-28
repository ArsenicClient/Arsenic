package arsenic.module.property;

import arsenic.utils.interfaces.IContainable;

public abstract class Property<T> implements IContainable {

    protected T value;
    protected IVisible visible = () -> false;

    public Property(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public void setVisible(IVisible visible) {this.visible = visible;}

    public IVisible getVisibleInterface() {return visible;}

    @FunctionalInterface public interface IVisible {boolean func();}

    
}