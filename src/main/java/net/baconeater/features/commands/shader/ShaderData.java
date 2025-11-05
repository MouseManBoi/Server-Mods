package net.baconeater.features.commands.shader;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Set;
import java.util.stream.Collectors;

public final class ShaderData {
    private ShaderData() {}

    private static String tagFor(String effect) {
        return "shader:" + effect;
    }

    /** Toggle returns the new state (true if enabled after toggle). */
    public static boolean toggleEffectTag(ServerPlayerEntity player, String effect) {
        String tag = tagFor(effect);
        if (player.getCommandTags().contains(tag)) {
            player.removeCommandTag(tag);
            return false;
        } else {
            player.addCommandTag(tag);
            return true;
        }
    }

    /** Set returns true if a change actually occurred. */
    public static boolean setEffectTag(ServerPlayerEntity player, String effect, boolean enable) {
        String tag = tagFor(effect);
        boolean has = player.getCommandTags().contains(tag);
        if (enable && !has) {
            player.addCommandTag(tag);
            return true;
        } else if (!enable && has) {
            player.removeCommandTag(tag);
            return true;
        }
        return false;
    }

    public static Set<String> getAllShaderTags(ServerPlayerEntity player) {
        return player.getCommandTags().stream()
                .filter(t -> t.startsWith("shader:"))
                .map(t -> t.substring("shader:".length()))
                .collect(Collectors.toSet());
    }
}
