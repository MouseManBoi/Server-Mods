package net.baconeater;

import net.baconeater.features.commands.heal.HealCommand;
import net.baconeater.features.commands.perspective.PerspectiveCommand;
import net.baconeater.features.commands.perspective.PerspectiveQueryTracker;
import net.baconeater.features.commands.perspective.network.PerspectiveReportPayload;
import net.baconeater.features.commands.shader.ShaderCommand;
import net.baconeater.features.commands.visibility.VisibilityCommand;
import net.baconeater.features.keybinds.KeybindScoreHandler;
import net.baconeater.features.keybinds.payload.KeybindC2S;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class ModServer implements ModInitializer {
	@Override
	public void onInitialize() {

		// === Networking ===
		// C2S: keybind actions from client
		PayloadTypeRegistry.playC2S().register(KeybindC2S.ID, KeybindC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(PerspectiveReportPayload.ID, PerspectiveReportPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KeybindC2S.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayerEntity player = ctx.player();
			server.execute(() -> KeybindScoreHandler.handle(server.getScoreboard(), player, payload.action()));
		});
		ServerPlayNetworking.registerGlobalReceiver(PerspectiveReportPayload.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			server.execute(() -> PerspectiveQueryTracker.handleResponse(payload.requestId(), payload.state()));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShaderCommand.register(dispatcher);
			HealCommand.register(dispatcher);
			VisibilityCommand.register(dispatcher);
			PerspectiveCommand.register(dispatcher);
		});
	}
}