Below is a complete Java (Maven) application that ingests Nobel laureates data from an OpenDataSoft API, models three core entities (Job, Laureate, Subscriber), and implements a basic workflow engine that performs data ingestion, transformation, and subscriber notification. The code is self-contained and ready to compile with Maven.

Project structure (provided as code below):
- pom.xml
- src/main/java/com/example/nobel/model/Laureate.java
- src/main/java/com/example/nobel/model/Job.java
- src/main/java/com/example/nobel/model/Subscriber.java
- src/main/java/com/example/nobel/engine/WorkflowEngine.java
- src/main/java/com/example/nobel/service/DataIngestService.java
- src/main/java/com/example/nobel/service/NotificationService.java
- src/main/java/com/example/nobel/App.java
- README.md (instructions)

-------------------------------------------------------------------
pom.xml
-------------------------------------------------------------------
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>nobel-workflow</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.7</version>
    </dependency>
  </dependencies>
</project>

-------------------------------------------------------------------
src/main/java/com/example/nobel/model/Laureate.java
-------------------------------------------------------------------
package com.example.nobel.model;

import java.util.Objects;

public class Laureate {
    private String id;
    private String fullName;
    private String birthDate;
    private String diedDate;
    private String birthCity;
    private String birthCountry;
    private String category;
    private String year;
    private String motivation;

    public Laureate() {}

    public Laureate(String id, String fullName, String birthDate, String diedDate,
                    String birthCity, String birthCountry, String category, String year, String motivation) {
        this.id = id;
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.diedDate = diedDate;
        this.birthCity = birthCity;
        this.birthCountry = birthCountry;
        this.category = category;
        this.year = year;
        this.motivation = motivation;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public Laureate setId(String id) {
        this.id = id;
        return this;
    }

    public String getFullName() {
        return fullName;
    }

    public Laureate setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public Laureate setBirthDate(String birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public String getDiedDate() {
        return diedDate;
    }

    public Laureate setDiedDate(String diedDate) {
        this.diedDate = diedDate;
        return this;
    }

    public String getBirthCity() {
        return birthCity;
    }

    public Laureate setBirthCity(String birthCity) {
        this.birthCity = birthCity;
        return this;
    }

    public String getBirthCountry() {
        return birthCountry;
    }

    public Laureate setBirthCountry(String birthCountry) {
        this.birthCountry = birthCountry;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public Laureate setCategory(String category) {
        this.category = category;
        return this;
    }

    public String getYear() {
        return year;
    }

    public Laureate setYear(String year) {
        this.year = year;
        return this;
    }

    public String getMotivation() {
        return motivation;
    }

    public Laureate setMotivation(String motivation) {
        this.motivation = motivation;
        return this;
    }

    @Override
    public String toString() {
        return "Laureate{" +
                "id='" + id + '\'' +
                ", fullName='" + fullName + '\'' +
                ", birthDate='" + birthDate + '\'' +
                ", diedDate='" + diedDate + '\'' +
                ", birthCity='" + birthCity + '\'' +
                ", birthCountry='" + birthCountry + '\'' +
                ", category='" + category + '\'' +
                ", year='" + year + '\'' +
                ", motivation='" + motivation + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Laureate laureate = (Laureate) o;
        return Objects.equals(id, laureate.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/model/Job.java
-------------------------------------------------------------------
package com.example.nobel.model;

import java.time.Instant;
import java.util.UUID;

public class Job {
    public enum Status {PENDING, RUNNING, FAILED, COMPLETED}

    private final String id;
    private final String type;
    private Status status;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private int attemptCount;
    private Object payload;

    public Job(String type, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public Job setStatus(Status status) {
        this.status = status;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Job setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Job setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
        return this;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Job incrementAttempt() {
        this.attemptCount++;
        return this;
    }

    public Object getPayload() {
        return payload;
    }

    public Job setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", attemptCount=" + attemptCount +
                ", payload=" + (payload != null ? payload.getClass().getSimpleName() : "null") +
                '}';
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/model/Subscriber.java
-------------------------------------------------------------------
package com.example.nobel.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Subscriber {
    private final String id;
    private final String name;
    private final String email;
    private final Set<String> categories = new HashSet<>();

    public Subscriber(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public Subscriber subscribeCategory(String category) {
        if (category != null) categories.add(category.toLowerCase());
        return this;
    }

    public boolean interestedIn(String category) {
        if (category == null) return false;
        return categories.contains(category.toLowerCase());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getCategories() {
        return categories;
    }

    @Override
    public String toString() {
        return "Subscriber{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", categories=" + categories +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subscriber that = (Subscriber) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/service/DataIngestService.java
-------------------------------------------------------------------
package com.example.nobel.service;

import com.example.nobel.model.Laureate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataIngestService {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String apiUrl;

    /**
     * apiUrl should point to the OpenDataSoft API search endpoint, e.g.:
     * https://public.opendatasoft.com/api/records/1.0/search/?dataset=nobel-prize-laureates&rows=100
     */
    public DataIngestService(String apiUrl) {
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.apiUrl = apiUrl;
    }

    public List<Laureate> fetchLaureates() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Failed to fetch data. Status: " + resp.statusCode());
        }

        return parseLaureates(resp.body());
    }

    private List<Laureate> parseLaureates(String body) throws IOException {
        List<Laureate> result = new ArrayList<>();
        JsonNode root = mapper.readTree(body);

        // OpenDataSoft responses usually have "records" array
        JsonNode records = root.path("records");
        if (!records.isArray()) return result;

        for (JsonNode rec : records) {
            JsonNode fields = rec.path("fields");
            // Try to extract fields; dataset naming can vary, so attempt several likely keys
            String id = Optional.ofNullable(rec.path("recordid").asText(null)).orElse(rec.path("id").asText(null));
            String fullName = firstNonNull(
                    fields.path("laureate_name").asText(null),
                    fields.path("full_name").asText(null),
                    fields.path("name").asText(null),
                    fields.path("firstname").asText(null) + " " + fields.path("surname").asText(null)
            );
            String birthDate = firstNonNull(fields.path("birth_date").asText(null), fields.path("born").asText(null));
            String diedDate = firstNonNull(fields.path("died_date").asText(null), fields.path("died").asText(null));
            String birthCity = firstNonNull(fields.path("birth_city").asText(null), fields.path("born_city").asText(null));
            String birthCountry = firstNonNull(fields.path("birth_country").asText(null), fields.path("born_country").asText(null));
            String category = firstNonNull(fields.path("category").asText(null), fields.path("prize_category").asText(null), fields.path("prize_categories").asText(null));
            String year = firstNonNull(fields.path("year").asText(null), fields.path("prize_year").asText(null));
            String motivation = firstNonNull(fields.path("motivation").asText(null), fields.path("prize_motivation").asText(null));

            // Clean up null-ish values
            if (fullName == null && fields.has("firstname") && fields.has("surname")) {
                fullName = (fields.path("firstname").asText("") + " " + fields.path("surname").asText("")).trim();
            }

            Laureate la = new Laureate()
                    .setId(id)
                    .setFullName(fullName)
                    .setBirthDate(birthDate)
                    .setDiedDate(diedDate)
                    .setBirthCity(birthCity)
                    .setBirthCountry(birthCountry)
                    .setCategory(category)
                    .setYear(year)
                    .setMotivation(motivation);

            result.add(la);
        }

        return result;
    }

    private String firstNonNull(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/service/NotificationService.java
-------------------------------------------------------------------
package com.example.nobel.service;

import com.example.nobel.model.Laureate;
import com.example.nobel.model.Subscriber;

import java.util.List;

public class NotificationService {
    /**
     * Simple console-based notification. This can be replaced with real email/SMS integration.
     */
    public void notifySubscriber(Subscriber sub, List<Laureate> laureates) {
        if (laureates == null || laureates.isEmpty()) return;
        // For demo, print a summary line per subscriber
        System.out.println("=== Notification to " + sub.getName() + " <" + sub.getEmail() + "> ===");
        for (Laureate l : laureates) {
            System.out.printf("- %s (%s): %s %s%n",
                    l.getFullName(),
                    l.getYear() != null ? l.getYear() : "N/A",
                    l.getCategory() != null ? "[" + l.getCategory() + "]" : "",
                    l.getMotivation() != null ? "- " + l.getMotivation() : "");
        }
        System.out.println("=== End Notification ===");
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/engine/WorkflowEngine.java
-------------------------------------------------------------------
package com.example.nobel.engine;

import com.example.nobel.model.Job;
import com.example.nobel.model.Laureate;
import com.example.nobel.model.Subscriber;
import com.example.nobel.service.DataIngestService;
import com.example.nobel.service.NotificationService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class WorkflowEngine {
    private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final DataIngestService ingestService;
    private final NotificationService notificationService;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    public WorkflowEngine(DataIngestService ingestService, NotificationService notificationService) {
        this.ingestService = ingestService;
        this.notificationService = notificationService;
        // Worker
        executor.submit(this::workerLoop);
    }

    public void registerSubscriber(Subscriber s) {
        this.subscribers.add(s);
    }

    public void enqueue(Job job) {
        queue.offer(job);
    }

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                Job job = queue.poll(1, TimeUnit.SECONDS);
                if (job == null) continue;
                process(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void process(Job job) {
        job.incrementAttempt();
        job.setStatus(Job.Status.RUNNING).setStartedAt(Instant.now());
        try {
            switch (job.getType()) {
                case "INGEST":
                    handleIngest(job);
                    break;
                case "TRANSFORM":
                    handleTransform(job);
                    break;
                case "NOTIFY":
                    handleNotify(job);
                    break;
                default:
                    System.err.println("Unknown job type: " + job.getType());
            }
            job.setStatus(Job.Status.COMPLETED).setCompletedAt(Instant.now());
        } catch (Exception e) {
            job.setStatus(Job.Status.FAILED);
            System.err.println("Job failed: " + job);
            e.printStackTrace();
            // Simple retry policy for demo: re-enqueue if attempts < 3
            if (job.getAttemptCount() < 3) {
                job.setStatus(Job.Status.PENDING);
                enqueue(job);
            }
        }
    }

    private void handleIngest(Job job) throws Exception {
        System.out.println("Running INGEST job: " + job.getId());
        List<Laureate> laureates = ingestService.fetchLaureates();
        // Create transform job with fetched data
        Job transform = new Job("TRANSFORM", laureates);
        enqueue(transform);
    }

    @SuppressWarnings("unchecked")
    private void handleTransform(Job job) {
        System.out.println("Running TRANSFORM job: " + job.getId());
        List<Laureate> raw = (List<Laureate>) job.getPayload();
        // Basic transform: filter out entries missing name or category and deduplicate by id
        Map<String, Laureate> byId = new LinkedHashMap<>();
        for (Laureate l : raw) {
            if (l.getFullName() == null || l.getCategory() == null) continue;
            String id = l.getId() != null ? l.getId() : l.getFullName() + "|" + l.getYear();
            if (!byId.containsKey(id)) {
                byId.put(id, l);
            }
        }
        List<Laureate> cleaned = new ArrayList<>(byId.values());
        // Create notify job
        Job notify = new Job("NOTIFY", cleaned);
        enqueue(notify);
    }

    @SuppressWarnings("unchecked")
    private void handleNotify(Job job) {
        System.out.println("Running NOTIFY job: " + job.getId());
        List<Laureate> laureates = (List<Laureate>) job.getPayload();
        // For each subscriber, filter laureates according to interest and notify
        for (Subscriber sub : subscribers) {
            List<Laureate> matched = laureates.stream()
                    .filter(l -> l.getCategory() != null && sub.interestedIn(l.getCategory()))
                    .collect(Collectors.toList());
            if (!matched.isEmpty()) {
                notificationService.notifySubscriber(sub, matched);
            }
        }
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignore) {
            executor.shutdownNow();
        }
    }
}

-------------------------------------------------------------------
src/main/java/com/example/nobel/App.java
-------------------------------------------------------------------
package com.example.nobel;

import com.example.nobel.engine.WorkflowEngine;
import com.example.nobel.model.Job;
import com.example.nobel.model.Subscriber;
import com.example.nobel.service.DataIngestService;
import com.example.nobel.service.NotificationService;

import java.util.concurrent.TimeUnit;

public class App {
    public static void main(String[] args) throws InterruptedException {
        // Configure the OpenDataSoft API endpoint (rows=50 for example)
        String apiUrl = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=nobel-prize-laureates&rows=50";

        DataIngestService ingestService = new DataIngestService(apiUrl);
        NotificationService notificationService = new NotificationService();

        WorkflowEngine engine = new WorkflowEngine(ingestService, notificationService);

        // Register sample subscribers
        Subscriber s1 = new Subscriber("sub-1", "Alice", "alice@example.com").subscribeCategory("physics").subscribeCategory("chemistry");
        Subscriber s2 = new Subscriber("sub-2", "Bob", "bob@example.com").subscribeCategory("literature").subscribeCategory("peace");

        engine.registerSubscriber(s1);
        engine.registerSubscriber(s2);

        // Enqueue initial ingest job
        Job ingestJob = new Job("INGEST", null);
        engine.enqueue(ingestJob);

        // Let the engine run for a while to process jobs
        TimeUnit.SECONDS.sleep(20);

        // Shutdown gracefully
        engine.shutdown();
        System.out.println("Engine shut down.");
    }
}

-------------------------------------------------------------------
README.md
-------------------------------------------------------------------
Java Nobel Laureates Workflow Application
----------------------------------------

What this is:
- A Java application that ingests Nobel laureates data from an OpenDataSoft API, models three entities (Job, Laureate, Subscriber), and runs a basic workflow (INGEST -> TRANSFORM -> NOTIFY).
- Notifications are printed to the console (replace NotificationService with real email/SMS code to send real notifications).

Build & Run:
1. Ensure you have Java 11+ and Maven installed.
2. From the project root, compile:
   mvn clean package
3. Run:
   mvn exec:java -Dexec.mainClass="com.example.nobel.App"
   (or run the generated jar)

Configuration:
- The OpenDataSoft API endpoint is configured in App.java (apiUrl). Change rows, dataset, or query as needed.

Notes:
- The dataset fields in OpenDataSoft can vary; parsing attempts several common field names and is resilient to missing fields.
- The workflow engine is simple and intended as a demonstrator: job queue, retry policy, basic filtering & deduplication, and subscriber notification.

-------------------------------------------------------------------

End of application code.

If you want, I can:
- Adjust the OpenDataSoft dataset URL to match a specific dataset you plan to use.
- Replace console notifications with an SMTP-based email notifier.
- Add unit tests or Dockerfile.