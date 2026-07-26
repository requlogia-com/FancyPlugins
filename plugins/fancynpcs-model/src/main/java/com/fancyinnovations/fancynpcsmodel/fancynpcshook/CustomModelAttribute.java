package com.fancyinnovations.fancynpcsmodel.fancynpcshook;

import com.fancyinnovations.fancynpcsmodel.main.FancyNpcsModelPlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.entity.Hitbox;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import de.oliver.fancyanalytics.logger.properties.StringProperty;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import de.oliver.fancylib.ReflectionUtils;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomModelAttribute {

    public static final String ATTRIBUTE_NAME = "custom_model";
    public static final String IDLE_ANIMATION_ATTRIBUTE_NAME = "model_idle_animation";
    public static final String HITBOX_ATTRIBUTE_NAME = "model_hitbox";

    private static final Hitbox DEFAULT_HITBOX = new Hitbox(0.6, 1.8, 0.6, 1.62);

    private static final Map<UUID, Npc> MODELED_NPCS = new ConcurrentHashMap<>();

    private static final Map<String, UUID> NPC_ENTITY_UUIDS = new ConcurrentHashMap<>();

    private static final Map<ActiveModel, String> ACTIVE_IDLE_ANIMATIONS = new ConcurrentHashMap<>();

    private static final Map<ActiveModel, CommandAnimation> ACTIVE_COMMAND_ANIMATIONS = new ConcurrentHashMap<>();

    private record CommandAnimation(Npc npc, IAnimationProperty property) {
    }

    public enum AnimationResult {
        SUCCESS,
        NO_MODEL,
        NO_ANIMATIONS,
        NOT_FOUND,
        FAILED
    }

    public static NpcAttribute getModelAttribute() {
        return new NpcAttribute(
                ATTRIBUTE_NAME,
                List.of(),
                List.of(EntityType.PLAYER),
                CustomModelAttribute::setModel
        );
    }

    public static NpcAttribute getIdleAnimationAttribute() {
        return new NpcAttribute(
                IDLE_ANIMATION_ATTRIBUTE_NAME,
                List.of(),
                List.of(EntityType.PLAYER),
                CustomModelAttribute::setIdleAnimation
        );
    }

    private static void setIdleAnimation(Npc npc, String animation) {
        playIdleAnimation(npc);
    }

    public static NpcAttribute getHitboxAttribute() {
        return new NpcAttribute(
                HITBOX_ATTRIBUTE_NAME,
                List.of(),
                List.of(EntityType.PLAYER),
                CustomModelAttribute::setHitbox
        );
    }

    private static void setHitbox(Npc npc, String value) {
        ActiveModel model = getActiveModel(npc);
        if (model == null) {
            return;
        }

        updateModelHitbox(npc, model);

        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity != null) {
            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(bukkitEntity);
            if (modeledEntity != null && modeledEntity.getBase() instanceof Dummy<?> dummy) {
                dummy.setHitbox(resolveHitbox(npc, model.getBlueprint().getMainHitbox(), npc.getData().getScale()));
                dummy.syncLocation(npc.getData().getLocation());
            }
        }
    }

    private static void updateModelHitbox(Npc npc, ActiveModel model) {
        /*
         * ModelEngine's hitbox scale is uniform. Deriving that scale from the
         * requested height also forces the blueprint's aspect ratio onto the
         * width, so a custom width can never be represented correctly.
         *
         * The Dummy base supports an explicit Hitbox with independent width,
         * height and depth. Keep the model from replacing that main hitbox and
         * let resolveHitbox(...) configure the Dummy instead.
         */
        model.setMainHitbox(false);
        model.setHitboxScale(npc.getData().getScale());
    }

    private static void setModel(Npc npc, String modelName) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return;
        }
        bukkitEntity.customName(Component.empty());

        UUID previousUuid = NPC_ENTITY_UUIDS.get(npc.getData().getId());
        if (previousUuid != null && !previousUuid.equals(bukkitEntity.getUniqueId())) {
            destroyModel(previousUuid);
            NPC_ENTITY_UUIDS.remove(npc.getData().getId());
        }

        if (modelName.equalsIgnoreCase("@none")) {
            closeAllModels(bukkitEntity);
            return;
        }

        ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(modelName);
        if (blueprint == null) {
            FancyNpcsModelPlugin.get().getFancyLogger().error(
                    "Failed to get model with name " + modelName,
                    StringProperty.of("model_name", modelName),
                    StringProperty.of("npc_name", npc.getData().getName())
            );
            return;
        }

        ModeledEntity currentModeledEntity = ModelEngineAPI.getModeledEntity(bukkitEntity);
        ActiveModel currentModel = getMatchingModel(currentModeledEntity, modelName);
        if (currentModel != null) {
            configureModel(npc, currentModel);
            syncModelTransform(currentModeledEntity, npc.getData().getLocation());
            bukkitEntity.setInvisible(true);
            MODELED_NPCS.put(bukkitEntity.getUniqueId(), npc);
            NPC_ENTITY_UUIDS.put(npc.getData().getId(), bukkitEntity.getUniqueId());
            return;
        }

        closeAllModels(bukkitEntity);

        ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelName);
        configureModel(npc, activeModel);

        Dummy<Entity> dummy = new Dummy<>(bukkitEntity.getUniqueId(), bukkitEntity);

        dummy.setHitbox(resolveHitbox(npc, blueprint.getMainHitbox(), npc.getData().getScale()));

        dummy.syncLocation(npc.getData().getLocation());
        dummy.setDetectingPlayers(true);

        ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(bukkitEntity.getUniqueId(), () -> dummy);
        modeledEntity.addModel(activeModel, true);
        syncModelTransform(modeledEntity, npc.getData().getLocation());

        bukkitEntity.setInvisible(true);

        MODELED_NPCS.put(bukkitEntity.getUniqueId(), npc);
        NPC_ENTITY_UUIDS.put(npc.getData().getId(), bukkitEntity.getUniqueId());

        playIdleAnimation(npc);
    }

    private static ActiveModel getMatchingModel(ModeledEntity modeledEntity, String modelName) {
        if (modeledEntity == null || modeledEntity.isDestroyed()) {
            return null;
        }

        for (ActiveModel model : modeledEntity.getModels().values()) {
            if (!model.isDestroyed() && model.getBlueprint().getName().equalsIgnoreCase(modelName)) {
                return model;
            }
        }
        return null;
    }

    private static void configureModel(Npc npc, ActiveModel model) {
        model.setScale(npc.getData().getScale());
        updateModelHitbox(npc, model);

        ModeledEntity modeledEntity = model.getModeledEntity();
        if (modeledEntity != null && modeledEntity.getBase() instanceof Dummy<?> dummy) {
            dummy.setHitbox(resolveHitbox(
                    npc,
                    model.getBlueprint().getMainHitbox(),
                    npc.getData().getScale()
            ));
        }
    }

    /**
     * Keeps ModelEngine's dummy entity in lockstep with FancyNpcs' packet-only entity.
     * The underlying Bukkit entity is not registered in the world, so ModelEngine cannot
     * discover location and rotation changes by itself.
     */
    public static void syncModelTransform(Npc npc) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return;
        }

        syncModelTransform(ModelEngineAPI.getModeledEntity(bukkitEntity), npc.getData().getLocation());
    }

    private static void syncModelTransform(ModeledEntity modeledEntity, Location location) {
        if (modeledEntity == null || modeledEntity.isDestroyed() || location == null) {
            return;
        }

        if (modeledEntity.getBase() instanceof Dummy<?> dummy) {
            dummy.syncLocation(location);
        }

        // Avoid ModelEngine interpolating from its default 0° rotation when a viewer
        // starts tracking the model for the first time.
        modeledEntity.setYBodyRotImmediately(location.getYaw());
        modeledEntity.setYHeadRotImmediately(location.getYaw());
        modeledEntity.setXHeadRotImmediately(location.getPitch());
    }

    private static Entity getBukkitEntity(Npc npc) {
        Object nmsEntity = ReflectionUtils.getValue(npc, "npc");
        if (nmsEntity == null) {
            FancyNpcsModelPlugin.get().getFancyLogger().error("Failed to get NMS entity from NPC");
            return null;
        }

        try {
            return (Entity) ReflectionUtils.getMethod(nmsEntity, "getBukkitEntity").invoke(nmsEntity);
        } catch (IllegalAccessException | InvocationTargetException e) {
            FancyNpcsModelPlugin.get().getFancyLogger().error(
                    "Failed to invoke getBukkitEntity method on NMS entity",
                    ThrowableProperty.of(e),
                    StringProperty.of("npc_name", npc.getData().getName())
            );
            return null;
        }
    }

    public static void removeAllModels(Npc npc) {
        Entity bukkitEntity = getBukkitEntity(npc);

        if (bukkitEntity != null) {
            bukkitEntity.setInvisible(false);
        }

        Set<UUID> uuids = new HashSet<>();
        if (bukkitEntity != null) {
            uuids.add(bukkitEntity.getUniqueId());
        }
        UUID remembered = NPC_ENTITY_UUIDS.get(npc.getData().getId());
        if (remembered != null) {
            uuids.add(remembered);
        }

        for (UUID uuid : uuids) {
            destroyModel(uuid);
        }
        NPC_ENTITY_UUIDS.remove(npc.getData().getId());
    }

    private static Hitbox resolveHitbox(Npc npc, Hitbox blueprintHitbox, float scale) {
        Hitbox hitbox = parseCustomHitbox(getAttributeValue(npc, HITBOX_ATTRIBUTE_NAME));
        if (hitbox == null) {
            hitbox = blueprintHitbox;
        }
        if (hitbox == null || hitbox.getWidth() <= 0 || hitbox.getHeight() <= 0) {
            hitbox = DEFAULT_HITBOX;
        }

        if (scale == 1) {
            return hitbox;
        }
        return new Hitbox(
                hitbox.getWidth() * scale,
                hitbox.getHeight() * scale,
                hitbox.getDepth() * scale,
                hitbox.getEyeHeight() * scale);
    }

    public static Hitbox parseCustomHitbox(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("@default")) {
            return null;
        }

        String[] parts = value.split(";");
        if (parts.length < 2) {
            return null;
        }
        try {
            double width = Double.parseDouble(parts[0].trim());
            double height = Double.parseDouble(parts[1].trim());
            if (width <= 0 || height <= 0) {
                return null;
            }
            double eyeHeight = parts.length >= 3 ? Double.parseDouble(parts[2].trim()) : height * 0.85;
            return new Hitbox(width, height, width, eyeHeight);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void closeAllModels(Entity bukkitEntity) {
        bukkitEntity.setInvisible(false);
        destroyModel(bukkitEntity.getUniqueId());
    }

    private static void destroyModel(UUID entityUuid) {
        MODELED_NPCS.remove(entityUuid);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entityUuid);
        if (modeledEntity != null) {
            for (ActiveModel model : modeledEntity.getModels().values()) {
                ACTIVE_IDLE_ANIMATIONS.remove(model);
                ACTIVE_COMMAND_ANIMATIONS.remove(model);
            }
            modeledEntity.destroy();
        }

        ModelEngineAPI.removeModeledEntity(entityUuid);
    }

    public static boolean hasAttribute(Npc npc) {
        for (Map.Entry<NpcAttribute, String> entry : npc.getData().getAttributes().entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(ATTRIBUTE_NAME)) {
                return true;
            }
        }

        return false;
    }

    public static ActiveModel getActiveModel(Npc npc) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return null;
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(bukkitEntity);
        if (modeledEntity == null) {
            return null;
        }

        Map<String, ActiveModel> models = modeledEntity.getModels();
        if (models.isEmpty()) {
            return null;
        }

        return models.values().iterator().next();
    }

    public static Npc getNpcByEntity(UUID entityUuid) {
        return MODELED_NPCS.get(entityUuid);
    }

    public static boolean hasAnimations(ActiveModel model) {
        return model != null && !model.getBlueprint().getAnimations().isEmpty();
    }

    private static String resolveAnimation(ActiveModel model, String name) {
        if (model == null || name == null) {
            return null;
        }
        for (String key : model.getBlueprint().getAnimations().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private static String getAttributeValue(Npc npc, String attributeName) {
        for (Map.Entry<NpcAttribute, String> entry : npc.getData().getAttributes().entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(attributeName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static AnimationResult playAnimation(Npc npc, String animation, boolean loop) {
        ActiveModel model = getActiveModel(npc);
        if (model == null) {
            return AnimationResult.NO_MODEL;
        }
        if (!hasAnimations(model)) {
            return AnimationResult.NO_ANIMATIONS;
        }

        String resolved = resolveAnimation(model, animation);
        if (resolved == null) {
            return AnimationResult.NOT_FOUND;
        }

        CommandAnimation previous = ACTIVE_COMMAND_ANIMATIONS.remove(model);
        if (previous != null && !previous.property().isEnded()) {
            model.getAnimationHandler().forceStopAnimation(previous.property().getName());
        }
        stopIdleAnimation(model);

        IAnimationProperty property = model.getAnimationHandler().playAnimation(resolved, 0.2, 0.2, 1.0, true);
        if (property == null) {
            playIdleAnimation(npc);
            return AnimationResult.FAILED;
        }

        property.setForceLoopMode(loop ? BlueprintAnimation.LoopMode.LOOP : BlueprintAnimation.LoopMode.ONCE);
        property.setForceOverride(BlueprintAnimation.OverrideMode.OVERRIDE);
        ACTIVE_COMMAND_ANIMATIONS.put(model, new CommandAnimation(npc, property));
        return AnimationResult.SUCCESS;
    }

    public static void playIdleAnimation(Npc npc) {
        String idle = getAttributeValue(npc, IDLE_ANIMATION_ATTRIBUTE_NAME);
        if (idle == null || idle.isEmpty() || idle.equalsIgnoreCase("@none")) {
            return;
        }

        ActiveModel model = getActiveModel(npc);
        String resolved = resolveAnimation(model, idle);
        if (resolved == null) {
            return;
        }

        CommandAnimation commandAnimation = ACTIVE_COMMAND_ANIMATIONS.get(model);
        if (commandAnimation != null) {
            if (!commandAnimation.property().isEnded()) {
                return;
            }
            ACTIVE_COMMAND_ANIMATIONS.remove(model, commandAnimation);
        }

        stopIdleAnimation(model);
        IAnimationProperty property = model.getAnimationHandler().playAnimation(resolved, 0.2, 0.2, 1.0, true);
        if (property != null) {
            property.setForceLoopMode(BlueprintAnimation.LoopMode.LOOP);
            ACTIVE_IDLE_ANIMATIONS.put(model, resolved);
        }
    }

    private static void stopIdleAnimation(ActiveModel model) {
        String activeIdle = ACTIVE_IDLE_ANIMATIONS.remove(model);
        if (activeIdle != null) {
            model.getAnimationHandler().forceStopAnimation(activeIdle);
        }
    }

    public static void handleAnimationEnd(ActiveModel model, IAnimationProperty property) {
        CommandAnimation commandAnimation = ACTIVE_COMMAND_ANIMATIONS.get(model);
        if (commandAnimation == null || commandAnimation.property() != property
                || !ACTIVE_COMMAND_ANIMATIONS.remove(model, commandAnimation)) {
            return;
        }

        FancyNpcsModelPlugin.get().getServer().getGlobalRegionScheduler().runDelayed(
                FancyNpcsModelPlugin.get(),
                task -> {
                    if (!model.isDestroyed() && !ACTIVE_COMMAND_ANIMATIONS.containsKey(model)) {
                        playIdleAnimation(commandAnimation.npc());
                    }
                },
                1L
        );
    }

    public static void stopAnimation(Npc npc, String animation) {
        ActiveModel model = getActiveModel(npc);
        String resolved = resolveAnimation(model, animation);
        if (resolved == null) {
            return;
        }
        ACTIVE_IDLE_ANIMATIONS.remove(model, resolved);
        model.getAnimationHandler().forceStopAnimation(resolved);
    }
}
