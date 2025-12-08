package net.baconeater.features.commands.perspective;

import net.minecraft.client.option.Perspective;

public enum PerspectiveState {
    FIRST("first", Perspective.FIRST_PERSON),
    SECOND("second", Perspective.THIRD_PERSON_BACK),
    THIRD("third", Perspective.THIRD_PERSON_FRONT);

    private final String commandName;
    private final Perspective clientPerspective;

    PerspectiveState(String commandName, Perspective clientPerspective) {
        this.commandName = commandName;
        this.clientPerspective = clientPerspective;
    }

    public String commandName() {
        return commandName;
    }

    public Perspective toClientPerspective() {
        return clientPerspective;
    }

    public static PerspectiveState fromClientPerspective(Perspective perspective) {
        for (PerspectiveState state : values()) {
            if (state.clientPerspective == perspective) {
                return state;
            }
        }
        // Default fallback
        return FIRST;
    }
}