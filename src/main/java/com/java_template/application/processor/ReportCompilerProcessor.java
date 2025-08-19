package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReportCompilerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportCompilerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportCompilerProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportCompiler for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        try {
            // For prototype assume report created in previous step; update its status to READY
            // Find a report by createdFromJobId - naive approach for prototype
            CompletableFuture<ArrayNode> reportsFuture = entityService.getItemsByCondition(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), null, true);
            ArrayNode reports = reportsFuture.get();
            if (reports != null) {
                for (com.fasterxml.jackson.databind.JsonNode rnode : reports) {
                    if (rnode.has("createdFromJobId") && rnode.get("createdFromJobId").asText().equals(job.getJobId())) {
                        try {
                            Report r = new Report();
                            if (rnode.has("technicalId")) r.setTechnicalId(rnode.get("technicalId").asText());
                            if (rnode.has("reportId")) r.setReportId(rnode.get("reportId").asText());
                            r.setStatus("READY");
                            CompletableFuture<UUID> updated = entityService.updateItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), UUID.fromString(r.getTechnicalId()), r);
                            updated.get();
                            logger.info("Marked report {} as READY for jobId={}", r.getReportId(), job.getJobId());
                        } catch (Exception ex) {
                            logger.warn("Failed to mark report as READY for jobId={}: {}", job.getJobId(), ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error compiling report for jobId={}", job.getJobId(), e);
        }
        return job;
    }
}
