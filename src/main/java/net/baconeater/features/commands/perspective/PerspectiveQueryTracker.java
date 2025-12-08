package net.baconeater.features.commands.perspective;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PerspectiveQueryTracker {
    private static final Map<UUID, QueryInfo> QUERIES = new ConcurrentHashMap<>();

    private PerspectiveQueryTracker() {
    }

    public static UUID register(ServerCommandSource source, String targetName) {
        UUID requestId = UUID.randomUUID();
        QUERIES.put(requestId, new QueryInfo(source, targetName));
        return requestId;
    }

    public static void handleResponse(UUID requestId, PerspectiveState state) {
        QueryInfo info = QUERIES.remove(requestId);
        if (info == null) {
            return;
        }
        info.source().sendFeedback(
                () -> Text.literal(info.targetName() + " is in " + state.commandName() + " perspective."),
                false);
    }

    private record QueryInfo(ServerCommandSource source, String targetName) {
    }
}