# Subscriber Controllers

## SubscriberController

REST API for managing email subscribers and subscription preferences.

### Endpoints

#### POST /api/subscribers
Create a new subscriber
- **Request Body**: Subscriber entity (without ID)
- **Response**: EntityWithMetadata<Subscriber>
- **Transition**: None (creates in initial_state)

**Request Example**:
```json
{
  "email": "user@example.com",
  "name": "John Doe",
  "emailPreferences": {
    "frequency": "IMMEDIATE",
    "format": "HTML",
    "topics": ["housing", "analysis"]
  }
}
```

**Response Example**:
```json
{
  "entity": {
    "subscriberId": "sub-789",
    "email": "user@example.com",
    "name": "John Doe",
    "emailPreferences": {
      "frequency": "IMMEDIATE", 
      "format": "HTML",
      "topics": ["housing", "analysis"]
    }
  },
  "meta": {
    "uuid": "uuid-789",
    "state": "created"
  }
}
```

#### PUT /api/subscribers/{id}/activate
Activate subscription
- **Path Parameter**: id (Subscriber UUID)
- **Transition**: activate_subscription
- **Response**: EntityWithMetadata<Subscriber>

#### PUT /api/subscribers/{id}/unsubscribe
Unsubscribe user
- **Path Parameter**: id (Subscriber UUID)
- **Transition**: unsubscribe
- **Response**: EntityWithMetadata<Subscriber>

#### GET /api/subscribers/{id}
Get subscriber by ID
- **Path Parameter**: id (Subscriber UUID)
- **Response**: EntityWithMetadata<Subscriber>
