package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for adding last modified timestamp to pet entity.
 */
@Component("addlastmodifiedtimestamp")
public class AddLastModifiedTimestamp implements CyodaProcessor<Pet> {

    private static final Logger logger = LoggerFactory.getLogger(AddLastModifiedTimestamp.class);
    private final ObjectMapper objectMapper;

    public AddLastModifiedTimestamp(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Pet> process(Pet pet) {
        logger.info("addLastModifiedTimestamp processor called");

        // Direct entity processing - much cleaner!
        pet.addLastModifiedTimestamp();
        return CompletableFuture.completedFuture(pet);
    }

    @Override
    public Class<Pet> getEntityType() {
        return Pet.class;
    }
}
