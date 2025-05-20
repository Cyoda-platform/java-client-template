```markdown
# Final Functional Requirements for Weekly Cat Fact Subscription Application

## 1. User Subscription Management

- Users can subscribe by providing their email address.
- Users can unsubscribe via a link in the email; unsubscribe immediately removes them from the active subscriber list but retains their historical data indefinitely.
- Users can resubscribe after unsubscribing.
- Email validation is enforced during subscription and unsubscription.
- Confirmation email is sent upon successful unsubscribe.

## 2. Cat Fact Ingestion and Email Sending

- The system retrieves a new cat fact from the external Cat Fact API once a week, regardless of previous email send status.
- Retrieved cat facts are stored weekly.
- Weekly cat facts are sent immediately after ingestion to all active subscribers.
- The system supports manual triggering of the weekly email send (e.g., via an admin dashboard).
- Failed email sends are retried up to 3 times.

## 3. Interaction Tracking and Reporting

- Track aggregated weekly counts of email opens and clicks per subscriber.
- Store all interaction and subscriber data indefinitely, including for unsubscribed users.
- Provide an admin dashboard reporting current subscriber count and weekly interaction summaries.
- Reporting supports filtering by date ranges.
- Reports are view-only; no data export/download is required.

## 4. API Behavior Rules

- All business logic invoking external data sources or performing calculations uses POST endpoints.
- GET endpoints are used only for retrieving stored application data.
- API endpoints support necessary validation and error responses.

---

## Example API Endpoints Summary

| Endpoint                         | Method | Description                                   |
|---------------------------------|--------|-----------------------------------------------|
| `/api/subscribers`               | POST   | Subscribe user with email                      |
| `/api/subscribers/unsubscribe`  | POST   | Unsubscribe user by email                      |
| `/api/subscribers/resubscribe`  | POST   | Resubscribe previously unsubscribed user      |
| `/api/facts/ingest-and-send`    | POST   | Retrieve cat fact and send weekly emails      |
| `/api/facts/manual-send`        | POST   | Manually trigger weekly email send            |
| `/api/reports/subscribers-count`| GET    | Retrieve active subscriber count               |
| `/api/reports/interaction-summary` | GET | Retrieve aggregated weekly email interactions  |

---

Feel free to ask if you'd like detailed endpoint request/response specs or interaction diagrams next.
```