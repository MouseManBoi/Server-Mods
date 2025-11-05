package net.baconeater.features.shaders;

import net.minecraft.util.Identifier;

public final class ShaderResolver {
    private ShaderResolver() {}
    /** "creeper" → minecraft:shaders/post/creeper.json ; "ns:name" → ns:shaders/post/name.json; full paths pass through. */
    public static Identifier toShaderPath(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        String ns, path;
        if (s.contains(":")) {
            String[] parts = s.split(":", 2);
            ns = parts[0]; path = parts[1];
            if (!path.contains("/")) path = "shaders/post/" + path + ".json";
            else if (!path.endsWith(".json")) path += ".json";
        } else {
            ns = "minecraft";
            path = s.contains("/") ? (s.endsWith(".json") ? s : s + ".json") : "shaders/post/" + s + ".json";
        }
        return Identifier.of(ns, path);
    }
}
