package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class CreateLaureateEntitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateLaureateEntitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public CreateLaureateEntitiesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job create laureate entities for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state for creating laureates")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        if (entity == null) {
            logger.error("Job entity null in CreateLaureateEntitiesProcessor");
            return false;
        }
        if (!"INGESTING".equalsIgnoreCase(entity.getStatus())) {
            logger.error("Job status not INGESTING in CreateLaureateEntitiesProcessor");
            return false;
        }
        if (entity.getDetails() == null || entity.getDetails().isEmpty()) {
            logger.error("Job details missing laureate data in CreateLaureateEntitiesProcessor");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String laureatesJson = job.getDetails();

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode recordsNode = mapper.readTree(laureatesJson);
            if (!recordsNode.isArray()) {
                logger.error("Laureates JSON is not an array");
                job.setStatus("FAILED");
                job.setDetails("Malformed laureates JSON data");
                return job;
            }

            for (JsonNode laureateNode : recordsNode) {
                Laureate laureate = new Laureate();
                // Extract fields with null checks
                laureate.setLaureateId(laureateNode.path("id").asInt(0));
                laureate.setFirstname(laureateNode.path("firstname").asText(null));
                laureate.setSurname(laureateNode.path("surname").asText(null));
                laureate.setGender(laureateNode.path("gender").asText(null));
                laureate.setBorn(laureateNode.path("born").asText(null));
                laureate.setDied(laureateNode.path("died").isNull() ? null : laureateNode.path("died").asText(null));
                laureate.setBorncountry(laureateNode.path("borncountry").asText(null));
                laureate.setBorncountrycode(laureateNode.path("borncountrycode").asText(null));
                laureate.setBorncity(laureateNode.path("borncity").asText(null));
                laureate.setYear(laureateNode.path("year").asText(null));
                laureate.setCategory(laureateNode.path("category").asText(null));
                laureate.setMotivation(laureateNode.path("motivation").asText(null));
                laureate.setAffiliationName(laureateNode.path("name").asText(null));
                laureate.setAffiliationCity(laureateNode.path("city").asText(null));
                laureate.setAffiliationCountry(laureateNode.path("country").asText(null));

                // Persist laureate entity asynchronously
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
                );
                idFuture.whenComplete((id, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to persist Laureate entity: {} {}", laureate.getFirstname(), laureate.getSurname(), ex);
                    } else {
                        logger.info("Persisted Laureate entity with ID: {}", id);
                    }
                });

                logger.info("Created Laureate entity: {} {}", laureate.getFirstname(), laureate.getSurname());
            }

            // Update job status to SUCCEEDED
            job.setStatus("SUCCEEDED");
            job.setDetails("Ingested " + recordsNode.size() + " laureates");

        } catch (Exception e) {
            logger.error("Exception processing laureates JSON", e);
            job.setStatus("FAILED");
            job.setDetails("Exception parsing laureates JSON: " + e.getMessage());
        }

        return job;
    }
}
