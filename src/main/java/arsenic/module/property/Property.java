package arsenic.module.property;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.utils.interfaces.IContainable;

public abstract class Property<T> implements IContainable {

    protected T value;
    protected IVisible visible = () -> true;

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

    public boolean isVisible() {return visible.func();}

    public abstract PropertyComponent createComponent();

    @FunctionalInterface public interface IVisible {boolean func();}



    
}