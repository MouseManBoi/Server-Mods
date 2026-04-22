package net.baconeater.features.commands.shader;

import java.util.Arrays;

public enum ShaderState {
    NONE("none"),
    IN("in"),
    OUT("out");

    private final String commandName;

    ShaderState(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public boolean isSpecified() {
        return this != NONE;
    }

    public static ShaderState fromCommandName(String value) {
        return Arrays.stream(values())
                .filter(state -> state.commandName.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown shader state: " + value));
    }
}
