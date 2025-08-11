package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private Map<String, Object> jobStore = new HashMap<>();
    private Map<String, Object> subscriberStore = new HashMap<>();
    private Map<String, Object> laureateStore = new HashMap<>();

    // POST /jobs - create Job
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        if (jobName == null || jobName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String technicalId = UUID.randomUUID().toString();
        Map<String, Object> job = new HashMap<>();
        job.put("technicalId", technicalId);
        job.put("jobName", jobName);
        job.put("status", "SCHEDULED");
        jobStore.put(technicalId, job);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /jobs/{technicalId} - get Job
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Object> getJob(@PathVariable String technicalId) {
        Object job = jobStore.get(technicalId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    // GET /laureates/{technicalId} - get Laureate
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Object> getLaureate(@PathVariable String technicalId) {
        Object laureate = laureateStore.get(technicalId);
        if (laureate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(laureate);
    }

    // POST /subscribers - create Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, Object> request) {
        String contactType = (String) request.get("contactType");
        String contactDetails = (String) request.get("contactDetails");
        if (contactType == null || contactType.isBlank() || contactDetails == null || contactDetails.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String technicalId = UUID.randomUUID().toString();
        Map<String, Object> subscriber = new HashMap<>();
        subscriber.put("technicalId", technicalId);
        subscriber.put("contactType", contactType);
        subscriber.put("contactDetails", contactDetails);
        subscriber.put("active", true);
        subscriberStore.put(technicalId, subscriber);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /subscribers/{technicalId} - get Subscriber
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Object> getSubscriber(@PathVariable String technicalId) {
        Object subscriber = subscriberStore.get(technicalId);
        if (subscriber == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscriber);
    }
}
