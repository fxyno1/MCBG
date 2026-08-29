package edu.mc.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an MCBG match starts and replay recording may begin.
 */
public final class GameStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final String gameName;

    public GameStartEvent(String worldName, String gameName) {
        this.worldName = worldName;
        this.gameName = gameName;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getGameName() {
        return gameName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
