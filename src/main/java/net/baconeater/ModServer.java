package net.baconeater;

import net.baconeater.features.commands.heal.HealCommand;
import net.baconeater.features.commands.convert.ConvertCommand;
import net.baconeater.features.commands.convert.ConvertState;
import net.baconeater.features.commands.playsound.PlaySoundOffsetCommand;
import net.baconeater.features.commands.playsound.network.PlaySoundOffsetPayload;
import net.baconeater.features.commands.perspective.PerspectiveCommand;
import net.baconeater.features.commands.shader.ShaderCommand;
import net.baconeater.features.commands.skin.SkinCommand;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.baconeater.features.commands.attack.AttackCommand;
import net.baconeater.features.commands.attack.AttackState;
import net.baconeater.features.commands.toast.ToastCommand;
import net.baconeater.features.commands.toast.network.ToastPayload;
import net.baconeater.features.commands.visibility.VisibilityCommand;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.baconeater.features.keybinds.KeybindScoreHandler;
import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ModServer implements ModInitializer {
	@Override
	public void onInitialize() {

		// === Networking ===
		// C2S: keybind actions from client
		PayloadTypeRegistry.playC2S().register(KeybindC2S.TYPE, KeybindC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(PlaySoundOffsetPayload.TYPE, PlaySoundOffsetPayload.CODEC);
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
			PayloadTypeRegistry.playS2C().register(PerspectiveRequestPayload.TYPE, PerspectiveRequestPayload.CODEC);
			PayloadTypeRegistry.playS2C().register(ToggleShaderPayload.TYPE, ToggleShaderPayload.CODEC);
			PayloadTypeRegistry.playS2C().register(VisibilityTogglePayload.TYPE, VisibilityTogglePayload.CODEC);
			PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE, ToastPayload.CODEC);
		}
		ServerPlayNetworking.registerGlobalReceiver(KeybindC2S.TYPE, (payload, ctx) -> {
			MinecraftServer server = ctx.server();
			ServerPlayer player = ctx.player();
			server.execute(() -> KeybindScoreHandler.handle(server.getScoreboard(), player, payload.action()));
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!isAttackDisabled(source.getEntity())
						&& !isAttackDisabled(source.getDirectEntity())
						&& ConvertState.applyConvertedDamage(entity, source, amount));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShaderCommand.register(dispatcher);
			HealCommand.register(dispatcher);
			ConvertCommand.register(dispatcher);
			VisibilityCommand.register(dispatcher);
			PerspectiveCommand.register(dispatcher);
			AttackCommand.register(dispatcher);
			SkinCommand.register(dispatcher);
			ToastCommand.register(dispatcher, registryAccess);
			PlaySoundOffsetCommand.register(dispatcher);
		});
	}

	private static boolean isAttackDisabled(Entity entity) {
		return entity != null && AttackState.isDisabled(entity);
	}
}
