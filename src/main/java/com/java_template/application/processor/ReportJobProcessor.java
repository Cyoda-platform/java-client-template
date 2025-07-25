package com.java_template.application.processor;

import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.ConversionReport;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ReportJobProcessor implements CyodaProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final RestTemplate restTemplate;

    public ReportJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        log.info("ReportJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportJob.class)
                .validate(ReportJob::isValid, "Invalid ReportJob entity state")
                .map(this::processReportJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportJobProcessor".equals(modelSpec.operationName()) &&
                "reportJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ReportJob processReportJob(ReportJob job) {
        log.info("Processing ReportJob with ID: {}", job.getId());
        if (job.getRecipientEmail() == null || job.getRecipientEmail().isBlank()) {
            log.error("Recipient email is blank for ReportJob ID: {}", job.getId());
            job.setStatus("FAILED");
            job.setErrorMessage("Recipient email is missing or blank");
            try {
                entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception e) {
                log.error("Error updating ReportJob status to FAILED: {}", e.getMessage());
            }
            return job;
        }

        job.setStatus("PENDING"); // keep as PENDING until FETCHING
        try {
            job.setStatus("FETCHING");
            entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();

            // Fetch BTC/USD and BTC/EUR rates from CoinGecko API
            String url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur";
            Map<String, Map<String, Double>> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("bitcoin")) {
                throw new RuntimeException("Invalid response from BTC price API");
            }
            Map<String, Double> btcPrices = response.get("bitcoin");
            Double btcUsd = btcPrices.get("usd");
            Double btcEur = btcPrices.get("eur");
            if (btcUsd == null || btcEur == null) {
                throw new RuntimeException("BTC/USD or BTC/EUR rates missing in API response");
            }

            // Create ConversionReport entity
            ConversionReport report = new ConversionReport();
            report.setJobTechnicalId(job.getId());
            report.setCreatedTimestamp(LocalDateTime.now());
            report.setBtcUsdRate(BigDecimal.valueOf(btcUsd));
            report.setBtcEurRate(BigDecimal.valueOf(btcEur));
            report.setStatus("CREATED");
            report.setEmailSentTimestamp(null);

            entityService.addItem("ConversionReport", Config.ENTITY_VERSION, report).get();
            log.info("Created ConversionReport for Job ID: {}", job.getId());

            job.setStatus("FETCHING_COMPLETED");
            entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();

            // Send email (simulate email sending)
            boolean emailSent = sendEmailReport(job.getRecipientEmail(), report);

            if (emailSent) {
                report.setStatus("EMAILED");
                report.setEmailSentTimestamp(LocalDateTime.now());
                entityService.addItem("ConversionReport", Config.ENTITY_VERSION, report).get();

                job.setStatus("COMPLETED");
                job.setErrorMessage(null);
                entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();

                log.info("Email sent successfully for ReportJob ID: {}", job.getId());
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to send email");
                entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();
                log.error("Failed to send email for ReportJob ID: {}", job.getId());
            }

        } catch (Exception e) {
            log.error("Error processing ReportJob ID {}: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            try {
                entityService.addItem("ReportJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception ex) {
                log.error("Error updating ReportJob status to FAILED: {}", ex.getMessage());
            }
        }
        return job;
    }

    private boolean sendEmailReport(String recipientEmail, ConversionReport report) {
        // Simulate email sending logic here
        // In real implementation, use JavaMailSender or external email service API
        log.info("Sending email report to {} with BTC/USD: {}, BTC/EUR: {}", recipientEmail, report.getBtcUsdRate(), report.getBtcEurRate());
        return true; // Simulate success
    }
}
