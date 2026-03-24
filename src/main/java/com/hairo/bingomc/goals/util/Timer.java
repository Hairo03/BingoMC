package com.hairo.bingomc.goals.util;

public class Timer {
    private long startMillis = -1L;
    private long accumulatedMillis = 0L;
    private boolean running = false;
    private long limitMillis = 60000L;

    public void setLimitSeconds(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Time limit must be > 0");
        }
        this.limitMillis = seconds * 1000L;
    }

    public boolean hasLimit() {
        return limitMillis > 0;
    }

    public boolean isExpired() {
        return hasLimit() && getElapsedMillis() >= limitMillis;
    }

    public long getRemainingMillis() {
        if (!hasLimit()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, limitMillis - getElapsedMillis());
    }

    public long getRemainingSeconds() {
        return getRemainingMillis() / 1000L;
    }

    public long getLimitMillis() {
        return limitMillis;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        startMillis = System.currentTimeMillis();
    }

    public void stop() {
        if (!running) {
            return;
        }
        accumulatedMillis += System.currentTimeMillis() - startMillis;
        startMillis = -1L;
        running = false;
    }

    public void reset() {
        startMillis = -1L;
        accumulatedMillis = 0L;
        running = false;
    }

    public long getElapsedMillis() {
        if (!running) {
            return accumulatedMillis;
        }
        return accumulatedMillis + (System.currentTimeMillis() - startMillis);
    }

    public long getElapsedSeconds() {
        return getElapsedMillis() / 1000L;
    }

    public boolean isRunning() {
        return running;
    }
}
