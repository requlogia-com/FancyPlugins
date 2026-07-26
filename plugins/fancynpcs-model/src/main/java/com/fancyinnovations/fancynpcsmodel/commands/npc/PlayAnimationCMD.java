package com.fancyinnovations.fancynpcsmodel.commands.npc;

import com.fancyinnovations.fancynpcsmodel.fancynpcshook.CustomModelAttribute;
import com.fancyinnovations.fancynpcsmodel.utils.FancyContext;
import com.ticxo.modelengine.api.model.ActiveModel;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PlayAnimationCMD extends FancyContext {

    public static final PlayAnimationCMD INSTANCE = new PlayAnimationCMD();

    private PlayAnimationCMD() {
    }

    @Command("npc play_animation <npc> <animation>")
    @Permission("fancynpcsmodel.command.npc.custom_model")
    public void onPlayAnimation(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Argument(suggestions = "PlayAnimationCMD/animation") String animation,
            final @Flag("loop") boolean loop
    ) {
        if (!CustomModelAttribute.hasAttribute(npc)) {
            translator.translate("commands.npc.play_animation.no_model_assigned")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        CustomModelAttribute.AnimationResult result = CustomModelAttribute.playAnimation(npc, animation, loop);
        switch (result) {
            case NO_MODEL -> translator.translate("commands.npc.play_animation.no_model_assigned")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            case NO_ANIMATIONS -> translator.translate("commands.npc.play_animation.no_animations")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            case NOT_FOUND, FAILED -> translator.translate("commands.npc.play_animation.failed")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .replace("animation", animation)
                    .send(sender);
            case SUCCESS -> translator.translate("commands.npc.play_animation.playing")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .replace("animation", animation)
                    .send(sender);
        }
    }

    @Suggestions("PlayAnimationCMD/animation")
    public List<String> suggestAnimations(final CommandContext<CommandSender> context, final CommandInput input) {
        ActiveModel model = CustomModelAttribute.getActiveModel(context.get("npc"));
        if (model == null) return new ArrayList<>();

        return model.getBlueprint().getAnimations().keySet().stream().toList();
    }
}
