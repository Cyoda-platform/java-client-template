package com.java_template.entity.RetrieveData;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class RetrieveDataWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(RetrieveDataWorkflow.class);

    private final ObjectMapper objectMapper;

    // Workflow orchestration only: call individual processing steps in order
    public CompletableFuture<ObjectNode> processRetrieveData(ObjectNode entity) {
        return processStepOne(entity)
                .thenCompose(this::processStepTwo)
                .thenCompose(this::processStepThree);
    }

    // Example processing step 1: simulate data fetching
    private CompletableFuture<ObjectNode> processStepOne(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("processStepOne: starting data fetch simulation for entity {}", entity);
            try {
                Thread.sleep(1000); // simulate delay
                entity.put("fetchStatus", "completed");
                entity.put("stepOne", "done");
                logger.info("processStepOne: data fetch simulated successfully");
            } catch (InterruptedException e) {
                logger.error("processStepOne: interrupted", e);
            }
            return entity;
        });
    }

    // Example processing step 2: simulate data analysis
    private CompletableFuture<ObjectNode> processStepTwo(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("processStepTwo: starting data analysis simulation for entity {}", entity);
            try {
                Thread.sleep(1000); // simulate delay
                entity.put("analysisStatus", "completed");
                entity.put("stepTwo", "done");
                logger.info("processStepTwo: data analysis simulated successfully");
            } catch (InterruptedException e) {
                logger.error("processStepTwo: interrupted", e);
            }
            return entity;
        });
    }

    // Example processing step 3: finalize processing
    private CompletableFuture<ObjectNode> processStepThree(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("processStepThree: finalizing processing for entity {}", entity);
            try {
                Thread.sleep(1000); // simulate delay
                entity.put("status", "processed");
                entity.put("stepThree", "done");
                entity.put("entityVersion", ENTITY_VERSION);
                logger.info("processStepThree: processing finalized");
            } catch (InterruptedException e) {
                logger.error("processStepThree: interrupted", e);
            }
            return entity;
        });
    }
}