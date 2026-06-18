package net.baconeater.features.keybinds;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Maintains per-key objectives and sets THIS PLAYER'S score to 1 on press. */
public final class KeybindScoreHandler {
    private KeybindScoreHandler() {}

    // Objectives EXACTLY as requested
    public static final String OBJ_TOGGLE = "customUniversal.ToggleTrigger"; // R
    public static final String OBJ_MOVE1  = "customUniversal.Move1Trigger";  // Z
    public static final String OBJ_MOVE2  = "customUniversal.Move2Trigger";  // X
    public static final String OBJ_MOVE3  = "customUniversal.Move3Trigger";  // C
    public static final String OBJ_MOVE4  = "customUniversal.Move4Trigger";  // V

    public static void handle(ServerScoreboard sb, ServerPlayer player, int action) {
        // Ensure objectives exist (dummy criterion)
        Objective oToggle = ensure(sb, OBJ_TOGGLE, "Toggle Trigger");
        Objective oM1     = ensure(sb, OBJ_MOVE1,  "Move1 Trigger");
        Objective oM2     = ensure(sb, OBJ_MOVE2,  "Move2 Trigger");
        Objective oM3     = ensure(sb, OBJ_MOVE3,  "Move3 Trigger");
        Objective oM4     = ensure(sb, OBJ_MOVE4,  "Move4 Trigger");

        // Map action -> objective
        Objective target = switch (action) {
            case 0 -> oToggle; // R
            case 1 -> oM1;     // Z
            case 2 -> oM2;     // X
            case 3 -> oM3;     // C
            case 4 -> oM4;     // V
            default -> null;
        };
        if (target == null) return;

        // Set THIS PLAYER'S score to 1 (visible with /scoreboard)
        ScoreAccess access = sb.getOrCreatePlayerScore(ScoreHolder.fromGameProfile(player.getGameProfile()), target);
        access.set(1);

        // If you'd rather count presses, use:
        // access.setScore(access.getScore() + 1);
    }

    private static Objective ensure(ServerScoreboard sb, String name, String display) {
        Objective obj = sb.getObjective(name);
        if (obj == null) {
            obj = sb.addObjective(
                    name,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(display),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }
        return obj;
    }
}
