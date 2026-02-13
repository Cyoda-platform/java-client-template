package com.cyoda.app.service;

import com.cyoda.app.entity.CatFact;
import com.cyoda.app.repository.CatFactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class WeeklyFactIngestionService {
    private static final Logger logger = LoggerFactory.getLogger(WeeklyFactIngestionService.class);
    private final CatFactRepository catFactRepository;
    private final RestTemplate restTemplate;
    private final SendWeeklyEmailsService sendWeeklyEmailsService;

    public WeeklyFactIngestionService(CatFactRepository catFactRepository,
                                      RestTemplate restTemplate,
                                      SendWeeklyEmailsService sendWeeklyEmailsService) {
        this.catFactRepository = catFactRepository;
        this.restTemplate = restTemplate;
        this.sendWeeklyEmailsService = sendWeeklyEmailsService;
    }

    // Run weekly on Mondays at 09:00 UTC (example cron). Adjust as needed.
    @Scheduled(cron = "0 0 9 * * MON")
    public void runWeeklyIngestion() {
        logger.info("Starting weekly cat fact ingestion");
        try {
            ResponseEntity<CatFactResponse> resp = restTemplate.getForEntity("https://catfact.ninja/fact", CatFactResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                CatFactResponse body = resp.getBody();
                CatFact fact = new CatFact();
                fact.setId(body.getId() != null ? body.getId() : UUID.randomUUID().toString());
                fact.setText(body.getFact());
                fact.setFetchedTimestamp(OffsetDateTime.now());
                catFactRepository.save(fact);
                logger.info("Fetched cat fact and saved id={}", fact.getId());

                // Trigger sending emails
                sendWeeklyEmailsService.sendEmailsForFact(fact);
            } else {
                logger.warn("Failed to fetch cat fact, status={}", resp.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Exception during weekly ingestion", e);
        }
    }

    // DTO for API response
    public static class CatFactResponse {
        private String fact;
        private String length;
        private String id; // some responses might include an id

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }

        public String getLength() {
            return length;
        }

        public void setLength(String length) {
            this.length = length;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
