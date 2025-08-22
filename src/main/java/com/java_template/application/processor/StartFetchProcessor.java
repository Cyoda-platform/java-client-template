package com.java_template.application.processor;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetSyncJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetSyncJob.class)
            .validate(this::isValidEntity, "Invalid PetSyncJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetSyncJob entity) {
        return entity != null && entity.isValid();
    }

    private PetSyncJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetSyncJob> context) {
        PetSyncJob job = context.entity();
        if (job == null) {
            logger.warn("Received null PetSyncJob in execution context");
            return null;
        }

        // Ensure fetchedCount is initialized
        if (job.getFetchedCount() == null) {
            job.setFetchedCount(0);
        }

        // Set start time if not already present
        if (job.getStartTime() == null || job.getStartTime().isBlank()) {
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            job.setStartTime(now);
        }

        // Basic config validation: expect a sourceUrl (or at least source) in config
        Map<String, Object> config = job.getConfig();
        if (config == null) {
            logger.error("PetSyncJob {} has null config - marking as FAILED", job.getId());
            job.setStatus("FAILED");
            job.setErrorMessage("Missing config for PetSyncJob");
            job.setEndTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            return job;
        }

        // Try to find a sourceUrl in config
        Object sourceUrlObj = config.get("sourceUrl");
        String sourceUrl = sourceUrlObj != null ? String.valueOf(sourceUrlObj) : null;

        if (sourceUrl == null || sourceUrl.isBlank()) {
            // If no sourceUrl, we still may have a logical source name
            Object sourceObj = config.get("source");
            String sourceCfg = sourceObj != null ? String.valueOf(sourceObj) : null;
            if ((sourceCfg == null || sourceCfg.isBlank()) && (job.getSource() == null || job.getSource().isBlank())) {
                logger.error("PetSyncJob {} missing sourceUrl and source config - marking as FAILED", job.getId());
                job.setStatus("FAILED");
                job.setErrorMessage("Missing sourceUrl or source in config");
                job.setEndTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                return job;
            } else {
                // Set source if provided in config but not on job
                if ((job.getSource() == null || job.getSource().isBlank()) && sourceCfg != null && !sourceCfg.isBlank()) {
                    job.setSource(sourceCfg);
                }
                // We can proceed to fetching state even without explicit URL (assume connector by source)
            }
        } else {
            // If sourceUrl present and source missing, populate source by URL host if possible
            if (job.getSource() == null || job.getSource().isBlank()) {
                job.setSource(extractSourceFromUrl(sourceUrl));
            }
        }

        // Mark job as FETCHING and reset error message
        job.setStatus("FETCHING");
        job.setErrorMessage(null);

        // At this stage the orchestration system should route to the next processors (parsing) after external fetch.
        // This processor solely updates the orchestration entity state to indicate work started.
        logger.info("PetSyncJob {} set to FETCHING (source: {}, sourceUrl: {})", job.getId(), job.getSource(), sourceUrl);

        return job;
    }

    private String extractSourceFromUrl(String url) {
        if (url == null) return null;
        try {
            String cleaned = url.trim();
            // naive extraction: host from URL
            java.net.URI uri = new java.net.URI(cleaned);
            String host = uri.getHost();
            if (host == null) return null;
            // use host without www.
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception ex) {
            logger.debug("Failed to parse source from url '{}': {}", url, ex.getMessage());
            return null;
        }
    }
}