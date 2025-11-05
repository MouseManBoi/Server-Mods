package net.baconeater.features.keybinds;

import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Maintains per-key objectives and sets THIS PLAYER'S score to 1 on press. */
public final class ScoreHandler {
    private ScoreHandler() {}

    // Objectives EXACTLY as requested
    public static final String OBJ_TOGGLE = "customUniversal.ToggleTrigger"; // R
    public static final String OBJ_MOVE1  = "customUniversal.Move1Trigger";  // Z
    public static final String OBJ_MOVE2  = "customUniversal.Move2Trigger";  // X
    public static final String OBJ_MOVE3  = "customUniversal.Move3Trigger";  // C
    public static final String OBJ_MOVE4  = "customUniversal.Move4Trigger";  // V

    public static void handle(ServerScoreboard sb, ServerPlayerEntity player, int action) {
        // Ensure objectives exist (dummy criterion)
        ScoreboardObjective oToggle = ensure(sb, OBJ_TOGGLE, "Toggle Trigger");
        ScoreboardObjective oM1     = ensure(sb, OBJ_MOVE1,  "Move1 Trigger");
        ScoreboardObjective oM2     = ensure(sb, OBJ_MOVE2,  "Move2 Trigger");
        ScoreboardObjective oM3     = ensure(sb, OBJ_MOVE3,  "Move3 Trigger");
        ScoreboardObjective oM4     = ensure(sb, OBJ_MOVE4,  "Move4 Trigger");

        // Map action -> objective
        ScoreboardObjective target = switch (action) {
            case 0 -> oToggle; // R
            case 1 -> oM1;     // Z
            case 2 -> oM2;     // X
            case 3 -> oM3;     // C
            case 4 -> oM4;     // V
            default -> null;
        };
        if (target == null) return;

        // Set THIS PLAYER'S score to 1 (visible with /scoreboard)
        ScoreAccess access = sb.getOrCreateScore(player, target, true);
        access.setScore(1);

        // If you'd rather count presses, use:
        // access.setScore(access.getScore() + 1);
    }

    private static ScoreboardObjective ensure(ServerScoreboard sb, String name, String display) {
        ScoreboardObjective obj = sb.getNullableObjective(name);
        if (obj == null) {
            obj = sb.addObjective(
                    name,
                    ScoreboardCriterion.DUMMY,
                    Text.literal(display),
                    ScoreboardCriterion.RenderType.INTEGER,
                    false,
                    null
            );
        }
        return obj;
    }
}
