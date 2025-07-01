package com.java_template.entity.game;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            normalizeScores(entity);
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> normalizeScores(ObjectNode entity) {
        if (!entity.has("homeScore") || entity.get("homeScore").isNull()) {
            entity.putNull("homeScore");
        }
        if (!entity.has("awayScore") || entity.get("awayScore").isNull()) {
            entity.putNull("awayScore");
        }
        return CompletableFuture.completedFuture(entity);
    }
}