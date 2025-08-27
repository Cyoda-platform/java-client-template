package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class StartJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public StartJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        logger.info("StartJobProcessor handling jobId={} currentStatus={}", job.getJobId(), job.getStatus());

        // Ensure requestedAt is present; if absent set to now
        if (job.getRequestedAt() == null || job.getRequestedAt().isBlank()) {
            job.setRequestedAt(Instant.now().toString());
            logger.debug("requestedAt was missing; set to now for jobId={}", job.getJobId());
        }

        // Basic validation of filter criteria: price range and date range consistency.
        ReportJob.FilterCriteria fc = job.getFilterCriteria();
        if (fc != null) {
            Double min = fc.getMinPrice();
            Double max = fc.getMaxPrice();
            if (min != null && max != null && min > max) {
                job.setStatus("FAILED");
                logger.warn("ReportJob {} failed: minPrice > maxPrice ({} > {})", job.getJobId(), min, max);
                return job;
            }

            ReportJob.DateRange dr = fc.getDateRange();
            if (dr != null) {
                String from = dr.getFrom();
                String to = dr.getTo();
                if ((from == null || from.isBlank()) || (to == null || to.isBlank())) {
                    job.setStatus("FAILED");
                    logger.warn("ReportJob {} failed: dateRange contains blank from/to", job.getJobId());
                    return job;
                }
                try {
                    LocalDate fromDate = LocalDate.parse(from);
                    LocalDate toDate = LocalDate.parse(to);
                    if (fromDate.isAfter(toDate)) {
                        job.setStatus("FAILED");
                        logger.warn("ReportJob {} failed: dateRange from is after to ({} > {})", job.getJobId(), from, to);
                        return job;
                    }
                } catch (DateTimeParseException ex) {
                    job.setStatus("FAILED");
                    logger.warn("ReportJob {} failed: invalid date format in dateRange from={} to={}", job.getJobId(), from, to);
                    return job;
                }
            }
        }

        // Move job to PROCESSING if it is in CREATED state or not yet processing.
        String status = job.getStatus();
        if (status == null || status.isBlank() || "CREATED".equalsIgnoreCase(status)) {
            job.setStatus("PROCESSING");
            logger.info("ReportJob {} transitioned to PROCESSING", job.getJobId());
            // Note: Do NOT call update on this entity. Cyoda will persist changes automatically.
            // Subsequent processors (FetchBookingsProcessor, etc.) should be triggered by this state change.
        } else {
            logger.info("ReportJob {} left in state {} (no transition performed)", job.getJobId(), status);
        }

        return job;
    }
}