# Weekly Cat Fact Subscription - Functional Requirements

Overview

A service that retrieves a new cat fact each week from the Cat Fact API (https://catfact.ninja/#/Facts/getRandomFact) and emails it to subscribed users. The application must support simple email-only subscriptions with opt-in and basic reporting.

Functional Requirements

1. Subscriber Management
- Sign up: Users can subscribe with email and explicit opt-in flag.
- Unsubscribe: Users can unsubscribe via a link in the email.
- Data stored: email (primary key), opt_in_timestamp, unsubscribed_flag, unsubscribe_timestamp.

2. Weekly Fact Ingestion
- Scheduled job: A scheduler triggers a job once a week to fetch a random cat fact from the Cat Fact API.
- Facts storage: Store each fetched fact with id, text, fetched_timestamp.

3. Email Publishing
- Send: After ingestion, the system sends the fetched fact via email to all active subscribers.
- Retry: Implement retries for transient email send failures (exponential backoff, 3 attempts).

4. Reporting
- Basic counts: Track number of subscribers, number of emails sent per week, number of bounces/failures.
- Admin endpoints: Expose endpoints to retrieve weekly report summaries.

5. Security & Compliance
- Opt-in: Ensure explicit opt-in is tracked.
- Data retention: Provide configuration for data retention of subscriber records.

Non-Functional Requirements

- Language: Java (Spring Boot preferred)
- Scheduling: Use the application's scheduler to run weekly jobs.
- Email Provider: Configurable SMTP provider (credentials via environment variables).
- Observability: Basic logging of ingestion and send operations.

