package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReportGeneratorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportGeneratorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportGenerator for request: {}", request.getId());

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
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                logger.warn("No products to include in report jobId={}", job.getJobId());
                return job;
            }

            Report report = new Report();
            report.setReportId("report_" + java.util.UUID.randomUUID().toString());
            report.setCreatedFromJobId(job.getJobId());
            report.setGeneratedAt(OffsetDateTime.now().toString());
            report.setStatus("COMPILING");
            report.setRecipients(job.getRecipients());

            // Build simple summary metrics: top sellers and low movers
            ArrayList<ObjectNode> topSellers = new ArrayList<>();
            ArrayList<ObjectNode> lowMovers = new ArrayList<>();

            Iterator<com.fasterxml.jackson.databind.JsonNode> it = items.elements();
            while (it.hasNext()) {
                ObjectNode node = (ObjectNode) it.next();
                Product p = new Product();
                if (node.has("productId")) p.setProductId(node.get("productId").asText());
                if (node.has("technicalId")) p.setTechnicalId(node.get("technicalId").asText());
                if (node.has("metrics") && node.get("metrics").has("salesVolume")) {
                    double sv = node.get("metrics").get("salesVolume").asDouble(0.0);
                    ObjectNode rec = mapper.createObjectNode();
                    rec.put("productId", p.getProductId());
                    rec.put("salesVolume", sv);
                    if (sv > 10) topSellers.add(rec);
                    if (sv < 1) lowMovers.add(rec);
                }
            }

            ObjectNode summary = mapper.createObjectNode();
            summary.putPOJO("topSellers", topSellers);
            summary.putPOJO("lowMovers", lowMovers);
            summary.putPOJO("restockCandidates", new ArrayList<>());
            report.setSummaryMetrics(mapper.convertValue(summary, java.util.Map.class));

            // Attachments: minimal prototype - JSON summary
            ArrayList<Report.Attachment> attachments = new ArrayList<>();
            Report.Attachment a = new Report.Attachment();
            a.setType("JSON");
            a.setFilename(report.getReportId() + "_summary.json");
            a.setUrl("internal://reports/" + a.getFilename());
            attachments.add(a);
            report.setAttachments(attachments);

            // Persist report
            CompletableFuture<UUID> idFuture = entityService.addItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), report);
            idFuture.get();
            logger.info("Persisted report reportId={} jobId={}", report.getReportId(), job.getJobId());

        } catch (Exception e) {
            logger.error("Error generating report for jobId={}", job.getJobId(), e);
        }
        return job;
    }
}
