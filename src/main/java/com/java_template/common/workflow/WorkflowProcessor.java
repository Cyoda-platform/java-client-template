package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class WorkflowProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowProcessor.class);

    private final WorkflowFactory workflowFactory;

    public WorkflowProcessor(WorkflowFactory workflowFactory) {
        this.workflowFactory = workflowFactory;
        logger.info("WorkflowProcessor initialized with factory containing {} methods",
                   workflowFactory.getRegisteredMethods().length);
    }

    public CompletableFuture<ObjectNode> processEvent(String eventName, ObjectNode payload) {
        Function<ObjectNode, CompletableFuture<ObjectNode>> workflowMethod = workflowFactory.getMethod(eventName);

        if (workflowMethod == null) {
            logger.warn("No workflow method found: {}", eventName);
            payload.put("success", false);
            return CompletableFuture.completedFuture(payload);
        }

        return workflowMethod.apply(payload);
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use WorkflowFactory directly instead
     */
    @Deprecated
    public void register(String methodKey, Function<ObjectNode, CompletableFuture<ObjectNode>> fn) {
        logger.warn("Deprecated register method called for: {}. Use WorkflowFactory instead.", methodKey);
    }
}