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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting Laureate ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid job state")
            .map(this::ingestLaureatesFromJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getSourceUrl() != null && !job.getSourceUrl().isEmpty()
            && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job ingestLaureatesFromJob(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simulate fetching laureates data from job.sourceUrl
        // In real implementation, use HTTP client like RestTemplate or WebClient

        logger.info("Ingesting laureates from source URL: {}", job.getSourceUrl());

        // For illustration, simulate ingestion of laureates
        List<Laureate> laureates = new ArrayList<>();
        // Here you would parse JSON response and create Laureate entities
        // Example dummy laureate creation
        Laureate laureate = new Laureate();
        laureate.setLaureateId("853");
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
        laureate.setIngestedAt(ZonedDateTime.now());

        laureates.add(laureate);

        // TODO: Persist laureates or send events for downstream processing
        logger.info("Ingested {} laureates", laureates.size());

        // Update job status and finishedAt timestamp
        job.setStatus("SUCCEEDED");
        job.setFinishedAt(ZonedDateTime.now());

        return job;
    }
}
