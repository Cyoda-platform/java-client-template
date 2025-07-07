package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Processor for normalizing pet status to lowercase.
 */
@Component("normalizestatus")
public class NormalizeStatus implements CyodaProcessor<Pet> {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeStatus.class);
    private final ObjectMapper objectMapper;

    public NormalizeStatus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Pet> process(Pet pet) {
        logger.info("normalizeStatus processor called");

        // Direct entity processing - much cleaner than JSON manipulation!
        pet.normalizeStatus();
        return CompletableFuture.completedFuture(pet);
    }

    @Override
    public Class<Pet> getEntityType() {
        return Pet.class;
    }
}
