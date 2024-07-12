package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventShader implements Event {
    private int iterations,offset;
    private final Type type;
    public EventShader(int iterations,int offset,Type type){
        this.iterations = iterations;
        this.offset = offset;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public enum Type {
        Bloom,
        Blur
    }
}
