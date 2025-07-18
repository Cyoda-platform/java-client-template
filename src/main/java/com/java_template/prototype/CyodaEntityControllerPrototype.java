package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/prototype/api/flights")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String AIRPORT_GAP_API_URL = "https://airportgap.dev-tester.com/api/flights/search"; // TODO replace if needed
    private static final String AIRPORT_GAP_API_KEY = "YOUR_API_KEY"; // TODO use real key

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, StoredSearchResults> storedResults = new ConcurrentHashMap<>();

    @Data
    public static class FlightSearchRequest {
        @NotBlank
        private String departureAirport;
        @NotBlank
        private String arrivalAirport;
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String departureDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String returnDate;
        @Min(1)
        private int passengers;
    }

    @Data
    public static class FlightSearchResponse {
        private String searchId;
        private int resultsCount;
        private String message;
    }

    @Data
    public static class FlightResult {
        private String flightNumber;
        private String airline;
        private String departureAirport;
        private String arrivalAirport;
        private String departureTime;
        private String arrivalTime;
        private Double price;
    }

    @Data
    public static class FlightResultsResponse {
        private String searchId;
        private List<FlightResult> flights;
    }

    @Data
    public static class FilterSortRequest {
        @NotBlank
        private String sortBy;
        private List<@NotBlank String> airlines;
        private Double priceMin;
        private Double priceMax;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T.*")
        private String departureStartTime;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T.*")
        private String departureEndTime;
    }

    @Data
    static class StoredSearchResults {
        private final Instant createdAt;
        private final List<FlightResult> flights;
    }

    @PostMapping("/search")
    public ResponseEntity<FlightSearchResponse> searchFlights(@RequestBody @Valid FlightSearchRequest request) {
        logger.info("Search request: {}->{} on {} passengers {}", request.getDepartureAirport(),
                request.getArrivalAirport(), request.getDepartureDate(), request.getPassengers());
        String searchId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("from", request.getDepartureAirport());
                payload.put("to", request.getArrivalAirport());
                payload.put("departure_date", request.getDepartureDate());
                if (request.getReturnDate() != null && !request.getReturnDate().isBlank()) {
                    payload.put("return_date", request.getReturnDate());
                }
                payload.put("passengers", request.getPassengers());
                URI uri = URI.create(AIRPORT_GAP_API_URL);
                ResponseEntity<String> apiResp = restTemplate.postForEntity(uri, payload, String.class);
                List<FlightResult> list = new ArrayList<>();
                if (apiResp.getStatusCode().is2xxSuccessful() && apiResp.getBody() != null) {
                    JsonNode root = objectMapper.readTree(apiResp.getBody());
                    JsonNode arr = root.path("flights");
                    if (arr.isArray()) {
                        for (JsonNode n : arr) {
                            FlightResult f = new FlightResult();
                            f.setFlightNumber(n.path("flight_number").asText(null));
                            f.setAirline(n.path("airline").asText(null));
                            f.setDepartureAirport(n.path("departure_airport").asText(null));
                            f.setArrivalAirport(n.path("arrival_airport").asText(null));
                            f.setDepartureTime(n.path("departure_time").asText(null));
                            f.setArrivalTime(n.path("arrival_time").asText(null));
                            if (n.has("price")) f.setPrice(n.get("price").asDouble());
                            list.add(f);
                        }
                    }
                }
                storedResults.put(searchId, new StoredSearchResults(Instant.now(), list));
                logger.info("Stored {} flights for {}", list.size(), searchId);
            } catch (Exception e) {
                logger.error("Error for " + searchId, e);
                storedResults.put(searchId, new StoredSearchResults(Instant.now(), Collections.emptyList()));
            }
        });
        FlightSearchResponse resp = new FlightSearchResponse();
        resp.setSearchId(searchId);
        resp.setResultsCount(0);
        resp.setMessage("Search started");
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/results/{searchId}")
    public ResponseEntity<FlightResultsResponse> getSearchResults(
            @PathVariable @NotBlank String searchId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String filterAirline) {
        logger.info("Get results for {}", searchId);
        StoredSearchResults s = storedResults.get(searchId);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        List<FlightResult> flights = new ArrayList<>(s.getFlights());
        if (filterAirline != null) flights.removeIf(f -> f.getAirline() == null || !f.getAirline().equalsIgnoreCase(filterAirline));
        if ("price_asc".equalsIgnoreCase(sort)) flights.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
        if ("price_desc".equalsIgnoreCase(sort)) flights.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());
        FlightResultsResponse resp = new FlightResultsResponse();
        resp.setSearchId(searchId);
        resp.setFlights(flights);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/results/{searchId}/filter")
    public ResponseEntity<FlightResultsResponse> filterResults(
            @PathVariable @NotBlank String searchId,
            @RequestBody @Valid FilterSortRequest req) {
        logger.info("Filter {} sortBy {}", searchId, req.getSortBy());
        StoredSearchResults s = storedResults.get(searchId);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        List<FlightResult> list = new ArrayList<>(s.getFlights());
        if (req.getAirlines() != null && !req.getAirlines().isEmpty()) {
            Set<String> set = new HashSet<>();
            req.getAirlines().forEach(a -> set.add(a.toLowerCase()));
            list.removeIf(f -> f.getAirline() == null || !set.contains(f.getAirline().toLowerCase()));
        }
        if (req.getPriceMin() != null) list.removeIf(f -> f.getPrice() == null || f.getPrice() < req.getPriceMin());
        if (req.getPriceMax() != null) list.removeIf(f -> f.getPrice() == null || f.getPrice() > req.getPriceMax());
        if (req.getDepartureStartTime() != null) list.removeIf(f -> f.getDepartureTime() == null || f.getDepartureTime().compareTo(req.getDepartureStartTime()) < 0);
        if (req.getDepartureEndTime() != null) list.removeIf(f -> f.getDepartureTime() == null || f.getDepartureTime().compareTo(req.getDepartureEndTime()) > 0);
        switch (req.getSortBy().toLowerCase()) {
            case "price_asc":
                list.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
                break;
            case "price_desc":
                list.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());
                break;
            case "departuretime_asc":
                list.sort(Comparator.comparing(FlightResult::getDepartureTime));
                break;
            case "departuretime_desc":
                list.sort(Comparator.comparing(FlightResult::getDepartureTime).reversed());
                break;
        }
        FlightResultsResponse r = new FlightResultsResponse();
        r.setSearchId(searchId);
        r.setFlights(list);
        return ResponseEntity.ok(r);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatus(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getMessage());
        Map<String,String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleException(Exception ex) {
        logger.error("Internal error", ex);
        Map<String,String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}