package com.java_template.application.controller;

import com.java_template.application.entity.dataextractionjob.version_1.DataExtractionJob;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-extraction-jobs")
public class DataExtractionJobController {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionJobController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<EntityResponse<DataExtractionJob>> createJob(@RequestBody DataExtractionJob job) {
        try {
            logger.info("Creating new data extraction job: {}", job.getJobName());
            EntityResponse<DataExtractionJob> response = entityService.save(job);
            logger.info("Data extraction job created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create data extraction job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<DataExtractionJob>> getJob(@PathVariable UUID id) {
        try {
            logger.info("Retrieving data extraction job with ID: {}", id);
            EntityResponse<DataExtractionJob> response = entityService.getItem(id, DataExtractionJob.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve data extraction job {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<DataExtractionJob>>> getAllJobs() {
        try {
            logger.info("Retrieving all data extraction jobs");
            List<EntityResponse<DataExtractionJob>> jobs = entityService.getItems(
                DataExtractionJob.class,
                DataExtractionJob.ENTITY_NAME,
                DataExtractionJob.ENTITY_VERSION,
                null,
                null,
                null
            );
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            logger.error("Failed to retrieve data extraction jobs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityResponse<DataExtractionJob>>> searchJobs(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String extractionType,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Searching data extraction jobs with filters - name: {}, type: {}, state: {}", 
                       jobName, extractionType, state);
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            
            List<Condition> conditions = new java.util.ArrayList<>();
            
            if (jobName != null && !jobName.trim().isEmpty()) {
                conditions.add(Condition.of("$.jobName", "CONTAINS", jobName));
            }
            if (extractionType != null && !extractionType.trim().isEmpty()) {
                conditions.add(Condition.of("$.extractionType", "EQUALS", extractionType));
            }
            if (state != null && !state.trim().isEmpty()) {
                conditions.add(Condition.lifecycle("state", "EQUALS", state));
            }
            
            condition.setConditions(conditions);
            
            List<EntityResponse<DataExtractionJob>> jobs = entityService.getItemsByCondition(
                DataExtractionJob.class,
                DataExtractionJob.ENTITY_NAME,
                DataExtractionJob.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            logger.error("Failed to search data extraction jobs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<DataExtractionJob>> updateJob(
            @PathVariable UUID id, 
            @RequestBody DataExtractionJob job,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating data extraction job with ID: {}, transition: {}", id, transition);
            
            EntityResponse<DataExtractionJob> response = entityService.update(id, job, transition);
            
            logger.info("Data extraction job updated with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update data extraction job {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        try {
            logger.info("Deleting data extraction job with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Data extraction job deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete data extraction job {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<EntityResponse<DataExtractionJob>> transitionJob(
            @PathVariable UUID id, 
            @PathVariable String transitionName) {
        try {
            logger.info("Transitioning data extraction job {} with transition: {}", id, transitionName);
            
            // Get current job
            EntityResponse<DataExtractionJob> currentResponse = entityService.getItem(id, DataExtractionJob.class);
            DataExtractionJob job = currentResponse.getData();
            
            // Update with transition
            EntityResponse<DataExtractionJob> response = entityService.update(id, job, transitionName);
            
            logger.info("Data extraction job transitioned with ID: {}, new state: {}", response.getId(), response.getState());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to transition data extraction job {} with {}: {}", id, transitionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/schedule-weekly")
    public ResponseEntity<EntityResponse<DataExtractionJob>> scheduleWeeklyJob() {
        try {
            logger.info("Scheduling weekly data extraction job");
            
            DataExtractionJob job = new DataExtractionJob();
            job.setJobName("Weekly Pet Store Data Extraction");
            job.setExtractionType("PRODUCTS");
            job.setApiEndpoint("https://petstore.swagger.io/v2");
            job.setScheduledTime(LocalDateTime.now().plusMinutes(5)); // Schedule 5 minutes from now
            job.setNextScheduledRun(LocalDateTime.now().plusDays(7)); // Next run in 7 days
            job.setRecordsExtracted(0);
            job.setRecordsProcessed(0);
            job.setRecordsFailed(0);
            
            EntityResponse<DataExtractionJob> response = entityService.save(job);
            logger.info("Weekly data extraction job scheduled with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to schedule weekly data extraction job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<DataExtractionJob>> getJobByBusinessId(@PathVariable Long businessId) {
        try {
            logger.info("Retrieving data extraction job with business ID: {}", businessId);
            EntityResponse<DataExtractionJob> response = entityService.findByBusinessId(
                DataExtractionJob.class,
                DataExtractionJob.ENTITY_NAME,
                DataExtractionJob.ENTITY_VERSION,
                businessId.toString(),
                "id"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve data extraction job by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<DataExtractionJob>> updateJobByBusinessId(
            @PathVariable Long businessId, 
            @RequestBody DataExtractionJob job,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating data extraction job with business ID: {}, transition: {}", businessId, transition);
            
            EntityResponse<DataExtractionJob> response = entityService.updateByBusinessId(job, "id", transition);
            
            logger.info("Data extraction job updated with business ID: {}", businessId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update data extraction job by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
