package com.hairo.bingomc.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimerExpiredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final long elapsedMillis;

    public TimerExpiredEvent(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public long getElapsedSeconds() {
        return elapsedMillis / 1000L;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
