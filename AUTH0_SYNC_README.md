# Auth0 User Synchronization Workflow

This document describes the Auth0 user synchronization workflow implementation, including the two processors: `Auth0Fetch` and `MergeUsers`.

## Overview

The ManualUserSync workflow synchronizes users from Auth0 into the local Cyoda-managed user store using an Auth0-authoritative merge policy. The workflow consists of two main processors:

1. **Auth0Fetch** - Retrieves users from Auth0 Management API with pagination
2. **MergeUsers** - Merges fetched users into local store with Auth0-authoritative policy

## Configuration

### Environment Variables

Configure the following environment variables for Auth0 integration:

```bash
# Auth0 Configuration
export AUTH0_DOMAIN=your-tenant.auth0.com
export AUTH0_CLIENT_ID=your-client-id
export AUTH0_CLIENT_SECRET=your-client-secret
export AUTH0_AUDIENCE=https://your-tenant.auth0.com/api/v2/
export AUTH0_PAGE_SIZE=100
```

### Application Properties

Add to `application.properties` or `application.yml`:

```yaml
auth0:
  domain: ${AUTH0_DOMAIN}
  clientId: ${AUTH0_CLIENT_ID}
  clientSecret: ${AUTH0_CLIENT_SECRET}
  audience: ${AUTH0_AUDIENCE}
  pageSize: 100
```

## Entities

### User Entity
- **Location**: `src/main/java/com/java_template/application/entity/user/version_1/User.java`
- **Fields**: id, auth0Id, email, name, metadata, presentInAuth0, environments, createdAt, updatedAt
- **JSON Example**: `src/main/resources/entity/user/version_1/user.json`

### ManualUserSync Entity
- **Location**: `src/main/java/com/java_template/application/entity/manualusersync/version_1/ManualUserSync.java`
- **Fields**: id, name, description, initiatedBy, startedAt, finishedAt, status, syncParameters, results, auth0FetchCursor, createdAt, updatedAt
- **JSON Example**: `src/main/resources/entity/ManualUserSync/version_1/ManualUserSync.json`

## Processors

### Auth0Fetch Processor
- **Class**: `com.java_template.application.processor.Auth0Fetch`
- **Purpose**: Fetch users from Auth0 Management API
- **Execution Mode**: SYNC
- **Attach Entity**: false (non-attaching)
- **Timeout**: 60 seconds
- **Retry Policy**: FIXED

**Features**:
- Client credentials OAuth2 flow for token acquisition
- Pagination support (configurable page size, default 100)
- Includes user_metadata, app_metadata, and identity provider attributes
- Robust error handling with detailed logging

### MergeUsers Processor
- **Class**: `com.java_template.application.processor.MergeUsers`
- **Purpose**: Merge Auth0 users into local store
- **Execution Mode**: SYNC
- **Attach Entity**: true (attaching)
- **Timeout**: 120 seconds
- **Retry Policy**: EXPONENTIAL

**Features**:
- Auth0-authoritative merge policy
- Creates new users from Auth0
- Updates existing users with Auth0 values
- Preserves local-only fields (environments)
- Marks missing users as not present in Auth0
- Tracks sync results (created, updated, missing counts)

## Workflow States

The ManualUserSync workflow has the following states:

- **idle**: Initial state, waiting for sync trigger
- **syncing**: Active sync in progress (runs Auth0Fetch and MergeUsers)
- **completed**: Sync completed successfully
- **failed**: Sync failed

## Running the Workflow

### Via CLI

```bash
# Start a manual sync
./gradlew bootRun --args='--workflow=ManualUserSync --action=start_sync'
```

### Via REST API

```bash
# Trigger sync transition
POST /ui/manualusersync/transition
{
  "entityId": "sync-entity-id",
  "transition": "start_sync"
}
```

## Testing

### Run All Tests

```bash
./gradlew test
```

### Run Auth0Fetch Tests

```bash
./gradlew test --tests Auth0FetchTest
```

### Run MergeUsers Tests

```bash
./gradlew test --tests MergeUsersTest
```

## Build and Validation

### Compile

```bash
./gradlew clean compileJava
```

### Validate Workflow Implementations

```bash
./gradlew validateWorkflowImplementations
```

### Full Build

```bash
./gradlew build
```

## Merge Policy Details

### Auth0-Authoritative Policy

When a user exists in both Auth0 and locally:
- **Overwritten**: email, name, metadata (caas_* fields)
- **Preserved**: environments array, local-only fields
- **Updated**: presentInAuth0 = true, updatedAt timestamp

### New User Creation

When a user exists in Auth0 but not locally:
- Create new local user record
- Set presentInAuth0 = true
- Initialize environments = []
- Set createdAt and updatedAt timestamps

### Missing User Handling

When a user exists locally but not in Auth0:
- Set presentInAuth0 = false
- Preserve environments array
- Preserve all local data

## Error Handling

Both processors implement robust error handling:
- Transient HTTP errors trigger exponential backoff retry
- Detailed error messages logged and attached to sync results
- Sync continues even if individual user merge fails
- All errors tracked in ManualUserSync.results.errors

## Date/Time Format

All date/time fields use ISO-8601 format with OffsetDateTime:
- Example: `2026-01-21T14:30:00Z`
- Timezone-aware for accurate audit trails

## Security Notes

- Auth0 credentials are managed via environment variables
- Never commit secrets to repository
- Use placeholder values in configuration templates
- Credentials are injected at deployment time by Environment agent

