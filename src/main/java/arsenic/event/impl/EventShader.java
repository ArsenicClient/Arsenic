package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventShader implements Event {
    private int iterations,offset;
    private boolean blur;
    public EventShader(int iterations,int offset,boolean blur){
        this.iterations = iterations;
        this.offset = offset;
        this.blur = blur;
    }
    public static class Bloom extends EventShader {
        public Bloom(int iterations,int offset) {
            super(iterations,offset,false);
        }
    }

    public static class Blur extends EventShader {
        public Blur(int iterations,int offset) {
            super(iterations,offset,true);
        }
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
}
