```java
package com.java_template.application.controller.adoptionrequest.version_1;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/adoptionRequests")
@RequiredArgsConstructor
public class AdoptionRequestController {

    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    @Operation(summary = "Create Adoption Request", description = "Submits an adoption request for a pet")
    @PostMapping
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request created", content = @Content(schema = @Schema(implementation = AdoptionRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<AdoptionRequestResponseDto> createAdoptionRequest(@RequestBody AdoptionRequestDto requestDto) {
        try {
            AdoptionRequest entity = new AdoptionRequest();
            entity.setPetId(requestDto.getPetId());
            entity.setUserId(requestDto.getUserId());
            entity.setStatus("PENDING");
            
            CompletableFuture<UUID> idFuture = entityService.addItem(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, entity);
            UUID entityId = idFuture.get();
            
            AdoptionRequestResponseDto responseDto = new AdoptionRequestResponseDto(entityId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Adoption Request by ID", description = "Retrieves an adoption request by its technical ID")
    @GetMapping("/{technicalId}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request retrieved", content = @Content(schema = @Schema(implementation = AdoptionRequestResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<AdoptionRequestResponseDto> getAdoptionRequest(@Parameter(name = "technicalId", description = "Technical ID of the adoption request") @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            
            if (dataPayload != null) {
                AdoptionRequestResponseDto response = new AdoptionRequestResponseDto(dataPayload.getData().toString());
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class AdoptionRequestDto {
        private String petId;
        private String userId;
    }

    @Data
    static class AdoptionRequestResponseDto {
        private String technicalId;

        public AdoptionRequestResponseDto(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
```