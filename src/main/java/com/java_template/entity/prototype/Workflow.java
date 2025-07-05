package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("prototype")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Condition function: returns true if entity is not null
    public boolean validateEntity(ObjectNode entity) {
        boolean valid = entity != null;
        logger.info("validateEntity check: {}", valid);
        return valid;
    }

    // Condition function: logical negation of validateEntity
    public boolean not_validateEntity(ObjectNode entity) {
        boolean invalid = !validateEntity(entity);
        logger.info("not_validateEntity check: {}", invalid);
        return invalid;
    }

    // Action function: enrich entity by adding/modifying fields synchronously
    public CompletableFuture<ObjectNode> enrichEntity(ObjectNode entity) {
        logger.info("enrichEntity action started");
        entity.put("processedTimestamp", System.currentTimeMillis());
        return CompletableFuture.completedFuture(entity);
    }

    // Async action function: fetch supplementary data and merge into entity
    public CompletableFuture<ObjectNode> fetchSupplementaryData(ObjectNode entity) {
        logger.info("fetchSupplementaryData action started");
        UUID supplementaryId = UUID.randomUUID(); // fake id for example

        return entityService.getItem("supplementaryModel", ENTITY_VERSION, supplementaryId)
                .handle((supplementaryData, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to fetch supplementary data for entity {}, continuing without it", supplementaryId, ex);
                    } else if (supplementaryData != null) {
                        entity.set("supplementaryData", supplementaryData);
                    }
                    return entity;
                });
    }
}