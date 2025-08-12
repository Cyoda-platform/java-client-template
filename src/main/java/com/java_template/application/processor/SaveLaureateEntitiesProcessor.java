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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SaveLaureateEntitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SaveLaureateEntitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SaveLaureateEntitiesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Saving laureate entities for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::saveEntities)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getErrorDetails() != null && !job.getErrorDetails().isEmpty();
    }

    private Job saveEntities(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String laureateJson = job.getErrorDetails(); // Using errorDetails temporarily for raw JSON
        try {
            JsonNode rootNode = objectMapper.readTree(laureateJson);
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                logger.error("Invalid laureate records JSON");
                job.setStatus("FAILED");
                job.setErrorDetails("Invalid laureate records JSON");
                return job;
            }

            List<Laureate> laureates = new ArrayList<>();
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                Laureate laureate = new Laureate();
                laureate.setLaureateId(fields.path("id").asText(null));
                laureate.setFirstname(fields.path("firstname").asText(null));
                laureate.setSurname(fields.path("surname").asText(null));
                laureate.setBorn(fields.path("born").asText(null));
                laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
                laureate.setBorncountry(fields.path("borncountry").asText(null));
                laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                laureate.setBorncity(fields.path("borncity").asText(null));
                laureate.setGender(fields.path("gender").asText(null));
                laureate.setYear(fields.path("year").asText(null));
                laureate.setCategory(fields.path("category").asText(null));
                laureate.setMotivation(fields.path("motivation").asText(null));
                laureate.setAffiliationName(fields.path("name").asText(null));
                laureate.setAffiliationCity(fields.path("city").asText(null));
                laureate.setAffiliationCountry(fields.path("country").asText(null));
                laureates.add(laureate);
            }

            // Persist laureates (in real app, use repository or service layer) - here, simulate by logging
            logger.info("Persisting {} laureates", laureates.size());

            // For demonstration, update job's resultSummary
            job.setResultSummary("Persisted " + laureates.size() + " laureates successfully");
            job.setStatus("SUCCEEDED");
            job.setErrorDetails(null);

        } catch (Exception e) {
            logger.error("Error parsing laureate JSON", e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
        }
        return job;
    }
}
