import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.ResponseStatusException;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/prototype/api/flights")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final AsyncRestTemplate restTemplate = new AsyncRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, FlightSearchResult> searchResults = new ConcurrentHashMap<>();

    @PostMapping("/search") // must be first
    public DeferredResult<ResponseEntity<FlightSearchResult>> searchFlights(@RequestBody @Valid FlightSearchCriteria criteria) {
        String jobId = UUID.randomUUID().toString();
        DeferredResult<ResponseEntity<FlightSearchResult>> output = new DeferredResult<>();

        CompletableFuture.runAsync(() -> {
            try {
                // Mock external API call
                String apiUrl = "https://api.example.com/flights"; // TODO: Replace with actual Airport Gap API endpoint
                restTemplate.getForEntity(apiUrl, String.class).addCallback(new ListenableFutureCallback<>() {
                    @Override
                    public void onSuccess(ResponseEntity<String> result) {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(result.getBody());
                            FlightSearchResult flightSearchResult = objectMapper.treeToValue(jsonNode, FlightSearchResult.class);
                            searchResults.put(jobId, flightSearchResult);
                            output.setResult(ResponseEntity.ok(flightSearchResult));
                        } catch (Exception e) {
                            logger.error("Error parsing JSON response", e);
                            output.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error parsing response"));
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        logger.error("Error during API call", t);
                        output.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during API call"));
                    }
                });
            } catch (Exception e) {
                logger.error("Error initiating search", e);
                output.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error initiating search"));
            }
        });

        return output;
    }

    @GetMapping("/results") // must be first
    public ResponseEntity<FlightSearchResult> getSearchResults(@RequestParam @NotNull String jobId) {
        FlightSearchResult result = searchResults.get(jobId);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sort-filter") // must be first
    public ResponseEntity<FlightSearchResult> sortAndFilterResults(@RequestBody @Valid SortFilterCriteria criteria) {
        // Mock logic for sorting and filtering
        // TODO: Implement sorting and filtering logic based on criteria
        logger.info("Sorting and filtering results");
        return ResponseEntity.ok(new FlightSearchResult()); // Mock result
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException", ex);
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class FlightSearchCriteria {
        @NotBlank
        private String departureAirport;
        @NotBlank
        private String arrivalAirport;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String departureDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String returnDate;
        @Min(1)
        private int passengers;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SortFilterCriteria {
        @NotBlank
        private String sortBy;
        @NotNull
        private Filter filter;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Filter {
        @Max(10000)
        private double maxPrice;
        @NotNull
        private TimeRange timeRange;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class TimeRange {
        @Pattern(regexp = "\\d{2}:\\d{2}")
        private String earliestDeparture;
        @Pattern(regexp = "\\d{2}:\\d{2}")
        private String latestArrival;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class FlightSearchResult {
        // Simplified placeholder for flight search result
    }
}