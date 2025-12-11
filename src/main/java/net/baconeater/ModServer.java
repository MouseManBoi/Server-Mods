package net.baconeater;

import net.baconeater.features.commands.heal.HealCommand;
import net.baconeater.features.commands.perspective.PerspectiveCommand;
import net.baconeater.features.commands.shader.ShaderCommand;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.baconeater.features.commands.visibility.VisibilityCommand;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.baconeater.features.keybinds.KeybindScoreHandler;
import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class ModServer implements ModInitializer {
	@Override
	public void onInitialize() {

		// === Networking ===
		// C2S: keybind actions from client
		PayloadTypeRegistry.playC2S().register(KeybindC2S.ID, KeybindC2S.CODEC);
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
			PayloadTypeRegistry.playS2C().register(PerspectiveRequestPayload.ID, PerspectiveRequestPayload.CODEC);
			PayloadTypeRegistry.playS2C().register(ToggleShaderPayload.ID, ToggleShaderPayload.CODEC);
			PayloadTypeRegistry.playS2C().register(VisibilityTogglePayload.ID, VisibilityTogglePayload.CODEC);
		}
		ServerPlayNetworking.registerGlobalReceiver(KeybindC2S.ID, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayerEntity player = ctx.player();
			server.execute(() -> KeybindScoreHandler.handle(server.getScoreboard(), player, payload.action()));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShaderCommand.register(dispatcher);
			HealCommand.register(dispatcher);
			VisibilityCommand.register(dispatcher);
			PerspectiveCommand.register(dispatcher);
		});
	}
}