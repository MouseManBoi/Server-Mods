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
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.List;

public final class PlaySoundOffsetCommand {
    private static final DynamicCommandExceptionType CANNOT_SEND_EXCEPTION =
            new DynamicCommandExceptionType(player -> Text.literal("Cannot play an offset sound for " + player + " because their client does not have this mod's playsound channel."));
    private static final DynamicCommandExceptionType UNKNOWN_SOUND_ARGUMENT_EXCEPTION =
            new DynamicCommandExceptionType(type -> Text.literal("Unsupported playsound sound argument type: " + type));

    private PlaySoundOffsetCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        replaceMinVolumeArgument(dispatcher.getRoot());
    }

    private static void replaceMinVolumeArgument(CommandNode<ServerCommandSource> node) {
        List<CommandNode<ServerCommandSource>> children = List.copyOf(node.getChildren());
        for (CommandNode<ServerCommandSource> child : children) {
            if ("minVolume".equals(child.getName())) {
                removeChild(node, child.getName());
                node.addChild(createSecondsArgument(child));
            } else {
                replaceMinVolumeArgument(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeChild(CommandNode<ServerCommandSource> node, String name) {
        CommandNodeAccessor<ServerCommandSource> accessor = (CommandNodeAccessor<ServerCommandSource>) node;
        accessor.server_mods$getChildren().remove(name);
        accessor.server_mods$getArguments().remove(name);
        accessor.server_mods$getLiterals().remove(name);
    }

    private static ArgumentCommandNode<ServerCommandSource, Float> createSecondsArgument(CommandNode<ServerCommandSource> minVolumeNode) {
        return RequiredArgumentBuilder.<ServerCommandSource, Float>argument("seconds", FloatArgumentType.floatArg(0.0F))
                .requires(minVolumeNode.getRequirement())
                .executes(context -> execute(context, FloatArgumentType.getFloat(context, "seconds")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static int execute(CommandContext<ServerCommandSource> context, float seconds) throws CommandSyntaxException {
        Identifier soundId = getSoundId(context);
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
        Vec3d pos = Vec3ArgumentType.getVec3(context, "pos");
        float volume = context.getArgument("volume", Float.class);
        float pitch = context.getArgument("pitch", Float.class);
        PlaySoundOffsetPayload payload = new PlaySoundOffsetPayload(
                soundId,
                getCategory(context),
                pos,
                volume,
                pitch,
                context.getSource().getWorld().getRandom().nextLong(),
                Math.round(seconds)
        );

        for (ServerPlayerEntity target : targets) {
            if (!ServerPlayNetworking.canSend(target, PlaySoundOffsetPayload.ID)) {
                throw CANNOT_SEND_EXCEPTION.create(target.getName().getString());
            }
            ServerPlayNetworking.send(target, payload);
        }

        if (targets.size() == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            context.getSource().sendFeedback(() -> Text.translatable("commands.playsound.success.single", Text.literal(soundId.toString()), target.getDisplayName()), true);
        } else {
            context.getSource().sendFeedback(() -> Text.translatable("commands.playsound.success.multiple", Text.literal(soundId.toString()), targets.size()), true);
        }

        return targets.size();
    }

    private static Identifier getSoundId(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Object sound = context.getArgument("sound", Object.class);
        if (sound instanceof RegistryEntry.Reference<?> reference) {
            return reference.registryKey().getValue();
        }
        if (sound instanceof Identifier identifier) {
            return identifier;
        }
        if (sound instanceof SoundEvent event) {
            return event.id();
        }

        throw UNKNOWN_SOUND_ARGUMENT_EXCEPTION.create(sound.getClass().getName());
    }

    private static SoundCategory getCategory(CommandContext<ServerCommandSource> context) {
        for (ParsedCommandNode<ServerCommandSource> parsedNode : context.getNodes()) {
            for (SoundCategory category : SoundCategory.values()) {
                if (parsedNode.getNode().getName().equals(category.getName())) {
                    return category;
                }
            }
        }

        return SoundCategory.MASTER;
    }
}
