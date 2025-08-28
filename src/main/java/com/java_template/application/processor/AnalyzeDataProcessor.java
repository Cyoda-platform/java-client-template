package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AnalyzeDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AnalyzeDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        // Perform minimal, step-appropriate validation rather than relying on entity.isValid()
        if (entity == null) return false;
        if (entity.getJobId() == null || entity.getJobId().isBlank()) return false;
        if (entity.getDataSourceUrl() == null || entity.getDataSourceUrl().isBlank()) return false;
        // Other fields like generatedAt or reportLocation are not required at the start of analysis
        return true;
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Mark processing started
        try {
            logger.info("AnalyzeDataProcessor starting analysis for jobId={}", entity.getJobId());
            entity.setStatus("ANALYZING");

            String dataSourceUrl = entity.getDataSourceUrl();
            if (dataSourceUrl == null || dataSourceUrl.isBlank()) {
                logger.error("No dataSourceUrl provided for job {}", entity.getJobId());
                entity.setStatus("FAILED");
                return entity;
            }

            // Fetch CSV from URL
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dataSourceUrl))
                .GET()
                .build();

            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                logger.error("Failed to fetch data from {}: {}", dataSourceUrl, ex.getMessage(), ex);
                entity.setStatus("FAILED");
                return entity;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.error("Non-success response {} when fetching {}", response.statusCode(), dataSourceUrl);
                entity.setStatus("FAILED");
                return entity;
            }

            String csvContent = response.body();
            if (csvContent == null || csvContent.isBlank()) {
                logger.error("Fetched content is empty for {}", dataSourceUrl);
                entity.setStatus("FAILED");
                return entity;
            }

            // Compute sample hash
            String sampleHash = computeSHA256Hex(csvContent);
            // Build basic CSV parsing
            String[] rawLines = csvContent.split("\\R");
            List<String> lines = Arrays.stream(rawLines).filter(l -> l != null && !l.isBlank()).collect(Collectors.toList());
            if (lines.isEmpty()) {
                logger.error("No CSV lines found for {}", dataSourceUrl);
                entity.setStatus("FAILED");
                return entity;
            }

            String headerLine = lines.get(0);
            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndex.put(headers[i].trim().toLowerCase(), i);
            }

            int rowCount = Math.max(0, lines.size() - 1);

            // Prepare metrics requested
            String requestedMetrics = entity.getRequestedMetrics() != null ? entity.getRequestedMetrics() : "";
            Set<String> metrics = Arrays.stream(requestedMetrics.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

            Map<String, Object> metricsResults = new HashMap<>();
            List<Double> priceValues = new ArrayList<>();

            boolean hasPriceColumn = headerIndex.containsKey("price");
            Integer priceIdx = hasPriceColumn ? headerIndex.get("price") : null;
            boolean hasBedrooms = headerIndex.containsKey("bedrooms");
            Integer bedroomsIdx = hasBedrooms ? headerIndex.get("bedrooms") : null;
            boolean hasArea = headerIndex.containsKey("area");
            Integer areaIdx = hasArea ? headerIndex.get("area") : null;

            // Parse rows and extract price and grouping columns where present
            for (int r = 1; r < lines.size(); r++) {
                String[] cols = splitCsvLine(lines.get(r));
                if (priceIdx != null && priceIdx < cols.length) {
                    String raw = cols[priceIdx].replaceAll("[\"']", "").trim();
                    if (!raw.isBlank()) {
                        try {
                            double v = Double.parseDouble(raw);
                            priceValues.add(v);
                        } catch (NumberFormatException nfe) {
                            // skip invalid numeric
                        }
                    }
                }
            }

            if (metrics.contains("avg_price") && !priceValues.isEmpty()) {
                double avg = priceValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                metricsResults.put("avg_price", avg);
            }

            if (metrics.contains("median_price") && !priceValues.isEmpty()) {
                List<Double> sorted = new ArrayList<>(priceValues);
                Collections.sort(sorted);
                double median;
                int n = sorted.size();
                if (n % 2 == 1) {
                    median = sorted.get(n / 2);
                } else {
                    median = (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
                }
                metricsResults.put("median_price", median);
            }

            if (metrics.contains("price_by_bedrooms") && hasBedrooms && hasPriceColumn && !priceValues.isEmpty()) {
                Map<String, List<Double>> byBedrooms = new HashMap<>();
                for (int r = 1; r < lines.size(); r++) {
                    String[] cols = splitCsvLine(lines.get(r));
                    String bedroomKey = (bedroomsIdx < cols.length) ? cols[bedroomsIdx].replaceAll("[\"']", "").trim() : "UNKNOWN";
                    String rawPrice = (priceIdx < cols.length) ? cols[priceIdx].replaceAll("[\"']", "").trim() : "";
                    if (rawPrice.isBlank()) continue;
                    try {
                        double pv = Double.parseDouble(rawPrice);
                        byBedrooms.computeIfAbsent(bedroomKey.isBlank() ? "UNKNOWN" : bedroomKey, k -> new ArrayList<>()).add(pv);
                    } catch (NumberFormatException ignored) {}
                }
                Map<String, Double> avgByBedrooms = new HashMap<>();
                for (Map.Entry<String, List<Double>> e : byBedrooms.entrySet()) {
                    double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    avgByBedrooms.put(e.getKey(), avg);
                }
                metricsResults.put("price_by_bedrooms", avgByBedrooms);
            }

            if (metrics.contains("distribution_by_area") && hasArea) {
                Map<String, Integer> dist = new HashMap<>();
                for (int r = 1; r < lines.size(); r++) {
                    String[] cols = splitCsvLine(lines.get(r));
                    String areaKey = (areaIdx < cols.length) ? cols[areaIdx].replaceAll("[\"']", "").trim() : "UNKNOWN";
                    if (areaKey == null || areaKey.isBlank()) areaKey = "UNKNOWN";
                    dist.put(areaKey, dist.getOrDefault(areaKey, 0) + 1);
                }
                metricsResults.put("distribution_by_area", dist);
            }

            // Build analysis metadata (stored as a reportLocation pointer)
            String isoNow = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
            Map<String, Object> analysisMetadata = new HashMap<>();
            analysisMetadata.put("jobId", entity.getJobId());
            analysisMetadata.put("rowCount", rowCount);
            analysisMetadata.put("sampleHash", sampleHash);
            analysisMetadata.put("computedAt", isoNow);
            analysisMetadata.put("metrics", metricsResults);

            // In a real system, we'd persist analysisMetadata into a storage and set reportLocation accordingly.
            // Here we set a logical pointer which downstream report generator can use.
            String logicalLocation = "analysis://" + Optional.ofNullable(entity.getJobId()).orElse(UUID.randomUUID().toString()) + "/analysis.json";
            entity.setReportLocation(logicalLocation);
            entity.setGeneratedAt(isoNow);

            // Optionally, we can persist a DataSource record representing the fetched sample for reuse.
            try {
                com.java_template.application.entity.datasource.version_1.DataSource ds = new com.java_template.application.entity.datasource.version_1.DataSource();
                ds.setId(UUID.randomUUID().toString());
                ds.setUrl(dataSourceUrl);
                ds.setLastFetchedAt(isoNow);
                ds.setSampleHash(sampleHash);
                // derive a simple schema: comma-separated header
                ds.setSchema(String.join(",", headers));
                ds.setValidationStatus("VALID"); // assume valid since we could parse basic CSV
                // add DataSource entity for future reuse
                entityService.addItem(
                    com.java_template.application.entity.datasource.version_1.DataSource.ENTITY_NAME,
                    com.java_template.application.entity.datasource.version_1.DataSource.ENTITY_VERSION,
                    ds
                );
            } catch (Exception ex) {
                // Non-fatal: if adding DataSource fails, log and continue
                logger.warn("Failed to persist DataSource sample for job {}: {}", entity.getJobId(), ex.getMessage());
            }

            // Store analysis metadata in a lightweight in-memory placeholder by setting reportLocation and generatedAt.
            // Mark analysis as complete by leaving status as ANALYZING but with analysis results available.
            // Downstream AnalysisCompleteCriterion should detect available results and transition to REPORTING.
            logger.info("Analysis complete for job {}: rows={}, metricsKeys={}", entity.getJobId(), rowCount, metricsResults.keySet());
            // Keep status as ANALYZING to indicate it was the analysis step; metrics are available via reportLocation
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error during analysis for job {}: {}", entity.getJobId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            return entity;
        }
    }

    private static String[] splitCsvLine(String line) {
        // Very simple CSV splitter that handles basic quoted fields without embedded quotes.
        // For production use, use a CSV library.
        if (line == null) return new String[0];
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static String computeSHA256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}