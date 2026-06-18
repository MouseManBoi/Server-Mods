package net.baconeater.features.commands.attack;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AttackState {
    private static final Set<UUID> DISABLED_ATTACKERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DISABLED_HURT_TINTS = ConcurrentHashMap.newKeySet();

    private AttackState() {
    }

    public static void disable(Entity entity) {
        DISABLED_ATTACKERS.add(entity.getUUID());
    }

    public static void enable(Entity entity) {
        DISABLED_ATTACKERS.remove(entity.getUUID());
    }

    public static boolean isDisabled(Entity entity) {
        return DISABLED_ATTACKERS.contains(entity.getUUID());
    }

    public static void setHurtTintDisabled(Entity entity, boolean disabled) {
        if (disabled) {
            DISABLED_HURT_TINTS.add(entity.getUUID());
        } else {
            DISABLED_HURT_TINTS.remove(entity.getUUID());
        }
    }

    public static boolean isHurtTintDisabled(Entity entity) {
        return DISABLED_HURT_TINTS.contains(entity.getUUID());
    }
}
