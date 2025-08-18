package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.store.InMemoryDataStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/importJobs")
public class ImportJobController {

    @PostMapping
    public ResponseEntity<Map<String, String>> createJob(@RequestBody JsonNode payload) {
        ImportJob job = new ImportJob();
        job.setTechnicalId("job-" + UUID.randomUUID());
        job.setPayload(payload.get("payload") != null ? payload.get("payload") : payload);
        job.setJobName(payload.has("jobName") ? payload.get("jobName").asText() : null);
        job.setRequestedBy(payload.has("requestedBy") ? payload.get("requestedBy").asText() : null);
        job.setStatus("PENDING");
        job.setCreatedAt(Instant.now().toString());
        InMemoryDataStore.jobsByTechnicalId.put(job.getTechnicalId(), job);
        return ResponseEntity.ok(Map.of("technicalId", job.getTechnicalId()));
    }

    @GetMapping("/technical/{technicalId}")
    public ResponseEntity<ImportJob> getJob(@PathVariable String technicalId) {
        ImportJob job = InMemoryDataStore.jobsByTechnicalId.get(technicalId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}
