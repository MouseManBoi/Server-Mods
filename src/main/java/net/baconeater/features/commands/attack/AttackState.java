package net.baconeater.features.commands.attack;

import net.minecraft.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AttackState {
    private static final Set<UUID> DISABLED_ATTACKERS = ConcurrentHashMap.newKeySet();

    private AttackState() {
    }

    public static void disable(Entity entity) {
        DISABLED_ATTACKERS.add(entity.getUuid());
    }

    public static void enable(Entity entity) {
        DISABLED_ATTACKERS.remove(entity.getUuid());
    }

    public static boolean isDisabled(Entity entity) {
        return DISABLED_ATTACKERS.contains(entity.getUuid());
    }
}
