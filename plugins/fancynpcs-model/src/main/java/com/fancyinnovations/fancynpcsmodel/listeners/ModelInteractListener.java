package com.fancyinnovations.fancynpcsmodel.listeners;

import com.fancyinnovations.fancynpcsmodel.fancynpcshook.CustomModelAttribute;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModelInteractListener implements Listener {

    private static final long DEBOUNCE_MILLIS = 120;

    private final Map<String, Long> lastInteractions = new ConcurrentHashMap<>();

    @EventHandler
    public void onModelInteract(BaseEntityInteractEvent event) {
        ActionTrigger trigger = switch (event.getAction()) {
            case ATTACK -> ActionTrigger.LEFT_CLICK;
            case INTERACT, INTERACT_ON -> ActionTrigger.RIGHT_CLICK;
        };

        Npc npc = CustomModelAttribute.getNpcByEntity(event.getBaseEntity().getUUID());
        if (npc == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String key = player.getUniqueId() + ":" + npc.getData().getId() + ":" + trigger;
        long now = System.currentTimeMillis();
        Long last = lastInteractions.get(key);
        if (last != null && now - last < DEBOUNCE_MILLIS) {
            return;
        }
        lastInteractions.put(key, now);

        npc.interact(player, trigger);
    }
}
