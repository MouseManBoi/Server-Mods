package net.baconeater.mixin;

import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessor<S> {
    @Accessor("children")
    Map<String, CommandNode<S>> server_mods$getChildren();

    @Accessor("literals")
    Map<String, CommandNode<S>> server_mods$getLiterals();

    @Accessor("arguments")
    Map<String, CommandNode<S>> server_mods$getArguments();
}
