# Weekly Cat Fact Subscription - Functional Requirements

Overview
--------
An application that retrieves a cat fact weekly from the Cat Fact API (https://catfact.ninja/#/Facts/getRandomFact) and sends it via email to all subscribers.

Core Features
-------------
1. Data Ingestion
   - A scheduled job that retrieves a random cat fact from the Cat Fact API once per week.
   - Persist retrieved facts to allow reporting and re-sends if needed.

2. User Interaction
   - A simple sign-up endpoint and UI allowing users to subscribe with their email address only.
   - Unsubscribe endpoint/link included in each email.

3. Publishing
   - Integrate with SendGrid for sending weekly emails with the retrieved cat fact.
   - Include unsubscribe link and minimal tracking pixel for opens.

4. Reporting
   - Track number of subscribers, emails sent per campaign, open rates, and click rates.
   - Store events for reporting: subscribe, unsubscribe, email_sent, email_opened, email_clicked.

Non-functional Requirements
---------------------------
- Use Java (template from Cyoda Java client template).
- Secure storage of sensitive config (SendGrid API key) via environment variables in Cyoda.
- Minimal latency for signup API responses.

Scheduling
----------
- The ingestion/publish job must run once a week; job should be configurable via environment settings.

Data Model (high level)
------------------------
- Subscriber: email, subscribed_at, unsubscribed_at, status
- CatFact: id, text, retrieved_at
- Event: id, type, timestamp, metadata

Deliverables
------------
- Java application using Cyoda patterns: entities, workflows, processors, and a weekly scheduled job.
- Integration tests for the ingestion and send pipeline.
- Basic frontend signup page (static HTML) and REST endpoints for subscribe/unsubscribe.

