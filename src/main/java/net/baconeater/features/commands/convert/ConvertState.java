package net.baconeater.features.commands.convert;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConvertState {
    private static final Map<UUID, RegistryKey<DamageType>> DAMAGE_TYPES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> false);

    private ConvertState() {
    }

    public static void setDamageType(Entity entity, RegistryKey<DamageType> damageType) {
        DAMAGE_TYPES.put(entity.getUuid(), damageType);
    }

    public static boolean applyConvertedDamage(LivingEntity target, DamageSource source, float amount) {
        if (CONVERTING.get()) {
            return true;
        }

        RegistryKey<DamageType> damageType = getConvertedDamageType(source);
        if (damageType == null || source.isOf(damageType) || !(target.getEntityWorld() instanceof ServerWorld world)) {
            return true;
        }

        Registry<DamageType> registry = world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE);
        RegistryEntry.Reference<DamageType> damageTypeEntry = registry.getOrThrow(damageType);
        DamageSource convertedSource = new DamageSource(damageTypeEntry, source.getSource(), source.getAttacker());

        CONVERTING.set(true);
        try {
            target.damage(world, convertedSource, amount);
        } finally {
            CONVERTING.set(false);
        }
        return false;
    }

    private static RegistryKey<DamageType> getConvertedDamageType(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker != null) {
            RegistryKey<DamageType> damageType = DAMAGE_TYPES.get(attacker.getUuid());
            if (damageType != null) {
                return damageType;
            }
        }

        Entity directSource = source.getSource();
        return directSource == null ? null : DAMAGE_TYPES.get(directSource.getUuid());
    }
}
