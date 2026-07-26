package com.fancyinnovations.fancynpcsmodel.commands.npc;

import com.fancyinnovations.fancynpcsmodel.fancynpcshook.CustomModelAttribute;
import com.fancyinnovations.fancynpcsmodel.utils.FancyContext;
import com.ticxo.modelengine.api.model.ActiveModel;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IdleAnimationCMD extends FancyContext {

    public static final IdleAnimationCMD INSTANCE = new IdleAnimationCMD();

    private IdleAnimationCMD() {
    }

    @Command("npc idle_animation <npc> <animation>")
    @Permission("fancynpcsmodel.command.npc.custom_model")
    public void onIdleAnimation(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Argument(suggestions = "IdleAnimationCMD/animation") String animation
    ) {
        if (!CustomModelAttribute.hasAttribute(npc)) {
            translator.translate("commands.npc.play_animation.no_model_assigned")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        NpcAttribute idleAttribute = FancyNpcsPlugin.get().getAttributeManager()
                .getAttributeByName(EntityType.PLAYER, CustomModelAttribute.IDLE_ANIMATION_ATTRIBUTE_NAME);

        if (animation.equalsIgnoreCase("@none")) {
            String previous = npc.getData().getAttributes().getOrDefault(idleAttribute, null);
            npc.getData().removeAttribute(idleAttribute);
            if (previous != null) {
                CustomModelAttribute.stopAnimation(npc, previous);
            }

            translator.translate("commands.npc.idle_animation.removed")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        ActiveModel model = CustomModelAttribute.getActiveModel(npc);
        if (!CustomModelAttribute.hasAnimations(model)) {
            translator.translate("commands.npc.play_animation.no_animations")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        npc.getData().addAttribute(idleAttribute, animation);
        idleAttribute.apply(npc, animation);

        translator.translate("commands.npc.idle_animation.applied")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("animation", animation)
                .send(sender);
    }

    @Suggestions("IdleAnimationCMD/animation")
    public List<String> suggestAnimations(final CommandContext<CommandSender> context, final CommandInput input) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("@none");

        ActiveModel model = CustomModelAttribute.getActiveModel(context.get("npc"));
        if (model != null) {
            suggestions.addAll(model.getBlueprint().getAnimations().keySet());
        }

        return suggestions;
    }
}
