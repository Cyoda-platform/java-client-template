package com.java_template.application.processor;

import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.Report;
import com.java_template.application.entity.EmailReport;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReportJobProcessor implements CyodaProcessor {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public ReportJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ReportJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportJob.class)
                .validate(this::isValidEntity, "Invalid ReportJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();
        String technicalId = context.request().getEntityId();

        try {
            // Simulate fetching BTC/USD and BTC/EUR rates from external API
            BigDecimal fetchedBtcUsdRate = fetchBtcUsdRate();
            BigDecimal fetchedBtcEurRate = fetchBtcEurRate();
            OffsetDateTime now = OffsetDateTime.now();

            entity.setBtcUsdRate(fetchedBtcUsdRate);
            entity.setBtcEurRate(fetchedBtcEurRate);
            entity.setTimestamp(now);

            // Create immutable Report entity
            Report report = new Report();
            report.setReportJobId(technicalId);
            report.setBtcUsdRate(fetchedBtcUsdRate);
            report.setBtcEurRate(fetchedBtcEurRate);
            report.setTimestamp(now);

            // Add Report entity via EntityService
            try {
                CompletableFuture<UUID> reportIdFuture = entityService.addItem("Report", "1", report);
                UUID reportId = reportIdFuture.get();
                logger.info("Created Report with id {}", reportId.toString());
            } catch (Exception e) {
                logger.error("Failed to create Report entity", e);
            }

            // Create EmailReport entity to send email
            EmailReport emailReport = new EmailReport();
            emailReport.setReportJobId(technicalId);
            emailReport.setRecipient("recipient@example.com"); // hardcoded recipient for prototype
            emailReport.setSubject("Bitcoin Conversion Rate Report");
            emailReport.setBody(String.format(
                    "BTC/USD: %s\nBTC/EUR: %s\nTimestamp: %s",
                    fetchedBtcUsdRate.toPlainString(),
                    fetchedBtcEurRate.toPlainString(),
                    now.toString()
            ));
            emailReport.setStatus("PENDING");
            emailReport.setSentTimestamp(null);

            // Add EmailReport entity via EntityService
            UUID emailReportId;
            try {
                CompletableFuture<UUID> emailReportIdFuture = entityService.addItem("EmailReport", "1", emailReport);
                emailReportId = emailReportIdFuture.get();
                logger.info("Created EmailReport with id {}", emailReportId.toString());
            } catch (Exception e) {
                logger.error("Failed to create EmailReport entity", e);
                return entity;
            }

            // Process EmailReport to send email
            processEmailReport(emailReportId.toString(), emailReport, entity, technicalId);

        } catch (Exception e) {
            logger.error("Error processing ReportJob with id {}", technicalId, e);
            entity.setEmailStatus("FAILED");
        }

        return entity;
    }

    private BigDecimal fetchBtcUsdRate() {
        // For prototype, return fixed dummy value
        return new BigDecimal("30123.45");
    }

    private BigDecimal fetchBtcEurRate() {
        // For prototype, return fixed dummy value
        return new BigDecimal("27950.30");
    }

    private void processEmailReport(String emailReportId, EmailReport emailReport, ReportJob reportJob, String reportJobId) {
        try {
            // Simulate sending email
            logger.info("Sending email to {}", emailReport.getRecipient());

            // Mark email as SENT
            emailReport.setStatus("SENT");
            emailReport.setSentTimestamp(OffsetDateTime.now());

            // Update ReportJob emailStatus
            reportJob.setEmailStatus("SENT");

            logger.info("Email sent successfully to {}", emailReport.getRecipient());

        } catch (Exception e) {
            logger.error("Failed to send email for EmailReport id {}", emailReportId, e);
            emailReport.setStatus("FAILED");
            reportJob.setEmailStatus("FAILED");
        }
    }

}
