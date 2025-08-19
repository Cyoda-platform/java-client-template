package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class AnalyzeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalyzeProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataIngestJob analysis for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataIngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataIngestJob entity) {
        return entity != null && entity.isValid();
    }

    private DataIngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataIngestJob> context) {
        DataIngestJob job = context.entity();
        try {
            logger.info("AnalyzeProcessor starting for jobTechnicalId={}", job.getTechnicalId());
            // mark analyzing (idempotent)
            job.setStatus("ANALYZING");

            String sourceUrl = job.getSource_url();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                logger.warn("AnalyzeProcessor: source_url missing for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            // Idempotency: check if a report already exists for this job (CREATED/READY)
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.job_technicalId", "EQUALS", job.getTechnicalId())
                );
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> existingFuture = entityService.getItemsByCondition(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    condition,
                    true
                );
                com.fasterxml.jackson.databind.node.ArrayNode existing = existingFuture.get();
                if (existing != null && existing.size() > 0) {
                    // If there's an existing report in CREATED or READY, reuse it (idempotent)
                    for (com.fasterxml.jackson.databind.JsonNode r : existing) {
                        String status = r.path("status").asText(null);
                        if (status != null && ("CREATED".equalsIgnoreCase(status) || "READY".equalsIgnoreCase(status))) {
                            logger.info("AnalyzeProcessor: existing report found for job {} with status {} - reusing", job.getTechnicalId(), status);
                            job.setStatus("DELIVERING");
                            return job;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("AnalyzeProcessor: failed to check existing reports for job {}: {}", job.getTechnicalId(), e.getMessage());
                // continue; we will attempt analysis
            }

            // Fetch CSV from sourceUrl
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException ioe) {
                logger.warn("AnalyzeProcessor: transient network error while downloading CSV for job {}: {}", job.getTechnicalId(), ioe.getMessage());
                // Treat as transient: leave in ANALYZING to allow retries
                job.setStatus("ANALYZING");
                return job;
            }

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                logger.warn("AnalyzeProcessor: failed to download CSV for job {} status={}", job.getTechnicalId(), resp.statusCode());
                job.setStatus("FAILED");
                return job;
            }

            String csv = resp.body();
            if (csv == null || csv.isBlank()) {
                logger.warn("AnalyzeProcessor: empty CSV for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            // Parse CSV - simple parser for numeric column 'price' and 'neighbourhood' grouping
            List<String> lines = Arrays.stream(csv.split("\r?\n")).collect(Collectors.toList());
            if (lines.size() <= 1) {
                logger.warn("AnalyzeProcessor: CSV has no data rows for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            String header = lines.get(0);
            String[] headers = header.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim();
                if (h.startsWith("\"") && h.endsWith("\"")) h = h.substring(1, h.length() - 1);
                headerIndex.put(h.toLowerCase(), i);
            }
            int priceIdx = headerIndex.getOrDefault("price", -1);
            int neighbourhoodIdx = headerIndex.getOrDefault("neighbourhood", -1);

            List<Double> prices = new ArrayList<>();
            Map<String, List<Double>> byNeighbour = new HashMap<>();
            Map<String, Integer> missingCounts = new HashMap<>();

            for (int i = 1; i < lines.size(); i++) {
                String row = lines.get(i);
                if (row == null || row.isBlank()) continue;
                String[] cols = row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1); // handle commas in quotes
                // price
                Double price = null;
                if (priceIdx >= 0 && priceIdx < cols.length) {
                    String pv = cols[priceIdx].trim();
                    if (!pv.isBlank()) {
                        try { price = Double.parseDouble(pv); prices.add(price); } catch (NumberFormatException nfe) { missingCounts.merge("price", 1, Integer::sum); }
                    } else {
                        missingCounts.merge("price", 1, Integer::sum);
                    }
                } else {
                    missingCounts.merge("price", 1, Integer::sum);
                }

                // neighbourhood
                String nb = null;
                if (neighbourhoodIdx >= 0 && neighbourhoodIdx < cols.length) {
                    nb = cols[neighbourhoodIdx].trim();
                    if (nb.isBlank()) {
                        nb = "<UNKNOWN>";
                        missingCounts.merge("neighbourhood", 1, Integer::sum);
                    }
                } else {
                    nb = "<UNKNOWN>";
                    missingCounts.merge("neighbourhood", 1, Integer::sum);
                }

                if (price != null) {
                    byNeighbour.computeIfAbsent(nb, k -> new ArrayList<>()).add(price);
                }
            }

            // Compute metrics
            int totalRecords = prices.size();
            if (totalRecords == 0) {
                logger.warn("AnalyzeProcessor: no valid price records for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            List<Double> sorted = prices.stream().sorted().collect(Collectors.toList());
            double median;
            int n = sorted.size();
            if (n % 2 == 1) {
                median = sorted.get(n / 2);
            } else {
                median = (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            }

            ObjectNode metrics = objectMapper.createObjectNode();
            metrics.put("total_records", totalRecords);
            metrics.put("mean_price", mean);
            metrics.put("median_price", median);

            // distribution by neighbourhood
            ObjectNode dist = objectMapper.createObjectNode();
            for (Map.Entry<String, List<Double>> e : byNeighbour.entrySet()) {
                List<Double> list = e.getValue();
                double m = list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                List<Double> s = list.stream().sorted().collect(Collectors.toList());
                double med;
                int sz = s.size();
                if (sz == 0) med = 0.0;
                else if (sz % 2 == 1) med = s.get(sz / 2);
                else med = (s.get(sz / 2 - 1) + s.get(sz / 2)) / 2.0;
                ObjectNode nnode = objectMapper.createObjectNode();
                nnode.put("count", list.size());
                nnode.put("mean", m);
                nnode.put("median", med);
                dist.set(e.getKey(), nnode);
            }
            metrics.set("distribution_by_neighbourhood", dist);

            // missing counts
            ObjectNode missingNode = objectMapper.createObjectNode();
            for (Map.Entry<String, Integer> e : missingCounts.entrySet()) {
                missingNode.put(e.getKey(), e.getValue());
            }
            metrics.set("missing_value_counts", missingNode);

            // Build AnalysisReport
            AnalysisReport report = new AnalysisReport();
            report.setJob_technicalId(job.getTechnicalId());
            report.setGenerated_at(Instant.now().toString());
            report.setSummary_metrics(objectMapper.writeValueAsString(metrics));
            report.setRecord_count(totalRecords);
            report.setReport_link("reports/" + job.getTechnicalId() + ".json");
            report.setStatus("CREATED");
            report.setCreated_at(Instant.now().toString());

            // Persist report via entityService
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                report
            );
            try {
                java.util.UUID createdId = addFuture.get();
                report.setTechnicalId(createdId.toString());
            } catch (Exception e) {
                logger.error("Failed to persist AnalysisReport for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
                job.setStatus("FAILED");
                return job;
            }

            // Move job to DELIVERING state so DeliveryProcessor picks it up
            job.setStatus("DELIVERING");
            logger.info("AnalyzeProcessor completed job {} and created report {}", job.getTechnicalId(), report.getTechnicalId());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error while analyzing job {}: {}", job.getTechnicalId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}
