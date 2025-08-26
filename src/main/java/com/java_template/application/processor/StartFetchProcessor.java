package com.java_template.application.processor;
import com.java_template.application.entity.datafeed.version_1.DataFeed;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataFeed for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(DataFeed.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataFeed entity) {
        // For start-fetch we only require minimal fields: id and url
        if (entity == null) return false;
        if (entity.getId() == null || entity.getId().isBlank()) return false;
        if (entity.getUrl() == null || entity.getUrl().isBlank()) return false;
        return true;
    }

    private DataFeed processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataFeed> context) {
        DataFeed entity = context.entity();

        // Business logic:
        // 1. Attempt to download CSV from dataFeed.url
        // 2. Compute checksum of content
        // 3. Infer simple schema preview from header + first data row
        // 4. Set lastFetchedAt, lastChecksum, recordCount, schemaPreview, status, updatedAt
        // 5. On failure set status=FAILED and updatedAt

        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        try {
            String urlString = entity.getUrl();
            logger.info("Starting fetch for DataFeed id={} url={}", entity.getId(), urlString);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);

            int respCode = conn.getResponseCode();
            if (respCode != 200) {
                logger.error("Failed to fetch DataFeed id={} url={} responseCode={}", entity.getId(), urlString, respCode);
                entity.setStatus("FAILED");
                entity.setUpdatedAt(now);
                return entity;
            }

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            boolean firstLine = true;
            String headerLine = null;
            String firstDataLine = null;
            int totalLines = 0;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    headerLine = line;
                    firstLine = false;
                } else if (firstDataLine == null) {
                    firstDataLine = line;
                }
                sb.append(line).append("\n");
                totalLines++;
            }
            reader.close();
            conn.disconnect();

            String content = sb.toString();
            if (content.isBlank()) {
                logger.error("Fetched content is empty for DataFeed id={}", entity.getId());
                entity.setStatus("FAILED");
                entity.setUpdatedAt(now);
                return entity;
            }

            // Compute checksum (SHA-256)
            String checksum = sha256Hex(content);

            // Count records: totalLines includes headerLine? We appended header and additional lines, totalLines equals number of lines read.
            // If header present (headerLine != null) then recordCount = totalLines - 1, else totalLines
            int recordCount = Math.max(0, totalLines - (headerLine != null ? 1 : 0));

            // Infer simple schema preview using header and first data row
            Map<String, String> schemaPreview = new HashMap<>();
            if (headerLine != null) {
                String[] headers = headerLine.split(",", -1);
                String[] sampleValues = (firstDataLine != null) ? firstDataLine.split(",", -1) : new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    String col = headers[i].trim();
                    String sample = i < sampleValues.length ? sampleValues[i].trim() : "";
                    String inferredType = inferType(sample);
                    schemaPreview.put(col.isEmpty() ? ("col_" + i) : col, inferredType);
                }
            }

            // Update entity fields
            entity.setLastFetchedAt(now);
            entity.setLastChecksum(checksum);
            entity.setRecordCount(recordCount);
            entity.setSchemaPreview(schemaPreview);
            entity.setStatus("FETCHED");
            entity.setUpdatedAt(now);

            logger.info("Fetch completed for DataFeed id={} records={} checksum={}", entity.getId(), recordCount, checksum);

            return entity;

        } catch (Exception e) {
            logger.error("Error while fetching DataFeed id={}: {}", entity.getId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setUpdatedAt(now);
            return entity;
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static String inferType(String sample) {
        if (sample == null || sample.isEmpty()) return "string";
        // Try integer
        try {
            Integer.parseInt(sample);
            return "integer";
        } catch (NumberFormatException ignored) {}
        // Try double
        try {
            Double.parseDouble(sample);
            return "numeric";
        } catch (NumberFormatException ignored) {}
        // Fallback
        return "string";
    }
}