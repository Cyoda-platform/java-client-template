```markdown
# Application Specification: Happy and Gloomy Email Sender (Java 21 Spring Boot)

## Overview
Build a Java 21 Spring Boot application that sends emails categorized as "happy" or "gloomy." The core design follows the Cyoda architecture with an entity, processors, and criteria for workflow-driven event processing.

---

## Entity: `Mail`
- **Fields:**
  - `isHappy` (Boolean)  
    - Determines whether the mail is classified as "happy" (`true`) or "gloomy" (`false`).
  - `mailList` (List<String>)  
    - List of recipient email addresses.

- **Role:**
  - Represents the mail object to be processed.
  - Drives the workflow based on the `isHappy` field.

---

## Processors
Two distinct processing components handle sending emails based on the `Mail` entity’s state:

1. **sendHappyMail**
   - Triggered when `isHappy == true`.
   - Responsible for sending "happy" emails to all recipients in `mailList`.
   - Implements the email sending logic tailored for happy mails (e.g., cheerful subject line, uplifting body content).
   
2. **sendGloomyMail**
   - Triggered when `isHappy == false`.
   - Responsible for sending "gloomy" emails to all recipients in `mailList`.
   - Implements the email sending logic tailored for gloomy mails (e.g., somber subject line, serious body content).

---

## Criteria
- **Happy Mail Criteria:**  
  - `isHappy == true`  
  - Used to route the mail entity to the `sendHappyMail` processor.

- **Gloomy Mail Criteria:**  
  - `isHappy == false`  
  - Used to route the mail entity to the `sendGloomyMail` processor.

---

## Technical Details and APIs

- **Framework:**  
  - Java 21 Spring Boot (leveraging modern features and compatibility with Cyoda architecture)
  
- **Email Sending API:**  
  - Use Spring's `JavaMailSender` interface for sending emails.
  - Configure SMTP server details in `application.properties` or `application.yml`.
  
- **Entity Definition:**
  ```java
  public class Mail {
      private Boolean isHappy;
      private List<String> mailList;

      // Getters and Setters
  }
  ```

- **Processor Interface:**
  ```java
  public interface MailProcessor {
      void process(Mail mail);
  }
  ```

- **sendHappyMail Implementation:**
  ```java
  @Service
  public class SendHappyMailProcessor implements MailProcessor {
      
      private final JavaMailSender mailSender;

      public SendHappyMailProcessor(JavaMailSender mailSender) {
          this.mailSender = mailSender;
      }

      @Override
      public void process(Mail mail) {
          if (Boolean.TRUE.equals(mail.getIsHappy())) {
              for (String recipient : mail.getMailList()) {
                  SimpleMailMessage message = new SimpleMailMessage();
                  message.setTo(recipient);
                  message.setSubject("Happy News!");
                  message.setText("Wishing you a joyful day!");
                  mailSender.send(message);
              }
          }
      }
  }
  ```

- **sendGloomyMail Implementation:**
  ```java
  @Service
  public class SendGloomyMailProcessor implements MailProcessor {
      
      private final JavaMailSender mailSender;

      public SendGloomyMailProcessor(JavaMailSender mailSender) {
          this.mailSender = mailSender;
      }

      @Override
      public void process(Mail mail) {
          if (Boolean.FALSE.equals(mail.getIsHappy())) {
              for (String recipient : mail.getMailList()) {
                  SimpleMailMessage message = new SimpleMailMessage();
                  message.setTo(recipient);
                  message.setSubject("Gloomy News");
                  message.setText("We hope things get better soon.");
                  mailSender.send(message);
              }
          }
      }
  }
  ```

---

## Workflow Trigger
- The workflow is triggered by creating or receiving a `Mail` entity.
- Based on the `isHappy` field, the workflow routes the mail entity to either:
  - `sendHappyMail` processor (if happy)
  - `sendGloomyMail` processor (if gloomy)

---

## Summary
This design preserves all business logic and technical requirements:
- One entity `Mail` with `isHappy` and `mailList`.
- Two processors `sendHappyMail` and `sendGloomyMail`.
- Criteria to route mails based on the `isHappy` boolean.
- Java 21 Spring Boot with Spring Mail integration for email sending.

If you want, I can provide the complete project structure, configuration, and full source code to implement this application following Cyoda principles.
```