package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/books")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BookReport> reports = new ConcurrentHashMap<>();

    @PostMapping("/report")
    public Map<String, String> generateReport() {
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        log.info("Received request to generate report, reportId={}", reportId);
        reports.put(reportId, new BookReport(reportId, requestedAt, 0, 0,
                new PublicationDateRange(null, null), Collections.emptyList()));
        CompletableFuture.runAsync(() -> processReport(reportId, requestedAt))
                .exceptionally(ex -> {
                    log.error("Error processing reportId={}: {}", reportId, ex.getMessage(), ex);
                    return null;
                });
        return Map.of(
                "status", "success",
                "message", "Report generation started",
                "reportId", reportId
        );
    }

    @GetMapping(value = "/report/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookReport getReport(@PathVariable("reportId") @NotBlank String reportId) {
        BookReport report = reports.get(reportId);
        if (report == null) {
            log.warn("Report not found: reportId={}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        log.info("Returning report for reportId={}", reportId);
        return report;
    }

    @Async
    protected void processReport(String reportId, Instant requestedAt) {
        log.info("Started processing reportId={}", reportId);
        try {
            URI uri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Books");
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            if (!rootNode.isArray()) {
                log.error("Unexpected JSON format from external API, expected array");
                throw new IllegalStateException("Invalid external API response format");
            }
            List<Book> books = new ArrayList<>();
            for (JsonNode bookNode : rootNode) {
                Book b = parseBook(bookNode);
                if (b != null) {
                    books.add(b);
                }
            }
            int totalPageCount = books.stream().mapToInt(Book::getPageCount).sum();
            Optional<LocalDate> earliestDate = books.stream()
                    .map(Book::getPublishDate).filter(Objects::nonNull).min(LocalDate::compareTo);
            Optional<LocalDate> latestDate = books.stream()
                    .map(Book::getPublishDate).filter(Objects::nonNull).max(LocalDate::compareTo);
            List<PopularTitle> popularTitles = books.stream()
                    .sorted(Comparator.comparingInt(Book::getPageCount).reversed())
                    .limit(5)
                    .map(b -> new PopularTitle(
                            b.getId(),
                            b.getTitle(),
                            snippet(b.getDescription(), 150),
                            snippet(b.getExcerpt(), 150),
                            b.getPageCount(),
                            b.getPublishDate()))
                    .collect(Collectors.toList());
            BookReport report = new BookReport(
                    reportId,
                    requestedAt,
                    books.size(),
                    totalPageCount,
                    new PublicationDateRange(earliestDate.orElse(null), latestDate.orElse(null)),
                    popularTitles
            );
            reports.put(reportId, report);
            sendReportEmail(report);
            log.info("Completed processing reportId={}", reportId);
        } catch (Exception e) {
            log.error("Failed processing reportId={}: {}", reportId, e.getMessage(), e);
        }
    }

    private Book parseBook(JsonNode node) {
        try {
            int id = node.path("id").asInt();
            String title = node.path("title").asText(null);
            String description = node.path("description").asText(null);
            int pageCount = node.path("pageCount").asInt(0);
            String excerpt = node.path("excerpt").asText(null);
            String publishDateStr = node.path("publishDate").asText(null);
            LocalDate publishDate = null;
            if (publishDateStr != null && !publishDateStr.isEmpty()) {
                try {
                    publishDate = LocalDate.parse(publishDateStr.substring(0, 10),
                            DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ex) {
                    log.warn("Failed to parse publishDate '{}' for book id={}", publishDateStr, id);
                }
            }
            return new Book(id, title, description, pageCount, excerpt, publishDate);
        } catch (Exception e) {
            log.error("Failed to parse book JSON node: {}", e.getMessage(), e);
            return null;
        }
    }

    private String snippet(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private void sendReportEmail(BookReport report) {
        log.info("Sending report email for reportId={}, totalBooks={}, popularTitles={}",
                report.getReportId(), report.getTotalBooks(), report.getPopularTitles().size());
        // TODO: Implement real email service integration
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getReason()
        );
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal server error"
        );
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Book {
        private int id;
        private String title;
        private String description;
        private int pageCount;
        private String excerpt;
        private LocalDate publishDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PopularTitle {
        private int id;
        private String title;
        private String descriptionSnippet;
        private String excerptSnippet;
        private int pageCount;
        private LocalDate publishDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PublicationDateRange {
        private LocalDate earliest;
        private LocalDate latest;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookReport {
        private String reportId;
        private Instant generatedAt;
        private int totalBooks;
        private int totalPageCount;
        private PublicationDateRange publicationDateRange;
        private List<PopularTitle> popularTitles;
    }
}