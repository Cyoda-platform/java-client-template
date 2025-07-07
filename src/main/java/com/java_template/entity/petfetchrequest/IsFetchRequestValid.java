package com.java_template.entity.petfetchrequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Processor for validating fetch request and setting the valid flag.
 */
@Component("isfetchrequestvalid")
public class IsFetchRequestValid implements CyodaProcessor<PetFetchRequest> {

    private static final Logger logger = LoggerFactory.getLogger(IsFetchRequestValid.class);
    private final ObjectMapper objectMapper;

    public IsFetchRequestValid(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<PetFetchRequest> process(PetFetchRequest entity) {
        logger.info("isFetchRequestValid processor called");

        // Direct entity processing - use the business logic method!
        entity.validateRequest();
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public Class<PetFetchRequest> getEntityType() {
        return PetFetchRequest.class;
    }
}
