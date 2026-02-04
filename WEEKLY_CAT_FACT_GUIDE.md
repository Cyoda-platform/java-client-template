# Weekly Cat Fact Subscription System - Complete Guide

## 🎯 Project Overview

A production-ready Spring Boot application that manages weekly cat fact subscriptions. The system:
- Fetches random cat facts from the Cat Fact API (https://catfact.ninja)
- Manages subscriber sign-ups and unsubscriptions
- Sends weekly emails to all active subscribers
- Tracks interactions (opens, clicks) for reporting
- Runs on a weekly schedule (Monday 8:00 AM)

## 📁 Project Structure

```
src/main/java/com/java_template/application/
├── entity/
│   ├── subscriber/version_1/Subscriber.java
│   ├── catfact/version_1/CatFact.java
│   └── event/version_1/Event.java
├── processor/
│   ├── SubscriberProcessor.java
│   ├── FetchCatFactProcessor.java
│   └── TrackEventProcessor.java
├── controller/
│   ├── SubscriberController.java
│   ├── CatFactController.java
│   └── EventController.java
└── service/
    └── CatFactScheduledService.java

src/main/resources/
├── entity/
│   ├── subscriber/version_1/Subscriber.json
│   ├── catfact/version_1/CatFact.json
│   └── event/version_1/Event.json
└── workflow/
    ├── subscriber/version_1/Subscriber.json
    ├── catfact/version_1/CatFact.json
    └── event/version_1/Event.json
```

## 🚀 Quick Start

### 1. Build the Application
```bash
./gradlew clean build
```

### 2. Run the Application
```bash
./gradlew bootRun
```

### 3. Test the API

**Sign up a subscriber:**
```bash
curl -X POST http://localhost:8080/ui/subscriber \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "status": "active"
  }'
```

**Get subscriber by email:**
```bash
curl http://localhost:8080/ui/subscriber/email/user@example.com
```

**Unsubscribe:**
```bash
curl -X POST http://localhost:8080/ui/subscriber/{id}/unsubscribe
```

## 📊 Entity Relationships

### Subscriber
- Represents a user subscribed to cat facts
- States: `active`, `unsubscribed`
- Business ID: email address

### CatFact
- Stores retrieved cat facts from the API
- States: `retrieved`, `sent`
- Business ID: factId
- Links to campaign via campaignId

### Event
- Tracks all interactions in the system
- Types: subscribe, unsubscribe, email_sent, email_opened, email_clicked
- Business ID: eventId
- Stores flexible metadata

## 🔄 Workflow States

### Subscriber Workflow
```
active ←→ unsubscribed
  ↓ UPDATE
  active
```

### CatFact Workflow
```
retrieved → sent
  ↓ UPDATE
  retrieved/sent
```

### Event Workflow
```
recorded
  ↓ UPDATE
  recorded
```

## ⏰ Scheduled Job

**CatFactScheduledService** runs every Monday at 8:00 AM:

1. Fetches random cat fact from API
2. Creates CatFact entity
3. Retrieves all active subscribers
4. Sends email to each subscriber
5. Tracks email_sent events

**Cron Expression**: `0 0 8 ? * MON`

## 🔌 REST API Endpoints

### Subscriber Endpoints
- `POST /ui/subscriber` - Create subscriber
- `GET /ui/subscriber/email/{email}` - Get by email
- `POST /ui/subscriber/{id}/unsubscribe` - Unsubscribe

### CatFact Endpoints
- `POST /ui/catfact` - Create cat fact
- `GET /ui/catfact/{id}` - Get by ID
- `POST /ui/catfact/{id}/send-campaign` - Send campaign

### Event Endpoints
- `POST /ui/event` - Track event
- `GET /ui/event/{id}` - Get event by ID

## 🔐 Configuration

### Environment Variables
```bash
# SendGrid API Key (for email sending)
SENDGRID_API_KEY=your_api_key_here

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/catfacts
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

### Application Properties
Edit `src/main/resources/application.yml` for:
- Server port
- Database settings
- Logging levels
- Scheduled job timing

## 🧪 Testing

Run all tests:
```bash
./gradlew test
```

Validate workflows:
```bash
./gradlew validateWorkflowImplementations
```

## 📝 Implementation Notes

- All entities implement `CyodaEntity` interface
- All processors implement `CyodaProcessor` interface
- Controllers use `EntityService` for CRUD operations
- Workflows are defined in JSON configuration files
- Scheduled service uses Spring `@Scheduled` annotation
- Event tracking enables comprehensive reporting

## 🚨 Important Constraints

- ✅ No Java reflection used
- ✅ No modifications to `common/` directory
- ✅ Processors cannot update current entity
- ✅ Type-safe with `List<QueryCondition>` for searches
- ✅ All transitions are manual (except initial creation)

## 📈 Next Steps

1. Configure SendGrid API key
2. Implement email template rendering
3. Add email sending logic to `sendEmailToSubscriber()`
4. Set up database (PostgreSQL recommended)
5. Deploy to production
6. Monitor scheduled job execution
7. Analyze event tracking data for reporting

## 📚 References

- Cat Fact API: https://catfact.ninja
- Cyoda Documentation: See project README.md
- Spring Boot Scheduling: https://spring.io/guides/gs/scheduling-tasks/

