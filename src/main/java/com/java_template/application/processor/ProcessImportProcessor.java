package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public ProcessImportProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            JsonNode payload = job.getPayload();
            if (payload == null || !payload.isArray()) {
                job.setStatus("Failed");
                job.setErrorMessage("Invalid payload format");
                return job;
            }

            ArrayNode array = (ArrayNode) payload;
            Iterator<JsonNode> it = array.elements();
            while (it.hasNext()) {
                JsonNode node = it.next();
                String type = node.has("type") ? node.get("type").asText() : null;
                if ("product".equalsIgnoreCase(type)) {
                    Product p = objectMapper.treeToValue(node.get("data"), Product.class);
                    // add product via entityService
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        objectMapper.valueToTree(p)
                    );
                    idFuture.get(5, TimeUnit.SECONDS);
                } else if ("user".equalsIgnoreCase(type)) {
                    User u = objectMapper.treeToValue(node.get("data"), User.class);
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        User.ENTITY_NAME,
                        String.valueOf(User.ENTITY_VERSION),
                        objectMapper.valueToTree(u)
                    );
                    idFuture.get(5, TimeUnit.SECONDS);
                } else {
                    logger.warn("Unknown import type: {}", type);
                }
            }

            job.setStatus("Completed");
            return job;
        } catch (Exception e) {
            logger.error("Error processing import job: {}", e.getMessage(), e);
            job.setStatus("Failed");
            job.setErrorMessage(e.getMessage());
            return job;
        }
    }
}
