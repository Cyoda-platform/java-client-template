package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            normalizeEmail(entity);
            // Add more workflow orchestration steps here if needed
            return entity;
        });
    }

    private void normalizeEmail(ObjectNode entity) {
        if (entity.has("email") && entity.get("email").isTextual()) {
            String normalizedEmail = entity.get("email").asText().toLowerCase(Locale.ROOT);
            entity.put("email", normalizedEmail);
        }
    }
}