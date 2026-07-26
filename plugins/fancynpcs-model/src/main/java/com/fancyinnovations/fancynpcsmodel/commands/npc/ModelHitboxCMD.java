package com.fancyinnovations.fancynpcsmodel.commands.npc;

import com.fancyinnovations.fancynpcsmodel.fancynpcshook.CustomModelAttribute;
import com.fancyinnovations.fancynpcsmodel.utils.FancyContext;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

public class ModelHitboxCMD extends FancyContext {

    public static final ModelHitboxCMD INSTANCE = new ModelHitboxCMD();

    private static final double MAX_SIZE = 64.0;

    private ModelHitboxCMD() {
    }

    @Command("npc model_hitbox <npc> <width> <height>")
    @Permission("fancynpcsmodel.command.npc.custom_model")
    public void onSetHitbox(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @Argument("width") double width,
            final @Argument("height") double height
    ) {
        if (!CustomModelAttribute.hasAttribute(npc)) {
            translator.translate("commands.npc.play_animation.no_model_assigned")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        if (width <= 0 || height <= 0 || width > MAX_SIZE || height > MAX_SIZE) {
            translator.translate("commands.npc.model_hitbox.invalid")
                    .withPrefix()
                    .replace("max", String.valueOf((int) MAX_SIZE))
                    .send(sender);
            return;
        }

        NpcAttribute hitboxAttribute = FancyNpcsPlugin.get().getAttributeManager()
                .getAttributeByName(EntityType.PLAYER, CustomModelAttribute.HITBOX_ATTRIBUTE_NAME);

        String value = width + ";" + height;
        npc.getData().addAttribute(hitboxAttribute, value);
        hitboxAttribute.apply(npc, value);

        translator.translate("commands.npc.model_hitbox.applied")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("width", String.valueOf(width))
                .replace("height", String.valueOf(height))
                .send(sender);
    }

    @Command("npc model_hitbox <npc> reset")
    @Permission("fancynpcsmodel.command.npc.custom_model")
    public void onResetHitbox(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc
    ) {
        NpcAttribute hitboxAttribute = FancyNpcsPlugin.get().getAttributeManager()
                .getAttributeByName(EntityType.PLAYER, CustomModelAttribute.HITBOX_ATTRIBUTE_NAME);

        npc.getData().removeAttribute(hitboxAttribute);
        npc.updateForAll();

        translator.translate("commands.npc.model_hitbox.reset")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }
}
