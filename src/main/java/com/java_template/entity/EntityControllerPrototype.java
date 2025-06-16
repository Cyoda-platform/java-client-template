import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock storage for activities and reports
    private final Map<String, JsonNode> activityData = new ConcurrentHashMap<>();
    private final Map<String, String> dailyReports = new ConcurrentHashMap<>();

    @PostMapping("/fetch-activities")
    public String fetchActivities(@RequestBody @Valid DateRange dateRange) {
        logger.info("Fetching activities from external API for date range: {} - {}", dateRange.getStartDate(), dateRange.getEndDate());
        try {
            // TODO: Replace URL with real Fakerest API endpoint
            String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
            String response = restTemplate.getForObject(apiUrl, String.class);

            if (response != null) {
                JsonNode activities = objectMapper.readTree(response);
                // Using dateRange as a key for simplicity
                activityData.put(dateRange.getStartDate() + "_" + dateRange.getEndDate(), activities);
                logger.info("Successfully fetched and stored activities.");
                return "Activities fetched successfully.";
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data returned from API");
            }
        } catch (Exception e) {
            logger.error("Error fetching activities: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching activities");
        }
    }

    @PostMapping("/process-activities")
    public String processActivities(@RequestBody @Valid ActivityRequest activityRequest) {
        logger.info("Processing activities for request: {}", activityRequest);
        CompletableFuture.runAsync(() -> {
            // TODO: Implement actual processing logic
            JsonNode activities = activityData.get(activityRequest.getKey());
            if (activities != null) {
                // Mock processing logic
                logger.info("Processing {} activities.", activities.size());
                // Simulate processing time
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Processing interrupted.");
                }
                logger.info("Processing complete.");
            } else {
                logger.error("No activities found for key: {}", activityRequest.getKey());
            }
        });
        return "Processing started.";
    }

    @GetMapping("/daily-report")
    public String getDailyReport(@RequestParam @NotBlank String date) {
        logger.info("Retrieving daily report for date: {}", date);
        String report = dailyReports.get(date);
        if (report != null) {
            return report;
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report found for date " + date);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {}", ex.getMessage());
        return String.format("Error: %s - %s", ex.getStatusCode().toString(), ex.getMessage());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class DateRange {
        @NotNull
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String startDate;

        @NotNull
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String endDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ActivityRequest {
        @NotNull
        @Size(min = 1)
        private String key;
    }
}