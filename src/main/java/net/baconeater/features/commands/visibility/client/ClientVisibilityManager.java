package net.baconeater.features.commands.visibility.client;

import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public final class ClientVisibilityManager {
    private static final Map<Integer, Boolean> HIDDEN_ENTITY_IDS = new HashMap<>();
    private static final Map<Integer, Boolean> PREVIOUS_INVISIBILITY = new HashMap<>();

    private ClientVisibilityManager() {
    }

    public static void handlePayload(VisibilityTogglePayload payload) {
        if (payload.action().hides()) {
            HIDDEN_ENTITY_IDS.put(payload.entityId(), payload.renderOutsideFirstPerson());
            refreshEntityVisibility(payload.entityId());
        } else {
            HIDDEN_ENTITY_IDS.remove(payload.entityId());
            showEntityIfPresent(payload.entityId());
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || HIDDEN_ENTITY_IDS.isEmpty()) {
            return;
        }

        for (int entityId : HIDDEN_ENTITY_IDS.keySet()) {
            refreshEntityVisibility(entityId);
        }
    }

    public static void clear() {
        HIDDEN_ENTITY_IDS.clear();
        PREVIOUS_INVISIBILITY.clear();
    }

    public static boolean isHidden(int entityId) {
        return HIDDEN_ENTITY_IDS.containsKey(entityId);
    }

    public static boolean shouldSuppressRender(int entityId) {
        if (!isHidden(entityId)) {
            return false;
        }

        return shouldHideInCurrentPerspective(entityId);
    }

    private static void refreshEntityVisibility(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Entity entity = client.world.getEntityById(entityId);
        if (entity == null) {
            return;
        }

        if (shouldHideInCurrentPerspective(entityId)) {
            hideEntity(entity);
            return;
        }

        restoreEntityVisibility(entity);
    }

    public static void reapplyHide(Entity entity) {
        if (entity == null) {
            return;
        }

        if (shouldHideInCurrentPerspective(entity.getId())) {
            hideEntity(entity);
            return;
        }

        restoreEntityVisibility(entity);
    }

    private static void hideEntity(Entity entity) {
        int entityId = entity.getId();
        PREVIOUS_INVISIBILITY.putIfAbsent(entityId, entity.isInvisible());
        entity.setInvisible(true);
    }

    private static void showEntityIfPresent(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            PREVIOUS_INVISIBILITY.remove(entityId);
            return;
        }

        Entity entity = client.world.getEntityById(entityId);
        if (entity != null) {
            restoreEntityVisibility(entity);
        } else {
            PREVIOUS_INVISIBILITY.remove(entityId);
        }
    }

    private static boolean shouldHideInCurrentPerspective(int entityId) {
        Boolean renderOutsideFirstPerson = HIDDEN_ENTITY_IDS.get(entityId);
        if (renderOutsideFirstPerson == null) {
            return false;
        }

        return !renderOutsideFirstPerson || isFirstPersonPerspective();
    }

    private static boolean isFirstPersonPerspective() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) {
            return true;
        }

        return client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    private static void restoreEntityVisibility(Entity entity) {
        Boolean wasInvisible = PREVIOUS_INVISIBILITY.remove(entity.getId());
        entity.setInvisible(Boolean.TRUE.equals(wasInvisible));
    }
}
