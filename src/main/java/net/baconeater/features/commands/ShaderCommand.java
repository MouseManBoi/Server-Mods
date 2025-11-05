package net.baconeater.features.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.baconeater.features.shaders.ShaderResolver;
import net.baconeater.features.shaders.payload.ShaderSelectS2C;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ShaderCommand {
    private ShaderCommand() {}

    private static boolean enabled = false;
    private static Identifier last = null;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(
                CommandManager.literal("shader")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Identifier id = ShaderResolver.toShaderPath(StringArgumentType.getString(ctx, "id"));
                                            if (id == null) { ctx.getSource().sendError(Text.literal("Invalid id")); return 0; }
                                            last = id; enabled = true; broadcast(ctx.getSource().getServer(), id);
                                            ctx.getSource().sendFeedback(() -> Text.literal("[shader] enabled: " + id), true);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    if (last == null) { ctx.getSource().sendError(Text.literal("No previous shader")); return 0; }
                                    enabled = true; broadcast(ctx.getSource().getServer(), last);
                                    ctx.getSource().sendFeedback(() -> Text.literal("[shader] enabled: " + last), true);
                                    return 1;
                                })
                        )
                        .then(CommandManager.literal("disable").executes(ctx -> {
                            enabled = false; broadcast(ctx.getSource().getServer(), null);
                            ctx.getSource().sendFeedback(() -> Text.literal("[shader] disabled"), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("toggle")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Identifier id = ShaderResolver.toShaderPath(StringArgumentType.getString(ctx, "id"));
                                            if (id == null) { ctx.getSource().sendError(Text.literal("Invalid id")); return 0; }
                                            if (enabled && id.equals(last)) { enabled = false; broadcast(ctx.getSource().getServer(), null); ctx.getSource().sendFeedback(() -> Text.literal("[shader] disabled"), true); }
                                            else { last = id; enabled = true; broadcast(ctx.getSource().getServer(), id); ctx.getSource().sendFeedback(() -> Text.literal("[shader] enabled: " + id), true); }
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    if (!enabled) {
                                        if (last == null) { ctx.getSource().sendError(Text.literal("No previous shader")); return 0; }
                                        enabled = true; broadcast(ctx.getSource().getServer(), last);
                                        ctx.getSource().sendFeedback(() -> Text.literal("[shader] enabled: " + last), true);
                                    } else {
                                        enabled = false; broadcast(ctx.getSource().getServer(), null);
                                        ctx.getSource().sendFeedback(() -> Text.literal("[shader] disabled"), true);
                                    }
                                    return 1;
                                })
                        )
                        .then(CommandManager.literal("status").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("[shader] " + (enabled ? "ENABLED " + last : "DISABLED")), false);
                            return 1;
                        }))
        );
    }

    private static void broadcast(MinecraftServer server, Identifier idOrNull) {
        var payload = new ShaderSelectS2C(idOrNull);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
