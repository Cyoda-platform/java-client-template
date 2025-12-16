package com.java_template.application.controller;

import com.java_template.application.entity.instrument.version_1.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Instrument entity
 * Provides CRUD operations and market data endpoints
 */
@RestController
@RequestMapping("/ui/instruments")
@CrossOrigin(origins = "*")
public class InstrumentController {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InstrumentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Instrument>> createInstrument(@RequestBody Instrument instrument) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Instrument.ENTITY_NAME).withVersion(Instrument.ENTITY_VERSION);
            EntityWithMetadata<Instrument> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, instrument.getSymbol(), "symbol", Instrument.class);

            if (existing != null) {
                logger.warn("Instrument with symbol {} already exists", instrument.getSymbol());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Instrument already exists with symbol: %s", instrument.getSymbol())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Instrument> response = entityService.create(instrument);
            logger.info("Instrument created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create instrument: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Instrument>> getInstrumentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Instrument.ENTITY_NAME).withVersion(Instrument.ENTITY_VERSION);
            EntityWithMetadata<Instrument> response = entityService.getById(id, modelSpec, Instrument.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve instrument: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<EntityWithMetadata<Instrument>> getInstrumentBySymbol(@PathVariable String symbol) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Instrument.ENTITY_NAME).withVersion(Instrument.ENTITY_VERSION);
            EntityWithMetadata<Instrument> response = entityService.findByBusinessId(
                    modelSpec, symbol, "symbol", Instrument.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve instrument: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Instrument>> updateInstrument(
            @PathVariable UUID id,
            @RequestBody Instrument instrument,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Instrument> response = entityService.update(id, instrument, transition);
            logger.info("Instrument updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update instrument: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-type")
    public ResponseEntity<List<EntityWithMetadata<Instrument>>> searchByType(@RequestParam String type) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Instrument.ENTITY_NAME).withVersion(Instrument.ENTITY_VERSION);

            SimpleCondition typeCondition = new SimpleCondition()
                    .withJsonPath("$.instrumentType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(type));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(typeCondition));

            PageResult<EntityWithMetadata<Instrument>> result = entityService.search(
                    modelSpec,
                    condition,
                    Instrument.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1000)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            logger.info("Found {} instruments of type '{}'", result.data().size(), type);
            return ResponseEntity.ok(result.data());
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search instruments: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstrument(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Instrument deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete instrument: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

