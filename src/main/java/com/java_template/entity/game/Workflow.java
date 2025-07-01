package com.java_template.entity.game;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    // Entry point for workflow orchestration - no business logic here
    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            normalizeScores(entity);
            // Potentially more workflow orchestration steps can be added here
            return entity;
        });
    }

    // Business logic: normalize homeScore and awayScore fields
    private void normalizeScores(ObjectNode entity) {
        if (!entity.has("homeScore") || entity.get("homeScore").isNull()) {
            entity.putNull("homeScore");
        }
        if (!entity.has("awayScore") || entity.get("awayScore").isNull()) {
            entity.putNull("awayScore");
        }
    }
}