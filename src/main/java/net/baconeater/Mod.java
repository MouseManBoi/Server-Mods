package net.baconeater;

import net.baconeater.features.keybinds.KeybindScoreHandler;
import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.commands.shader.ShaderCommand;
import net.baconeater.features.commands.shader.payload.ShaderToggleS2C;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class Mod implements ModInitializer {
	@Override
	public void onInitialize() {
		// === Commands ===
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShaderCommand.register(dispatcher);
		});

		// === Networking ===
		// C2S: keybind actions from client
		PayloadTypeRegistry.playC2S().register(KeybindC2S.ID, KeybindC2S.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KeybindC2S.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayerEntity player = ctx.player();
			server.execute(() -> KeybindScoreHandler.handle(server.getScoreboard(), player, payload.action()));
		});

		// S2C: notify clients when a shader effect is toggled/enabled/disabled by command
		PayloadTypeRegistry.playS2C().register(ShaderToggleS2C.ID, ShaderToggleS2C.CODEC);
	}
}
