package net.baconeater.features.commands.convert;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConvertState {
    private static final Map<UUID, ResourceKey<DamageType>> DAMAGE_TYPES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> false);

    private ConvertState() {
    }

    public static void setDamageType(Entity entity, ResourceKey<DamageType> DamageType) {
        DAMAGE_TYPES.put(entity.getUUID(), DamageType);
    }

    public static boolean applyConvertedDamage(LivingEntity target, DamageSource source, float amount) {
        if (CONVERTING.get()) {
            return true;
        }

        ResourceKey<DamageType> DamageType = getConvertedDamageType(source);
        if (DamageType == null || source.is(DamageType) || !(target.level() instanceof ServerLevel world)) {
            return true;
        }

        Registry<DamageType> registry = world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        Holder.Reference<DamageType> damageTypeEntry = registry.getOrThrow(DamageType);
        DamageSource convertedSource = new DamageSource(damageTypeEntry, source.getDirectEntity(), source.getEntity());

        CONVERTING.set(true);
        try {
            target.hurtServer(world, convertedSource, amount);
        } finally {
            CONVERTING.set(false);
        }
        return false;
    }

    private static ResourceKey<DamageType> getConvertedDamageType(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            ResourceKey<DamageType> DamageType = DAMAGE_TYPES.get(attacker.getUUID());
            if (DamageType != null) {
                return DamageType;
            }
        }

        Entity directSource = source.getDirectEntity();
        return directSource == null ? null : DAMAGE_TYPES.get(directSource.getUUID());
    }
}
