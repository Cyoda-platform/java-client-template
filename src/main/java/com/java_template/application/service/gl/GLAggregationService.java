package com.java_template.application.service.gl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.JournalEntry;
import com.java_template.application.entity.accrual.version_1.JournalEntryDirection;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating journal entries into GL monthly reports.
 *
 * <p>As specified in section 8 of the requirements, this service:</p>
 * <ul>
 *   <li>Iterates all Accrual entities for a given month</li>
 *   <li>Groups journal entries by (asOfDate, account, direction, currency, priorPeriodFlag)</li>
 *   <li>Sums amounts for each group</li>
 *   <li>Separates prior period adjustments</li>
 *   <li>Calculates total debits and credits</li>
 *   <li>Generates batch file ID and checksum</li>
 *   <li>Exports reports to CSV and JSON formats</li>
 * </ul>
 */
@Service
public class GLAggregationService {

    private static final Logger logger = LoggerFactory.getLogger(GLAggregationService.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with dependency injection.
     *
     * @param entityService Service for accessing Accrual entities
     */
    public GLAggregationService(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Aggregates all journal entries for the specified month into a GL report.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Query all Accrual entities where asOfDate falls in the specified month</li>
     *   <li>Extract journal entries and inherit fields from parent Accrual</li>
     *   <li>Group entries by aggregation key</li>
     *   <li>Sum amounts for each group</li>
     *   <li>Separate prior period adjustments</li>
     *   <li>Calculate totals and generate metadata</li>
     * </ol>
     *
     * @param month The month to aggregate (e.g., YearMonth.of(2025, 8) for August 2025)
     * @return GLMonthlyReport containing all aggregated data
     */
    public GLMonthlyReport aggregateMonthlyJournals(YearMonth month) {
        logger.info("Starting GL aggregation for month: {}", month);

        // Calculate date range for the month
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        logger.debug("Querying accruals from {} to {}", startDate, endDate);

        // Query all accruals for the month
        List<EntityWithMetadata<Accrual>> accruals = queryAccrualsForMonth(startDate, endDate);
        logger.info("Found {} accruals for month {}", accruals.size(), month);

        // Group journal entries by aggregation key
        Map<GLAggregationKey, List<BigDecimal>> groupedEntries = new HashMap<>();

        for (EntityWithMetadata<Accrual> accrualWithMetadata : accruals) {
            Accrual accrual = accrualWithMetadata.entity();

            // Skip accruals without journal entries
            if (accrual.getJournalEntries() == null || accrual.getJournalEntries().isEmpty()) {
                logger.debug("Skipping accrual {} - no journal entries", accrual.getAccrualId());
                continue;
            }

            // Process each journal entry
            for (JournalEntry entry : accrual.getJournalEntries()) {
                // Create aggregation key using inherited fields from parent Accrual
                GLAggregationKey key = new GLAggregationKey(
                        accrual.getAsOfDate(),                                    // Inherited from Accrual
                        entry.getAccount(),                                        // From JournalEntry
                        entry.getDirection(),                                      // From JournalEntry
                        accrual.getCurrency(),                                     // Inherited from Accrual
                        Boolean.TRUE.equals(accrual.getPriorPeriodFlag())         // Inherited from Accrual
                );

                // Add amount to the group
                groupedEntries.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getAmount());
            }
        }

        logger.info("Grouped journal entries into {} aggregation keys", groupedEntries.size());

        // Create aggregation entries with summed amounts
        List<GLAggregationEntry> entries = groupedEntries.entrySet().stream()
                .map(e -> new GLAggregationEntry(
                        e.getKey(),
                        e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()
                ))
                .sorted(Comparator.comparing((GLAggregationEntry e) -> e.getKey().getAsOfDate())
                        .thenComparing(e -> e.getKey().getAccount())
                        .thenComparing(e -> e.getKey().getDirection()))
                .collect(Collectors.toList());

        // Separate prior period adjustments
        List<GLAggregationEntry> priorPeriodAdjustments = entries.stream()
                .filter(e -> e.getKey().isPriorPeriodFlag())
                .collect(Collectors.toList());

        logger.info("Identified {} prior period adjustment entries", priorPeriodAdjustments.size());

        // Calculate total debits and credits
        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getKey().getDirection() == JournalEntryDirection.DR)
                .map(GLAggregationEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getKey().getDirection() == JournalEntryDirection.CR)
                .map(GLAggregationEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        logger.info("Calculated totals - Debits: {}, Credits: {}", totalDebits, totalCredits);

        // Generate batch file ID
        String batchFileId = generateBatchFileId(month);

        // Calculate checksum
        String checksum = calculateChecksum(entries, totalDebits, totalCredits);

        GLMonthlyReport report = new GLMonthlyReport(
                month,
                entries,
                totalDebits,
                totalCredits,
                priorPeriodAdjustments,
                batchFileId,
                checksum
        );

        logger.info("GL aggregation completed: {}", report);

        return report;
    }

    /**
     * Queries all Accrual entities whose asOfDate falls within the specified date range.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @return List of accruals with metadata
     */
    private List<EntityWithMetadata<Accrual>> queryAccrualsForMonth(LocalDate startDate, LocalDate endDate) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);

        try {
            // Query all accruals (in production, this should be filtered by date range)
            List<EntityWithMetadata<Accrual>> allAccruals = entityService.findAll(modelSpec, Accrual.class);

            // Filter by date range
            return allAccruals.stream()
                    .filter(a -> {
                        LocalDate asOfDate = a.entity().getAsOfDate();
                        return asOfDate != null &&
                               !asOfDate.isBefore(startDate) &&
                               !asOfDate.isAfter(endDate);
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error querying accruals for date range {} to {}", startDate, endDate, e);
            throw new RuntimeException("Failed to query accruals", e);
        }
    }

    /**
     * Generates a unique batch file ID for the report.
     *
     * @param month The month being reported
     * @return Batch file ID (format: GL-YYYYMM-UUID)
     */
    private String generateBatchFileId(YearMonth month) {
        String monthStr = String.format("%04d%02d", month.getYear(), month.getMonthValue());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("GL-%s-%s", monthStr, uuid);
    }

    /**
     * Calculates a checksum of the report data for integrity verification.
     *
     * @param entries All aggregation entries
     * @param totalDebits Total debit amount
     * @param totalCredits Total credit amount
     * @return SHA-256 checksum as hex string
     */
    private String calculateChecksum(List<GLAggregationEntry> entries,
                                     BigDecimal totalDebits, BigDecimal totalCredits) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Include all entry data in checksum
            for (GLAggregationEntry entry : entries) {
                String data = String.format("%s|%s|%s|%s|%s|%s|%d",
                        entry.getKey().getAsOfDate(),
                        entry.getKey().getAccount(),
                        entry.getKey().getDirection(),
                        entry.getKey().getCurrency(),
                        entry.getKey().isPriorPeriodFlag(),
                        entry.getTotalAmount(),
                        entry.getEntryCount());
                digest.update(data.getBytes());
            }

            // Include totals in checksum
            digest.update(totalDebits.toString().getBytes());
            digest.update(totalCredits.toString().getBytes());

            // Convert to hex string
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }

    /**
     * Exports the GL monthly report to CSV format.
     *
     * <p>CSV format:</p>
     * <ul>
     *   <li>Header row with column names</li>
     *   <li>Data rows for each aggregation entry</li>
     *   <li>Separate section for prior period adjustments</li>
     *   <li>Summary row with total debits and credits</li>
     *   <li>Footer with batch file ID and checksum</li>
     * </ul>
     *
     * @param report The report to export
     * @param outputPath Path where the CSV file should be written
     * @throws IOException if file writing fails
     */
    public void exportReportToCSV(GLMonthlyReport report, Path outputPath) throws IOException {
        logger.info("Exporting GL report to CSV: {}", outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write("AsOfDate,Account,Direction,Currency,PriorPeriodFlag,Amount,EntryCount");
            writer.newLine();

            // Write regular entries (non-PPA)
            List<GLAggregationEntry> regularEntries = report.getEntries().stream()
                    .filter(e -> !e.getKey().isPriorPeriodFlag())
                    .collect(Collectors.toList());

            for (GLAggregationEntry entry : regularEntries) {
                writeCSVRow(writer, entry);
            }

            // Write prior period adjustments section if any exist
            if (!report.getPriorPeriodAdjustments().isEmpty()) {
                writer.newLine();
                writer.write("# PRIOR PERIOD ADJUSTMENTS");
                writer.newLine();

                for (GLAggregationEntry entry : report.getPriorPeriodAdjustments()) {
                    writeCSVRow(writer, entry);
                }
            }

            // Write summary section
            writer.newLine();
            writer.write("# SUMMARY");
            writer.newLine();
            writer.write(String.format("Total Debits,%s", report.getTotalDebits()));
            writer.newLine();
            writer.write(String.format("Total Credits,%s", report.getTotalCredits()));
            writer.newLine();
            writer.write(String.format("Balanced,%s", report.isBalanced()));
            writer.newLine();

            if (!report.isBalanced()) {
                writer.write(String.format("Imbalance,%s", report.getImbalance()));
                writer.newLine();
            }

            // Write metadata footer
            writer.newLine();
            writer.write("# METADATA");
            writer.newLine();
            writer.write(String.format("Month,%s", report.getMonth()));
            writer.newLine();
            writer.write(String.format("Batch File ID,%s", report.getBatchFileId()));
            writer.newLine();
            writer.write(String.format("Checksum,%s", report.getChecksum()));
            writer.newLine();
        }

        logger.info("CSV export completed: {}", outputPath);
    }

    /**
     * Writes a single aggregation entry as a CSV row.
     *
     * @param writer The buffered writer
     * @param entry The entry to write
     * @throws IOException if writing fails
     */
    private void writeCSVRow(BufferedWriter writer, GLAggregationEntry entry) throws IOException {
        writer.write(String.format("%s,%s,%s,%s,%s,%s,%d",
                entry.getKey().getAsOfDate(),
                entry.getKey().getAccount(),
                entry.getKey().getDirection(),
                entry.getKey().getCurrency(),
                entry.getKey().isPriorPeriodFlag(),
                entry.getTotalAmount(),
                entry.getEntryCount()));
        writer.newLine();
    }

    /**
     * Exports the GL monthly report to JSON format.
     *
     * <p>The JSON output includes all report data in a structured format
     * that can be easily consumed by downstream systems.</p>
     *
     * @param report The report to export
     * @param outputPath Path where the JSON file should be written
     * @throws IOException if file writing fails
     */
    public void exportReportToJSON(GLMonthlyReport report, Path outputPath) throws IOException {
        logger.info("Exporting GL report to JSON: {}", outputPath);

        objectMapper.writeValue(outputPath.toFile(), report);

        logger.info("JSON export completed: {}", outputPath);
    }

    /**
     * Validates that the report is balanced (debits equal credits).
     *
     * <p>In double-entry bookkeeping, total debits must always equal total credits.
     * An imbalanced report indicates a data integrity issue.</p>
     *
     * @param report The report to validate
     * @return true if balanced, false otherwise
     */
    public boolean validateReportBalance(GLMonthlyReport report) {
        boolean balanced = report.isBalanced();

        if (balanced) {
            logger.info("Report validation PASSED - Debits ({}) equal Credits ({})",
                    report.getTotalDebits(), report.getTotalCredits());
        } else {
            logger.error("Report validation FAILED - Imbalance detected: Debits ({}) vs Credits ({}), Difference: {}",
                    report.getTotalDebits(), report.getTotalCredits(), report.getImbalance());
        }

        return balanced;
    }
}

