package net.baconeater;

import net.baconeater.features.commands.ShaderCommand;
import net.baconeater.features.keybinds.ScoreHandler;
import net.baconeater.features.keybinds.payload.C2SPayload;
import net.baconeater.features.shaders.payload.SelectS2C;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class Mod implements ModInitializer {
	@Override
	public void onInitialize() {
		// === Networking ===
		// C2S (keybind actions from client)
		PayloadTypeRegistry.playC2S().register(
				C2SPayload.ID,
				C2SPayload.CODEC
		);
		ServerPlayNetworking.registerGlobalReceiver(C2SPayload.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayerEntity player = ctx.player();
			server.execute(() ->
					ScoreHandler.handle(server.getScoreboard(), player, payload.action())
			);
		});

		// S2C (shader selection from server → clients) — register codec ONCE here (not in ModClient)
		PayloadTypeRegistry.playS2C().register(
				SelectS2C.ID,
				SelectS2C.CODEC
		);

		// === Commands ===
		ShaderCommand.register();
	}
}
