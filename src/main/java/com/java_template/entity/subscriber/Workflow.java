package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component("subscriber")
@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> normalizeScores(ObjectNode entity) {
        if (entity.has("scores") && entity.get("scores").isArray()) {
            entity.withArray("scores").forEach(scoreNode -> {
                if (scoreNode.has("player") && scoreNode.get("player").isTextual()) {
                    String playerName = scoreNode.get("player").asText();
                    scoreNode = (ObjectNode) scoreNode;
                    scoreNode.put("player", playerName.trim().toUpperCase(Locale.ROOT));
                }
                if (scoreNode.has("points") && scoreNode.get("points").isInt()) {
                    int points = scoreNode.get("points").asInt();
                    if (points < 0) {
                        scoreNode.put("points", 0);
                    }
                }
            });
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        int totalPoints = 0;
        if (entity.has("scores") && entity.get("scores").isArray()) {
            for (var scoreNode : entity.withArray("scores")) {
                if (scoreNode.has("points") && scoreNode.get("points").isInt()) {
                    totalPoints += scoreNode.get("points").asInt();
                }
            }
        }
        entity.put("totalPoints", totalPoints);
        entity.put("status", "complete");
        return CompletableFuture.completedFuture(entity);
    }

}