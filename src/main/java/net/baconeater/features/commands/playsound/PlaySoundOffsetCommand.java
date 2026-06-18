package net.baconeater.features.commands.playsound;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.baconeater.features.commands.playsound.network.PlaySoundOffsetPayload;
import net.baconeater.mixin.CommandNodeAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

public final class PlaySoundOffsetCommand {
    private static final DynamicCommandExceptionType CANNOT_SEND_EXCEPTION =
            new DynamicCommandExceptionType(player -> Component.literal("Cannot play an offset sound for " + player + " because their client does not have this mod's playsound channel."));
    private static final DynamicCommandExceptionType UNKNOWN_SOUND_ARGUMENT_EXCEPTION =
            new DynamicCommandExceptionType(type -> Component.literal("Unsupported playsound sound argument type: " + type));

    private PlaySoundOffsetCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        replaceMinVolumeArgument(dispatcher.getRoot());
    }

    private static void replaceMinVolumeArgument(CommandNode<CommandSourceStack> node) {
        List<CommandNode<CommandSourceStack>> children = List.copyOf(node.getChildren());
        for (CommandNode<CommandSourceStack> child : children) {
            if ("minVolume".equals(child.getName())) {
                removeChild(node, child.getName());
                node.addChild(createSecondsArgument(child));
            } else {
                replaceMinVolumeArgument(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeChild(CommandNode<CommandSourceStack> node, String name) {
        CommandNodeAccessor<CommandSourceStack> accessor = (CommandNodeAccessor<CommandSourceStack>) node;
        accessor.server_mods$getChildren().remove(name);
        accessor.server_mods$getArguments().remove(name);
        accessor.server_mods$getLiterals().remove(name);
    }

    private static ArgumentCommandNode<CommandSourceStack, Float> createSecondsArgument(CommandNode<CommandSourceStack> minVolumeNode) {
        return RequiredArgumentBuilder.<CommandSourceStack, Float>argument("seconds", FloatArgumentType.floatArg(0.0F))
                .requires(minVolumeNode.getRequirement())
                .executes(context -> execute(context, FloatArgumentType.getFloat(context, "seconds")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static int execute(CommandContext<CommandSourceStack> context, float seconds) throws CommandSyntaxException {
        Identifier soundId = getSoundId(context);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        float volume = context.getArgument("volume", Float.class);
        float pitch = context.getArgument("pitch", Float.class);
        PlaySoundOffsetPayload payload = new PlaySoundOffsetPayload(
                soundId,
                getCategory(context),
                pos,
                volume,
                pitch,
                context.getSource().getLevel().getRandom().nextLong(),
                Math.round(seconds)
        );

        for (ServerPlayer target : targets) {
            if (!ServerPlayNetworking.canSend(target, PlaySoundOffsetPayload.TYPE)) {
                throw CANNOT_SEND_EXCEPTION.create(target.getName().getString());
            }
            ServerPlayNetworking.send(target, payload);
        }

        if (targets.size() == 1) {
            ServerPlayer target = targets.iterator().next();
            context.getSource().sendSuccess(() -> Component.translatable("commands.playsound.success.single", Component.literal(soundId.toString()), target.getDisplayName()), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.playsound.success.multiple", Component.literal(soundId.toString()), targets.size()), true);
        }

        return targets.size();
    }

    private static Identifier getSoundId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Object sound = context.getArgument("sound", Object.class);
        if (sound instanceof Holder.Reference<?> reference) {
            return reference.key().identifier();
        }
        if (sound instanceof Identifier identifier) {
            return identifier;
        }
        if (sound instanceof SoundEvent event) {
            return event.location();
        }

        throw UNKNOWN_SOUND_ARGUMENT_EXCEPTION.create(sound.getClass().getName());
    }

    private static SoundSource getCategory(CommandContext<CommandSourceStack> context) {
        for (ParsedCommandNode<CommandSourceStack> parsedNode : context.getNodes()) {
            for (SoundSource category : SoundSource.values()) {
                if (parsedNode.getNode().getName().equals(category.getName())) {
                    return category;
                }
            }
        }

        return SoundSource.MASTER;
    }
}
