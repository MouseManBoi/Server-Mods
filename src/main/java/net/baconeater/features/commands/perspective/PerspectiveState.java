package net.baconeater.features.commands.perspective;

public enum PerspectiveState {
    FIRST("first"),
    SECOND("second"),
    THIRD("third");

    private final String commandName;

    PerspectiveState(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }
}