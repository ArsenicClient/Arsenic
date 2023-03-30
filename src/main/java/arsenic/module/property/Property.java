package arsenic.module.property;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.utils.functionalinterfaces.INoParamFunction;
import arsenic.utils.interfaces.IContainable;

public abstract class Property<T> implements IContainable {

    protected T value;
    protected INoParamFunction<Boolean> visible = () -> true;

    protected Property(T value) {
        this.value = value;
    }

    public T getValue() { return value; }

    public void setValue(T value) { this.value = value; }

    public void setVisible(INoParamFunction<Boolean> visible) { this.visible = visible; }

    public boolean isVisible() { return visible.getValue(); }

    public abstract PropertyComponent<?> createComponent();

    public String getName() {
        return value.toString();
    }

}