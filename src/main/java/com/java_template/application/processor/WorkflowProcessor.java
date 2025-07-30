package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.DataDownload;
import com.java_template.application.entity.ReportEmail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public WorkflowProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Workflow.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Workflow entity) {
        return entity != null && entity.isValid();
    }

    private Workflow processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Workflow> context) {
        Workflow workflow = context.entity();
        EntityProcessorCalculationRequest request = context.request();

        // Business logic from prototype's processWorkflow method adapted here
        try {
            workflow.setStatus("PROCESSING");

            if (workflow.getUrl() == null || workflow.getUrl().isBlank()) {
                workflow.setStatus("FAILED");
                logger.error("Workflow validation failed: URL is blank");
                return workflow;
            }
            if (workflow.getSubscribers() == null || workflow.getSubscribers().isEmpty()) {
                workflow.setStatus("FAILED");
                logger.error("Workflow validation failed: Subscribers list is empty");
                return workflow;
            }

            // Create DataDownload entity
            DataDownload dataDownload = new DataDownload();
            dataDownload.setWorkflowTechnicalId(request.getEntityId());
            dataDownload.setDownloadUrl(workflow.getUrl());
            dataDownload.setStatus("PENDING");

            CompletableFuture<UUID> dataDownloadIdFuture = entityService.addItem(
                    DataDownload.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    dataDownload
            );
            UUID dataDownloadId = dataDownloadIdFuture.get();
            logger.info("DataDownload created with technicalId: {}", dataDownloadId);

            // Trigger processing of DataDownload
            processDataDownload(dataDownloadId, dataDownload);

        } catch (Exception e) {
            logger.error("Error processing Workflow: {}", e.getMessage());
        }

        return workflow;
    }

    private void processDataDownload(UUID technicalId, DataDownload dataDownload) {
        logger.info("Processing DataDownload with technicalId: {}", technicalId);
        try {
            dataDownload.setStatus("PROCESSING");

            // Simulate download content
            String csvData = "id,address,price\n1,123 A St,100000\n2,456 B Ave,150000\n";

            dataDownload.setDataContent(csvData);
            dataDownload.setStatus("SUCCESS");
            dataDownload.setTimestamp(Instant.now().toString());

            // Analyze data and generate report string
            String report = "Report Summary: 2 entries, average price = 125000";

            // Update Workflow with report
            UUID workflowTechnicalId = UUID.fromString(dataDownload.getWorkflowTechnicalId());
            CompletableFuture<ObjectNode> workflowNodeFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    workflowTechnicalId
            );
            ObjectNode workflowNode = workflowNodeFuture.get();
            if (workflowNode != null) {
                Workflow workflow = objectMapper.treeToValue(workflowNode, Workflow.class);
                if (workflow != null) {
                    workflow.setReport(report);
                    workflow.setStatus("COMPLETED");

                    List<String> subscribers = workflow.getSubscribers() != null ? workflow.getSubscribers() : Collections.emptyList();
                    for (String email : subscribers) {
                        ReportEmail reportEmail = new ReportEmail();
                        reportEmail.setWorkflowTechnicalId(dataDownload.getWorkflowTechnicalId());
                        reportEmail.setEmailTo(email);
                        reportEmail.setEmailContent(report);
                        reportEmail.setStatus("PENDING");
                        reportEmail.setTimestamp(null);

                        CompletableFuture<UUID> reportEmailIdFuture = entityService.addItem(
                                ReportEmail.ENTITY_NAME,
                                com.java_template.common.config.Config.ENTITY_VERSION,
                                reportEmail
                        );
                        UUID reportEmailId = reportEmailIdFuture.get();
                        logger.info("ReportEmail created for subscriber: {} with technicalId: {}", email, reportEmailId);

                        // Trigger sending email
                        processReportEmail(reportEmailId, reportEmail);
                    }
                }
            }

        } catch (Exception e) {
            dataDownload.setStatus("FAILED");
            logger.error("DataDownload processing failed: {}", e.getMessage());

            try {
                UUID workflowTechnicalId = UUID.fromString(dataDownload.getWorkflowTechnicalId());
                CompletableFuture<ObjectNode> workflowNodeFuture = entityService.getItem(
                        Workflow.ENTITY_NAME,
                        com.java_template.common.config.Config.ENTITY_VERSION,
                        workflowTechnicalId
                );
                ObjectNode workflowNode = workflowNodeFuture.get();
                if (workflowNode != null) {
                    Workflow workflow = objectMapper.treeToValue(workflowNode, Workflow.class);
                    if (workflow != null) {
                        workflow.setStatus("FAILED");
                    }
                }
            } catch (Exception ex) {
                logger.error("Error updating Workflow status to FAILED: {}", ex.getMessage());
            }
        }
    }

    private void processReportEmail(UUID technicalId, ReportEmail reportEmail) {
        logger.info("Processing ReportEmail with technicalId: {}", technicalId);
        try {
            reportEmail.setStatus("SENDING");

            // Simulate sending email
            logger.info("Sending email to: {}", reportEmail.getEmailTo());

            reportEmail.setStatus("SENT");
            reportEmail.setTimestamp(Instant.now().toString());

            logger.info("Email sent successfully to: {}", reportEmail.getEmailTo());
        } catch (Exception e) {
            reportEmail.setStatus("FAILED");
            logger.error("Failed to send email to {}: {}", reportEmail.getEmailTo(), e.getMessage());
        }
    }

}
