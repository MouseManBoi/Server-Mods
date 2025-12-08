package net.baconeater.features.commands.visibility.client;

import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClientVisibilityManager {
    private static final Set<Integer> HIDDEN_ENTITY_IDS = new HashSet<>();
    private static final Map<Integer, Boolean> PREVIOUS_INVISIBILITY = new HashMap<>();
    private static final Map<Integer, ItemStack> PREVIOUS_ITEM_DISPLAYS = new HashMap<>();

    private ClientVisibilityManager() {
    }

    public static void handlePayload(VisibilityTogglePayload payload) {
        if (payload.action().hides()) {
            HIDDEN_ENTITY_IDS.add(payload.entityId());
            hideEntityIfPresent(payload.entityId());
        } else {
            HIDDEN_ENTITY_IDS.remove(payload.entityId());
            showEntityIfPresent(payload.entityId());
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || HIDDEN_ENTITY_IDS.isEmpty()) {
            return;
        }

        for (int entityId : HIDDEN_ENTITY_IDS) {
            hideEntityIfPresent(entityId);
        }
    }

    public static void clear() {
        HIDDEN_ENTITY_IDS.clear();
        PREVIOUS_INVISIBILITY.clear();
        PREVIOUS_ITEM_DISPLAYS.clear();
    }

    private static void hideEntityIfPresent(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Entity entity = client.world.getEntityById(entityId);
        if (entity == null) {
            return;
        }

        PREVIOUS_INVISIBILITY.putIfAbsent(entityId, entity.isInvisible());
        entity.setInvisible(true);

        if (entity instanceof DisplayEntity.ItemDisplayEntity itemDisplay) {
            PREVIOUS_ITEM_DISPLAYS.putIfAbsent(entityId, itemDisplay.getItemStack().copy());
            itemDisplay.setItemStack(ItemStack.EMPTY);
        }
    }

    private static void showEntityIfPresent(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            PREVIOUS_ITEM_DISPLAYS.remove(entityId);
            PREVIOUS_INVISIBILITY.remove(entityId);
            return;
        }

        Entity entity = client.world.getEntityById(entityId);
        Boolean wasInvisible = PREVIOUS_INVISIBILITY.remove(entityId);
        ItemStack previousItem = PREVIOUS_ITEM_DISPLAYS.remove(entityId);
        if (entity != null) {
            entity.setInvisible(Boolean.TRUE.equals(wasInvisible));

            if (entity instanceof DisplayEntity.ItemDisplayEntity itemDisplay && previousItem != null) {
                itemDisplay.setItemStack(previousItem);
            }
        }
    }
}