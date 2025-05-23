package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaFlights")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, SearchResultResponse> searchResultsCache = new ConcurrentHashMap<>();
    private static final String ENTITY_NAME = "flight";

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> searchFlights(@RequestBody @Valid SearchRequest request) {
        logger.info("Received flight search request: {} -> {} on {}, passengers {}",
                request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate(), request.getPassengers());

        String searchId = UUID.randomUUID().toString();

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("searchId", searchId);
        entity.set("request", objectMapper.valueToTree(request));
        entity.put("status", "processing");

        // Call entityService.addItem with workflow function processFlight to do async processing before persistence
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entity,
                this::processFlight
        );

        SearchResultResponse emptyResponse = new SearchResultResponse(Collections.emptyList(), 0, searchId);
        return ResponseEntity.accepted().body(emptyResponse);
    }

    @GetMapping(value = "/results/{searchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> getCachedResults(@PathVariable @NotBlank String searchId) {
        logger.info("Retrieving cached flight search results for {}", searchId);
        SearchResultResponse result = searchResultsCache.get(searchId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search ID not found");
        }
        return ResponseEntity.ok(result);
    }

    // Main workflow orchestration method - no business logic here
    private CompletableFuture<ObjectNode> processFlight(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity)
                .thenCompose(this::processValidateSearchId)
                .thenCompose(this::processValidateRequestNode)
                .thenCompose(this::processFetchFlights)
                .thenCompose(this::processApplyFilters)
                .thenCompose(this::processApplySorting)
                .thenCompose(this::processSetResults)
                .exceptionally(ex -> {
                    logger.error("processFlight: Exception: {}", ex.getMessage(), ex);
                    entity.put("status", "error");
                    entity.set("flights", objectMapper.createArrayNode());
                    entity.put("totalResults", 0);
                    String searchId = entity.path("searchId").asText(null);
                    if (searchId != null)
                        searchResultsCache.put(searchId, new SearchResultResponse(Collections.emptyList(), 0, searchId));
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processValidateSearchId(ObjectNode entity) {
        String searchId = entity.path("searchId").asText(null);
        if (searchId == null || searchId.isEmpty()) {
            logger.error("processValidateSearchId: Missing or empty searchId");
            entity.put("status", "error");
            return CompletableFuture.completedFuture(entity);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processValidateRequestNode(ObjectNode entity) {
        JsonNode requestNode = entity.path("request");
        if (requestNode.isMissingNode() || !requestNode.isObject()) {
            logger.error("processValidateRequestNode: Missing or invalid request node");
            entity.put("status", "error");
            return CompletableFuture.completedFuture(entity);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processFetchFlights(ObjectNode entity) {
        try {
            SearchRequest request = objectMapper.treeToValue(entity.path("request"), SearchRequest.class);
            List<FlightInfo> flights = mockFetchFlights(request);
            ArrayNode flightsArray = objectMapper.createArrayNode();
            for (FlightInfo f : flights) {
                flightsArray.add(objectMapper.valueToTree(f));
            }
            entity.set("flights_raw", flightsArray);
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("processFetchFlights: Exception: {}", e.getMessage(), e);
            entity.put("status", "error");
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> processApplyFilters(ObjectNode entity) {
        try {
            SearchRequest request = objectMapper.treeToValue(entity.path("request"), SearchRequest.class);
            ArrayNode flightsRaw = (ArrayNode) entity.path("flights_raw");
            List<FlightInfo> flights = new ArrayList<>();
            for (JsonNode node : flightsRaw) {
                flights.add(objectMapper.treeToValue(node, FlightInfo.class));
            }
            flights = applyFilters(flights, request);
            ArrayNode filteredArray = objectMapper.createArrayNode();
            for (FlightInfo f : flights) {
                filteredArray.add(objectMapper.valueToTree(f));
            }
            entity.set("flights_filtered", filteredArray);
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("processApplyFilters: Exception: {}", e.getMessage(), e);
            entity.put("status", "error");
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> processApplySorting(ObjectNode entity) {
        try {
            SearchRequest request = objectMapper.treeToValue(entity.path("request"), SearchRequest.class);
            ArrayNode flightsFiltered = (ArrayNode) entity.path("flights_filtered");
            List<FlightInfo> flights = new ArrayList<>();
            for (JsonNode node : flightsFiltered) {
                flights.add(objectMapper.treeToValue(node, FlightInfo.class));
            }
            flights = applySorting(flights, request.getSortBy(), request.getSortOrder());
            ArrayNode sortedArray = objectMapper.createArrayNode();
            for (FlightInfo f : flights) {
                sortedArray.add(objectMapper.valueToTree(f));
            }
            entity.set("flights", sortedArray);
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("processApplySorting: Exception: {}", e.getMessage(), e);
            entity.put("status", "error");
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> processSetResults(ObjectNode entity) {
        String searchId = entity.path("searchId").asText(null);
        ArrayNode flightsNode = (ArrayNode) entity.path("flights");
        int totalResults = flightsNode.size();

        entity.put("totalResults", totalResults);
        entity.put("status", "completed");

        try {
            List<FlightInfo> flights = new ArrayList<>();
            for (JsonNode node : flightsNode) {
                flights.add(objectMapper.treeToValue(node, FlightInfo.class));
            }
            if (searchId != null) {
                searchResultsCache.put(searchId, new SearchResultResponse(flights, totalResults, searchId));
            }
        } catch (Exception e) {
            logger.error("processSetResults: Exception: {}", e.getMessage(), e);
            entity.put("status", "error");
        }

        // Clean up intermediate nodes
        entity.remove("flights_raw");
        entity.remove("flights_filtered");

        return CompletableFuture.completedFuture(entity);
    }

    private List<FlightInfo> mockFetchFlights(SearchRequest request) throws InterruptedException {
        Thread.sleep(500);
        List<FlightInfo> flights = new ArrayList<>();
        flights.add(new FlightInfo("FL-001","Delta Air Lines","DL123",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T07:00:00Z",request.getDepartureDate()+"T10:00:00Z",250.00,"USD"));
        flights.add(new FlightInfo("FL-002","American Airlines","AA456",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T09:30:00Z",request.getDepartureDate()+"T12:30:00Z",230.00,"USD"));
        flights.add(new FlightInfo("FL-003","United Airlines","UA789",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T12:00:00Z",request.getDepartureDate()+"T15:00:00Z",270.00,"USD"));
        return flights;
    }

    private List<FlightInfo> applyFilters(List<FlightInfo> flights, SearchRequest req) {
        List<FlightInfo> filtered = new ArrayList<>(flights);
        if (req.getAirlines() != null && !req.getAirlines().isEmpty()) {
            Set<String> allowed = new HashSet<>();
            req.getAirlines().forEach(a -> allowed.add(a.toLowerCase()));
            filtered.removeIf(f -> !allowed.contains(f.getAirline().toLowerCase()));
        }
        if (req.getMaxPrice() != null) {
            filtered.removeIf(f -> f.getPrice() > req.getMaxPrice());
        }
        if (req.getDepartureTimeFrom() != null && req.getDepartureTimeTo() != null) {
            String from = req.getDepartureTimeFrom();
            String to = req.getDepartureTimeTo();
            filtered.removeIf(f -> {
                String dep = f.getDepartureTime().substring(11,16);
                return dep.compareTo(from) < 0 || dep.compareTo(to) > 0;
            });
        }
        return filtered;
    }

    private List<FlightInfo> applySorting(List<FlightInfo> flights, String sortBy, String sortOrder) {
        if (sortBy == null) return flights;
        Comparator<FlightInfo> cmp = null;
        switch (sortBy) {
            case "price": cmp = Comparator.comparingDouble(FlightInfo::getPrice); break;
            case "departureTime": cmp = Comparator.comparing(FlightInfo::getDepartureTime); break;
            case "arrivalTime": cmp = Comparator.comparing(FlightInfo::getArrivalTime); break;
        }
        if (cmp == null) return flights;
        if ("desc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        flights.sort(cmp);
        return flights;
    }
}