package com.java_template.entity.petfetchrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * No-operation processor that returns entity unchanged.
 */
@Component("noop")
public class Noop implements CyodaProcessor<PetFetchRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Noop.class);

    @Override
    public CompletableFuture<PetFetchRequest> process(PetFetchRequest entity) {
        logger.info("noop processor called");
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public Class<PetFetchRequest> getEntityType() {
        return PetFetchRequest.class;
    }
}
