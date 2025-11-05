package net.baconeater.features.commands.shader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.baconeater.features.commands.shader.payload.ShaderToggleS2C;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Set;

public final class ShaderCommand {
    private ShaderCommand() {}

    // Tab-complete known effects; free to add/remove
    private static final Set<String> KNOWN_EFFECTS = Set.of(
            "creeper", "spider", "wither", "blaze", "ghost", "cinematicBars", "titanShift"
    );

    private static final SuggestionProvider<ServerCommandSource> EFFECT_SUGGESTIONS = (ctx, builder) -> {
        KNOWN_EFFECTS.forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("shader")
                        .requires(src -> src.hasPermissionLevel(2)) // OP level 2+
                        .then(CommandManager.literal("toggle")
                                .then(CommandManager.argument("targets", EntityArgumentType.players())
                                        .then(CommandManager.argument("effect", StringArgumentType.word())
                                                .suggests(EFFECT_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
                                                    String effect = StringArgumentType.getString(ctx, "effect");
                                                    return toggle(ctx.getSource(), players, effect);
                                                })
                                        )
                                )
                        )
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("targets", EntityArgumentType.players())
                                        .then(CommandManager.argument("effect", StringArgumentType.word())
                                                .suggests(EFFECT_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
                                                    String effect = StringArgumentType.getString(ctx, "effect");
                                                    return set(ctx.getSource(), players, effect, true);
                                                })
                                        )
                                )
                        )
                        .then(CommandManager.literal("disable")
                                .then(CommandManager.argument("targets", EntityArgumentType.players())
                                        .then(CommandManager.argument("effect", StringArgumentType.word())
                                                .suggests(EFFECT_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
                                                    String effect = StringArgumentType.getString(ctx, "effect");
                                                    return set(ctx.getSource(), players, effect, false);
                                                })
                                        )
                                )
                        )
                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                                            var tags = ShaderData.getAllShaderTags(p);
                                            if (tags.isEmpty()) {
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal(p.getName().getString() + " has no shader effects."),
                                                        false
                                                );
                                            } else {
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal(p.getName().getString() + " effects: " + String.join(", ", tags)),
                                                        false
                                                );
                                            }
                                            return tags.size();
                                        })
                                )
                        )
        );
    }

    private static int toggle(ServerCommandSource source, Collection<ServerPlayerEntity> players, String effect) {
        int changed = 0;
        for (ServerPlayerEntity p : players) {
            boolean nowEnabled = ShaderData.toggleEffectTag(p, effect);
            // S2C notify for instant client visuals
            ServerPlayNetworking.send(p, new ShaderToggleS2C(effect, nowEnabled));
            source.sendFeedback(() ->
                    Text.literal(p.getName().getString() + ": " + effect + " toggled -> " + (nowEnabled ? "ON" : "OFF")), true);
            changed++;
        }
        return changed;
    }

    private static int set(ServerCommandSource source, Collection<ServerPlayerEntity> players, String effect, boolean enable) {
        int changed = 0;
        for (ServerPlayerEntity p : players) {
            boolean actuallyChanged = ShaderData.setEffectTag(p, effect, enable);
            if (actuallyChanged) {
                ServerPlayNetworking.send(p, new ShaderToggleS2C(effect, enable));
                source.sendFeedback(() ->
                        Text.literal(p.getName().getString() + ": " + effect + " " + (enable ? "enabled" : "disabled")), true);
                changed++;
            }
        }
        return changed;
    }
}
