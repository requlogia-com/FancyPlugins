package com.fancyinnovations.fancynpcsmodel.listeners;

import com.fancyinnovations.fancynpcsmodel.fancynpcshook.CustomModelAttribute;
import com.ticxo.modelengine.api.events.AnimationEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ModelAnimationListener implements Listener {

    @EventHandler
    public void onAnimationEnd(AnimationEndEvent event) {
        CustomModelAttribute.handleAnimationEnd(event.getModel(), event.getProperty());
    }
}
