package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.AbstractWorkflowHandler;
import com.java_template.common.workflow.WorkflowMethod;
import com.java_template.common.workflow.entity.PetEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("pet")
public class Workflow extends AbstractWorkflowHandler<PetEntity> {

    @Override
    public String getEntityType() {
        return "pet";
    }

    @Override
    public PetEntity createEntity(Object data) {
        if (data instanceof ObjectNode) {
            return new PetEntity((ObjectNode) data);
        }
        throw new IllegalArgumentException("Expected ObjectNode for Pet entity creation");
    }

    @Override
    protected Class<PetEntity> getEntityClass() {
        return PetEntity.class;
    }

    // Workflow methods - automatically discovered via @WorkflowMethod annotation
    @WorkflowMethod(description = "Normalizes the pet status to lowercase")
    public CompletableFuture<PetEntity> normalizeStatus(PetEntity petEntity) {
        logger.info("normalizeStatus called");
        petEntity.normalizeStatus();
        return CompletableFuture.completedFuture(petEntity);
    }

    @WorkflowMethod(description = "Adds a timestamp indicating when the pet was last modified")
    public CompletableFuture<PetEntity> addLastModifiedTimestamp(PetEntity petEntity) {
        logger.info("addLastModifiedTimestamp called");
        petEntity.addLastModifiedTimestamp();
        return CompletableFuture.completedFuture(petEntity);
    }

    // Condition methods (for backward compatibility with existing workflow definitions)
    public boolean isStatusPresent(PetEntity petEntity) {
        boolean present = petEntity.hasStatus();
        logger.info("isStatusPresent: {}", present);
        return present;
    }

    public boolean alwaysTrue(PetEntity petEntity) {
        logger.info("alwaysTrue called: returning true");
        return true;
    }

    // Legacy methods for backward compatibility with existing registrar
    public CompletableFuture<ObjectNode> normalizeStatus(ObjectNode petEntity) {
        logger.info("Legacy normalizeStatus called");
        PetEntity entity = new PetEntity(petEntity);
        return normalizeStatus(entity).thenApply(PetEntity::toObjectNode);
    }

    public CompletableFuture<ObjectNode> addLastModifiedTimestamp(ObjectNode petEntity) {
        logger.info("Legacy addLastModifiedTimestamp called");
        PetEntity entity = new PetEntity(petEntity);
        return addLastModifiedTimestamp(entity).thenApply(PetEntity::toObjectNode);
    }

    public boolean isStatusPresent(ObjectNode petEntity) {
        PetEntity entity = new PetEntity(petEntity);
        return isStatusPresent(entity);
    }

    public boolean alwaysTrue(ObjectNode petEntity) {
        logger.info("Legacy alwaysTrue called: returning true");
        return true;
    }
}