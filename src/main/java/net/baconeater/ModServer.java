package net.baconeater;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.baconeater.features.keybinds.KeybindScoreHandler;
import net.baconeater.features.keybinds.payload.KeybindC2S;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class ModServer implements ModInitializer {
	public static final String MOD_ID = "creepershader";
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@Override
	public void onInitialize() {

		// === Networking ===
		// C2S: keybind actions from client
		PayloadTypeRegistry.playC2S().register(KeybindC2S.ID, KeybindC2S.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KeybindC2S.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayerEntity player = ctx.player();
			server.execute(() -> KeybindScoreHandler.handle(server.getScoreboard(), player, payload.action()));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(createShaderCommand()));
	}

	private LiteralArgumentBuilder<ServerCommandSource> createShaderCommand() {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("shader")
				.requires(source -> source.hasPermissionLevel(2));

		registerShaderAction(root, "toggle", ToggleShaderPayload.ShaderAction.TOGGLE);
		registerShaderAction(root, "enable", ToggleShaderPayload.ShaderAction.ENABLE);
		registerShaderAction(root, "disable", ToggleShaderPayload.ShaderAction.DISABLE);

		return root;
	}

	private void registerShaderAction(
			LiteralArgumentBuilder<ServerCommandSource> root,
			String name,
			ToggleShaderPayload.ShaderAction action) {
		root.then(CommandManager.literal(name)
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("shader", IdentifierArgumentType.identifier())
								.executes(context -> applyShaderUpdate(
										EntityArgumentType.getPlayers(context, "targets"),
										context.getSource(),
										IdentifierArgumentType.getIdentifier(context, "shader"),
										action)))
						.then(CommandManager.literal("creeper")
								.executes(context -> applyShaderUpdate(
										EntityArgumentType.getPlayers(context, "targets"),
										context.getSource(),
										Identifier.of("minecraft", "creeper"),
										action)))));
	}

	private int applyShaderUpdate(
			Collection<ServerPlayerEntity> players,
			ServerCommandSource source,
			Identifier shader,
			ToggleShaderPayload.ShaderAction action) {
		ToggleShaderPayload payload = switch (action) {
			case TOGGLE -> ToggleShaderPayload.toggle(shader);
			case ENABLE -> ToggleShaderPayload.enable(shader);
			case DISABLE -> ToggleShaderPayload.disable(shader);
		};
		players.forEach(player -> ServerPlayNetworking.send(player, payload));
		String message = switch (action) {
			case TOGGLE -> "Toggled";
			case ENABLE -> "Enabled";
			case DISABLE -> "Disabled";
		};
		source.sendFeedback(
				() -> Text.literal(message + " shader " + shader + " for " + players.size() + " player(s)."),
				true);
		return players.size();
	}
}
