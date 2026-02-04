# Weekly Cat Fact Subscription - API Examples

## Subscriber API

### 1. Sign Up (Create Subscriber)
```bash
curl -X POST http://localhost:8080/ui/subscriber \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "status": "active"
  }'
```

**Response (201 Created):**
```json
{
  "entity": {
    "email": "alice@example.com",
    "status": "active",
    "subscribedAt": "2025-01-20T10:30:00",
    "unsubscribedAt": null
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "active",
    "version": 1
  }
}
```

### 2. Get Subscriber by Email
```bash
curl http://localhost:8080/ui/subscriber/email/alice@example.com
```

### 3. Unsubscribe
```bash
curl -X POST http://localhost:8080/ui/subscriber/550e8400-e29b-41d4-a716-446655440000/unsubscribe
```

## CatFact API

### 1. Create Cat Fact
```bash
curl -X POST http://localhost:8080/ui/catfact \
  -H "Content-Type: application/json" \
  -d '{
    "factId": "fact-2025-01-20-001",
    "text": "Cats have over 20 different vocalizations.",
    "retrievedAt": "2025-01-20T08:00:00",
    "campaignId": "campaign-2025-01-20"
  }'
```

**Response (201 Created):**
```json
{
  "entity": {
    "factId": "fact-2025-01-20-001",
    "text": "Cats have over 20 different vocalizations.",
    "retrievedAt": "2025-01-20T08:00:00",
    "campaignId": "campaign-2025-01-20"
  },
  "metadata": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "state": "retrieved",
    "version": 1
  }
}
```

### 2. Get Cat Fact by ID
```bash
curl http://localhost:8080/ui/catfact/660e8400-e29b-41d4-a716-446655440001
```

### 3. Send Campaign
```bash
curl -X POST http://localhost:8080/ui/catfact/660e8400-e29b-41d4-a716-446655440001/send-campaign
```

## Event API

### 1. Track Event (Email Sent)
```bash
curl -X POST http://localhost:8080/ui/event \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "event-2025-01-20-001",
    "type": "email_sent",
    "timestamp": "2025-01-20T09:00:00",
    "metadata": {
      "subscriberEmail": "alice@example.com",
      "campaignId": "campaign-2025-01-20",
      "factId": "fact-2025-01-20-001"
    }
  }'
```

### 2. Track Event (Email Opened)
```bash
curl -X POST http://localhost:8080/ui/event \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "event-2025-01-20-002",
    "type": "email_opened",
    "timestamp": "2025-01-20T10:15:00",
    "metadata": {
      "subscriberEmail": "alice@example.com",
      "campaignId": "campaign-2025-01-20"
    }
  }'
```

### 3. Track Event (Email Clicked)
```bash
curl -X POST http://localhost:8080/ui/event \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "event-2025-01-20-003",
    "type": "email_clicked",
    "timestamp": "2025-01-20T10:20:00",
    "metadata": {
      "subscriberEmail": "alice@example.com",
      "campaignId": "campaign-2025-01-20",
      "linkClicked": "https://catfact.ninja"
    }
  }'
```

### 4. Get Event by ID
```bash
curl http://localhost:8080/ui/event/770e8400-e29b-41d4-a716-446655440002
```

## Error Responses

### 409 Conflict (Duplicate)
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Subscriber already exists with email: alice@example.com"
}
```

### 400 Bad Request
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to create subscriber: Invalid email format"
}
```

### 404 Not Found
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Resource not found"
}
```

## Workflow Transitions

### Subscriber Transitions
- `UPDATE` - Update subscriber data (stays in current state)
- `UNSUBSCRIBE` - Move from active to unsubscribed
- `RESUBSCRIBE` - Move from unsubscribed back to active

### CatFact Transitions
- `UPDATE` - Update fact data (stays in current state)
- `SEND_CAMPAIGN` - Move from retrieved to sent

### Event Transitions
- `UPDATE` - Update event data (stays in current state)

## Testing with Postman

1. Import the API endpoints into Postman
2. Set base URL: `http://localhost:8080`
3. Create subscriber
4. Create cat fact
5. Track events
6. Verify data in database

## Batch Operations

### Create Multiple Subscribers
```bash
for i in {1..5}; do
  curl -X POST http://localhost:8080/ui/subscriber \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"user$i@example.com\", \"status\": \"active\"}"
done
```

### Track Multiple Events
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8080/ui/event \
    -H "Content-Type: application/json" \
    -d "{\"eventId\": \"event-$i\", \"type\": \"email_sent\", \"timestamp\": \"2025-01-20T09:00:00\", \"metadata\": {}}"
done
```

