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

import java.util.List;
import java.util.ArrayList;

@Component
public class JobIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobId() != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic for ingestion
        job.setStatus("INGESTING");
        logger.info("Job {} status set to INGESTING", job.getJobId());

        // Simulate call to OpenDataSoft API and ingest laureates
        List<Laureate> laureates = new ArrayList<>();
        try {
            // Simulated data fetch - in real implementation, fetch from API
            // For demo, create dummy laureate
            Laureate laureate = new Laureate();
            laureate.setLaureateId("laureate_1");
            laureate.setFirstname("Akira");
            laureate.setSurname("Suzuki");
            laureate.setBorn("1930-09-12");
            laureate.setDied(null);
            laureate.setBorncountry("Japan");
            laureate.setBorncountrycode("JP");
            laureate.setBorncity("Mukawa");
            laureate.setGender("male");
            laureate.setYear("2010");
            laureate.setCategory("Chemistry");
            laureate.setMotivation("for palladium-catalyzed cross couplings in organic synthesis");
            laureate.setAffiliationName("Hokkaido University");
            laureate.setAffiliationCity("Sapporo");
            laureate.setAffiliationCountry("Japan");

            laureates.add(laureate);
            // In real scenario, would process each laureate and trigger further workflows

            job.setStatus("SUCCEEDED");
            logger.info("Job {} ingestion succeeded", job.getJobId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            logger.error("Job {} ingestion failed: {}", job.getJobId(), e.getMessage());
        }
        return job;
    }
}
