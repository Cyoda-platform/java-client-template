package com.java_template.entity.SendReport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component
@RequiredArgsConstructor
@Slf4j
public class SendReportWorkflow {

    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processAnalyzeBooks(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Replace with actual external API call and analysis logic
            entity.put("analysisStatus", "completed");
            entity.put("popularityThreshold", entity.path("popularityThreshold").asInt(300));
            entity.put("entityVersion", ENTITY_VERSION);
            log.info("processAnalyzeBooks completed");
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processGenerateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Replace with actual report generation logic
            entity.put("reportStatus", "generated");
            entity.put("reportContent", "Summary of book titles, total page counts, publication dates, and popular excerpts.");
            entity.put("entityVersion", ENTITY_VERSION);
            log.info("processGenerateReport completed");
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processSendReport(ObjectNode entity) {
        return processAnalyzeBooks(entity)
                .thenCompose(this::processGenerateReport)
                .thenCompose(this::processSendEmail)
                .exceptionally(ex -> {
                    log.error("Error in workflow processSendReport", ex);
                    entity.put("status", "failed");
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> processSendEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: Replace with actual asynchronous email sending logic
                entity.put("status", "sent");
                log.info("Report sent successfully to recipients.");
            } catch (Exception e) {
                log.error("Error sending report", e);
                entity.put("status", "send_failed");
            }
            entity.put("entityVersion", ENTITY_VERSION);
            return entity;
        });
    }
}