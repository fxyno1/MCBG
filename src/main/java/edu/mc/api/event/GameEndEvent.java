package edu.mc.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an MCBG match has finished and replay recording may be saved.
 */
public final class GameEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final int gameTimeSeconds;

    public GameEndEvent(String worldName, int gameTimeSeconds) {
        this.worldName = worldName;
        this.gameTimeSeconds = gameTimeSeconds;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getGameTimeSeconds() {
        return gameTimeSeconds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
