package com.java_template.prototype;

import com.java_template.application.entity.Mail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mail")
    public ResponseEntity<?> createMail(@RequestBody MailRequest mailRequest) {
        if (mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty()) {
            log.error("Mail list is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Mail list cannot be empty");
        }
        if (mailRequest.getContent() == null || mailRequest.getContent().isBlank()) {
            log.error("Content is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Content cannot be blank");
        }

        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        Mail mail = new Mail();
        mail.setMailList(mailRequest.getMailList());
        mail.setContent(mailRequest.getContent());
        mail.setMoodCriteriaChecked(false);
        mail.setIsHappy(null); // initially unknown

        mailCache.put(technicalId, mail);

        try {
            processMail(technicalId, mail);
            log.info("Mail processed successfully with technicalId {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing mail with technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new TechnicalIdResponse(technicalId));
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }
        MailResponse response = new MailResponse();
        response.setTechnicalId(technicalId);
        response.setMailList(mail.getMailList());
        response.setContent(mail.getContent());
        response.setIsHappy(mail.getIsHappy());
        response.setMoodCriteriaChecked(mail.getMoodCriteriaChecked());
        response.setStatus("SENT"); // Assuming mail is sent after processing
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/mail", params = "isHappy")
    public ResponseEntity<?> getMailsByMood(@RequestParam Boolean isHappy) {
        List<MailResponse> filteredMails = mailCache.entrySet().stream()
                .filter(entry -> isHappy.equals(entry.getValue().getIsHappy()))
                .map(entry -> {
                    Mail mail = entry.getValue();
                    MailResponse response = new MailResponse();
                    response.setTechnicalId(entry.getKey());
                    response.setMailList(mail.getMailList());
                    response.setContent(mail.getContent());
                    response.setIsHappy(mail.getIsHappy());
                    response.setMoodCriteriaChecked(mail.getMoodCriteriaChecked());
                    response.setStatus("SENT");
                    return response;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(String technicalId, Mail mail) {
        // Step 1: Validate criteria
        boolean happyCriteriaMet = checkMailHappyCriteria(mail);
        boolean gloomyCriteriaMet = checkMailGloomyCriteria(mail);

        // Step 2: Assign mood based on criteria
        if (happyCriteriaMet) {
            mail.setIsHappy(true);
            sendHappyMail(technicalId, mail);
        } else if (gloomyCriteriaMet) {
            mail.setIsHappy(false);
            sendGloomyMail(technicalId, mail);
        } else {
            // Neither criteria met - optional manual review or log warning
            log.warn("Mail with technicalId {} did not meet any mood criteria", technicalId);
            mail.setIsHappy(null);
        }

        mail.setMoodCriteriaChecked(true);
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Example logic: If content contains "happy" or "joy"
        String contentLower = mail.getContent().toLowerCase();
        return contentLower.contains("happy") || contentLower.contains("joy");
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Example logic: If content contains "sad" or "gloom"
        String contentLower = mail.getContent().toLowerCase();
        return contentLower.contains("sad") || contentLower.contains("gloom");
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail [{}] to recipients: {}", technicalId, mail.getMailList());
        // Here would be actual mail sending code or integration with mail service
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail [{}] to recipients: {}", technicalId, mail.getMailList());
        // Here would be actual mail sending code or integration with mail service
    }

    // DTO classes for request and response

    public static class MailRequest {
        private List<String> mailList;
        private String content;

        public List<String> getMailList() {
            return mailList;
        }

        public void setMailList(List<String> mailList) {
            this.mailList = mailList;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class TechnicalIdResponse {
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    public static class MailResponse {
        private String technicalId;
        private List<String> mailList;
        private String content;
        private Boolean isHappy;
        private Boolean moodCriteriaChecked;
        private String status;

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public List<String> getMailList() {
            return mailList;
        }

        public void setMailList(List<String> mailList) {
            this.mailList = mailList;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Boolean getIsHappy() {
            return isHappy;
        }

        public void setIsHappy(Boolean isHappy) {
            this.isHappy = isHappy;
        }

        public Boolean getMoodCriteriaChecked() {
            return moodCriteriaChecked;
        }

        public void setMoodCriteriaChecked(Boolean moodCriteriaChecked) {
            this.moodCriteriaChecked = moodCriteriaChecked;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}