package com.java_template.application.processor;

import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.Report;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReportJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final com.java_template.common.service.EntityService entityService;

    public ReportJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        logger.info("ReportJobProcessor initialized with SerializerFactory, ObjectMapper and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportJob.class)
                .validate(ReportJob::isValid, "Invalid ReportJob entity")
                .map(this::processReportJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportJobProcessor".equals(modelSpec.operationName()) &&
                "reportJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ReportJob processReportJobLogic(ReportJob entity) {
        logger.info("Processing ReportJob with ID: {}", entity.getTechnicalId());

        // Step 1: Fetch BTC/USD and BTC/EUR rates from CoinGecko API
        String apiUrl = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur";
        double btcUsdRate;
        double btcEurRate;
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("Failed to fetch BTC rates, HTTP status: " + status);
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            String jsonResponse = content.toString();
            var root = objectMapper.readTree(jsonResponse);
            btcUsdRate = root.path("bitcoin").path("usd").asDouble();
            btcEurRate = root.path("bitcoin").path("eur").asDouble();

            if (btcUsdRate <= 0 || btcEurRate <= 0) {
                throw new RuntimeException("Invalid BTC rates retrieved");
            }
        } catch (Exception e) {
            logger.error("Error fetching BTC conversion rates for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            // We cannot update the entity in DB here, just set status on entity object
            throw new RuntimeException("Failed to fetch BTC conversion rates");
        }

        // Step 2: Save Report entity with fetched rates
        Report report = new Report();
        report.setJobTechnicalId(entity.getTechnicalId());
        report.setGeneratedAt(Instant.now());
        report.setBtcUsdRate(btcUsdRate);
        report.setBtcEurRate(btcEurRate);
        report.setEmailSent(false);

        try {
            CompletableFuture<UUID> reportIdFuture = entityService.addItem("Report", Config.ENTITY_VERSION, report);
            UUID reportId = reportIdFuture.join();
            // Note: reportId is generated but not linked back directly here
        } catch (Exception e) {
            logger.error("Failed to save Report entity for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            // We cannot update DB here, just set status
            throw new RuntimeException("Failed to save Report entity");
        }

        // Step 3: Update ReportJob with fetched rates and status
        entity.setBtcUsdRate(btcUsdRate);
        entity.setBtcEurRate(btcEurRate);
        entity.setStatus("FETCHED");

        // Step 4: Send email with conversion rates (simulate email sending)
        try {
            sendEmailReport(entity, report);
            entity.setStatus("SENT");
            entity.setEmailSentAt(Instant.now());
            report.setEmailSent(true);
            logger.info("Email sent successfully for ReportJob ID: {}", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to send email for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            throw new RuntimeException("Failed to send email report");
        }

        return entity;
    }

    private void sendEmailReport(ReportJob job, Report report) {
        String emailContent = String.format(
                "Bitcoin Conversion Rates Report\nRequested At: %s\nBTC/USD: %.4f\nBTC/EUR: %.4f",
                job.getRequestedAt().toString(),
                report.getBtcUsdRate(),
                report.getBtcEurRate()
        );
        logger.info("Sending email report for ReportJob ID: %s\n%s", job.getTechnicalId(), emailContent);
        // Real email integration to be implemented
    }

}
