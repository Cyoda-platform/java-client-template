package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionschedule.version_1.ExtractionSchedule;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchFromPetStoreProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchFromPetStoreProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FetchFromPetStoreProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Fetching data from PetStore for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionSchedule.class)
            .validate(this::isValidEntity, "Invalid ExtractionSchedule state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionSchedule entity) {
        return entity != null && entity.isValid();
    }

    private ExtractionSchedule processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionSchedule> context) {
        ExtractionSchedule schedule = context.entity();
        try {
            // Simulate fetching from PetStore API. In the prototype we will query existing Product items as a stand-in.
            // For each product item found, create a snapshot and call SaveProductSnapshotProcessor logic by updating product sales_history

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                SearchConditionRequest.group("AND", Condition.of("$.store_id", "IEQUALS", schedule.getSchedule_id())),
                true
            );

            ArrayNode items = itemsFuture.get();
            if (items == null || items.isEmpty()) {
                logger.info("No products found to fetch for schedule {}", schedule.getSchedule_id());
            } else {
                Iterator<com.fasterxml.jackson.databind.JsonNode> it = items.elements();
                while (it.hasNext()) {
                    ObjectNode productNode = (ObjectNode) it.next();
                    // For prototype: simply append a synthetic sales snapshot to sales_history
                    // Real implementation would parse API response and call SaveProductSnapshotProcessor
                    logger.info("Processing product snapshot for product_id={}", productNode.get("product_id").asText());
                    // Here we would trigger SaveProductSnapshotProcessor; for prototype we leave as a no-op
                }
            }

            // Reschedule and set last_run on success
            schedule.setLast_run(Instant.now().toString());
            schedule.setStatus("COMPLETED");

            logger.info("Fetch completed for schedule {}. Marked COMPLETED", schedule.getSchedule_id());
        } catch (Exception ex) {
            logger.error("Error fetching from PetStore for schedule {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
            schedule.setStatus("FAILED");
            // Create incident and notify would be separate processors invoked by the workflow
        }
        return schedule;
    }
}
