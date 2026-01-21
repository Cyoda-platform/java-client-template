# API Endpoints Reference

## User Management Endpoints

### Create User
```
POST /ui/user
Content-Type: application/json

{
  "userId": "auth0|507f1f77bcf86cd799439011",
  "email": "user@example.com",
  "name": "John Doe",
  "presentInAuth0": true
}
```
Returns: 201 Created with Location header

### Get User by Technical ID
```
GET /ui/user/{id}
```
Returns: 200 OK with EntityWithMetadata<User>

### Update User
```
PUT /ui/user/{id}
Content-Type: application/json
?transition=UPDATE

{
  "userId": "auth0|507f1f77bcf86cd799439011",
  "email": "newemail@example.com",
  ...
}
```
Returns: 200 OK with updated entity

### Sync User from Auth0
```
POST /ui/user/{id}/sync-auth0
```
Triggers: SYNC_FROM_AUTH0 transition with Auth0SyncProcessor
Returns: 200 OK with synced entity

### Delete User
```
DELETE /ui/user/{id}
```
Returns: 204 No Content

---

## Environment Management Endpoints

### Create Environment
```
POST /ui/environment
Content-Type: application/json

{
  "environmentId": "auth0|507f1f77bcf86cd799439011-production",
  "userId": "auth0|507f1f77bcf86cd799439011",
  "name": "production",
  "markedForDeletion": false
}
```
Returns: 201 Created with Location header

### Get Environment by Technical ID
```
GET /ui/environment/{id}
```
Returns: 200 OK with EntityWithMetadata<Environment>

### Update Environment
```
PUT /ui/environment/{id}
Content-Type: application/json

{
  "environmentId": "...",
  "userId": "...",
  "name": "staging",
  ...
}
```
Returns: 200 OK with updated entity

### Delete Environment
```
DELETE /ui/environment/{id}
```
Returns: 204 No Content

---

## Error Responses
All endpoints return RFC 7807 ProblemDetail on errors:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to create user: ..."
}
```

## Workflow States
- **User Entity**: `initial` (single state with manual transitions)
- **Transitions**: `SYNC_FROM_AUTH0`, `UPDATE`

