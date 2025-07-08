package com.java_template.processor;

import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.ModelKey;
import com.java_template.entity.pet.Pet;
import com.java_template.serializer.ProcessorRequestSerializer;
import org.cyoda.cloud.api.event.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("addlastmodifiedtimestamp")
public class AddLastModifiedTimestamp implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddLastModifiedTimestamp.class);
    private final ProcessorRequestSerializer serializer;

    public AddLastModifiedTimestamp(ProcessorRequestSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public CompletableFuture<EntityProcessorCalculationResponse> process(EntityProcessorCalculationRequest request) {
        logger.info("addLastModifiedTimestamp processor called");

        return CompletableFuture.supplyAsync(() -> {
            // Option 1: Type-safe extraction when we know it's a Pet
            Pet pet = serializer.extractEntity(request, Pet.class);
            pet.addLastModifiedTimestamp();
            return serializer.createSuccessResponse(request, pet);
        });
    }

    @Override
    public boolean supports(ModelKey modelKey) {
        // This processor supports Pet entities of version
        return "pet".equals(modelKey.modelName()) && ENTITY_VERSION.equals(modelKey.modelVersion());
    }

    @Override
    public String getName() {
        return "addlastmodifiedtimestamp";
    }
}
