package arsenic.utils.timer;

public class Timer {
    private long startTime;
    private long coolDownTime;
    private boolean finishChecked;

    public void start() {
        startTime = System.currentTimeMillis();
        finishChecked = false;
    }

    public boolean hasFinished() {
        return System.currentTimeMillis() >= (startTime + coolDownTime);
    }

    public boolean firstFinish() {
        if (hasFinished()) {
            finishChecked = true;
            return true;
        }
        return false;
    }

    public void setCooldown(long coolDownTime) {
        this.coolDownTime = coolDownTime;
    }

    public long getCooldownTime() {
        return coolDownTime;
    }

    public long getElapsedTime() {
        long et = System.currentTimeMillis() - startTime;
        return et > coolDownTime ? coolDownTime : et;
    }

    public long getTimeLeft() {
        long tl = coolDownTime - (System.currentTimeMillis() - startTime);
        return tl < 0 ? 0 : tl;
    }
}