# GitHub Actions Build Status - Curl Instructions

## Prerequisites

Set your GitHub token as an environment variable:
```bash
export GITHUB_TOKEN="your_github_token_here"
```

## 1. Trigger a Build

### Basic Trigger (Standard Build)
```bash
curl -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{
    "ref": "your-branch-name",
    "inputs": {
      "branch": "your-branch-name",
      "build_type": "standard"
    }
  }'
```

### Compilation Check Only
```bash
curl -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{
    "ref": "your-branch-name",
    "inputs": {
      "branch": "your-branch-name",
      "build_type": "compile-only"
    }
  }'
```

### Build Both JARs
```bash
curl -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{
    "ref": "your-branch-name",
    "inputs": {
      "branch": "your-branch-name",
      "build_type": "both"
    }
  }'
```

**Response:** HTTP 204 (No Content) on success

## 2. Get Build Status

### Get Latest Workflow Run
```bash
curl -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1"
```

### Get Latest Run Status (Simplified)
```bash
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq '.workflow_runs[0] | {
      id: .id,
      status: .status,
      conclusion: .conclusion,
      branch: .head_branch,
      commit: .head_sha[0:7],
      actor: .actor.login,
      created_at: .created_at,
      html_url: .html_url
    }'
```

### Get Specific Run Details
```bash
# Replace RUN_ID with actual run ID
curl -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID"
```

### Get Run Status Only
```bash
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID" \
  | jq '{status: .status, conclusion: .conclusion}'
```

## 3. Monitor Build Progress

### Check if Build is Complete
```bash
# Returns "completed" when done
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID" \
  | jq -r '.status'
```

### Get Build Result
```bash
# Returns "success", "failure", "cancelled", etc.
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID" \
  | jq -r '.conclusion'
```

### Simple Status Check Loop
```bash
#!/bin/bash
RUN_ID="your_run_id"
while true; do
  STATUS=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
    "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/$RUN_ID" \
    | jq -r '.status')
  
  echo "Status: $STATUS"
  
  if [ "$STATUS" = "completed" ]; then
    CONCLUSION=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/$RUN_ID" \
      | jq -r '.conclusion')
    echo "Result: $CONCLUSION"
    break
  fi
  
  sleep 10
done
```

## 4. Get Build Artifacts

### List Artifacts for a Run
```bash
curl -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID/artifacts"
```

### Download Artifact
```bash
# Get artifact download URL first
ARTIFACT_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/RUN_ID/artifacts" \
  | jq -r '.artifacts[0].id')

# Download artifact
curl -L -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/artifacts/$ARTIFACT_ID/zip" \
  -o artifact.zip
```

## 5. Response Examples

### Workflow Run Response
```json
{
  "id": 1234567890,
  "status": "completed",
  "conclusion": "success",
  "head_branch": "main",
  "head_sha": "abc123def456",
  "actor": {
    "login": "username"
  },
  "created_at": "2024-01-01T12:00:00Z",
  "updated_at": "2024-01-01T12:05:00Z",
  "html_url": "https://github.com/Cyoda-platform/java-client-template/actions/runs/1234567890",
  "inputs": {
    "branch": "main",
    "build_type": "standard"
  }
}
```

### Status Values
- **status**: `queued`, `in_progress`, `completed`
- **conclusion**: `success`, `failure`, `cancelled`, `skipped`, `timed_out`, `action_required`

## 6. Error Handling

### Check for Errors
```bash
response=$(curl -s -w "%{http_code}" -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1")

http_code="${response: -3}"
body="${response%???}"

if [ "$http_code" -ne 200 ]; then
  echo "Error: HTTP $http_code"
  echo "$body"
  exit 1
fi

echo "$body" | jq '.'
```

### Common Error Codes
- **401**: Invalid or missing token
- **403**: Insufficient permissions
- **404**: Repository or workflow not found
- **422**: Invalid request (e.g., workflow doesn't have workflow_dispatch trigger)

## 7. Getting Run ID After Triggering

### The Problem
The POST `/dispatches` endpoint returns HTTP 204 with no run ID. You need to query the `/runs` endpoint to find your triggered run.

### Method 1: Get Latest Run (Simple)
```bash
# 1. Trigger workflow
curl -X POST -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{"ref":"main","inputs":{"branch":"main","build_type":"compile-only"}}'

# 2. Wait for run to appear (usually 2-10 seconds)
sleep 5

# 3. Get latest run ID
RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq -r '.workflow_runs[0].id')

echo "Run ID: $RUN_ID"
```

### Method 2: Filter by Timestamp (More Reliable)
```bash
# Get timestamp before triggering
TRIGGER_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Trigger workflow
curl -X POST -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{"ref":"main","inputs":{"branch":"main","build_type":"compile-only"}}'

# Wait and find runs created after trigger time
sleep 5
RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
  | jq -r --arg trigger_time "$TRIGGER_TIME" \
  '.workflow_runs[] | select(.created_at > $trigger_time) | .id' | head -1)

echo "Run ID: $RUN_ID"
```

### Method 3: Filter by Actor and Branch
```bash
# Get your GitHub username
GITHUB_USER=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  https://api.github.com/user | jq -r '.login')

# Trigger workflow
curl -X POST -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{"ref":"feature-branch","inputs":{"branch":"feature-branch","build_type":"standard"}}'

# Find your most recent run on the specific branch
sleep 5
RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
  | jq -r --arg user "$GITHUB_USER" --arg branch "feature-branch" \
  '.workflow_runs[] | select(.actor.login == $user and .head_branch == $branch) | .id' | head -1)

echo "Run ID: $RUN_ID"
```

### Method 4: Complete Script with Retry Logic
```bash
#!/bin/bash
trigger_and_get_run_id() {
  local branch="$1"
  local build_type="$2"
  local max_attempts=12

  # Get baseline run ID before triggering
  local baseline_id=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
    "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
    | jq -r '.workflow_runs[0].id // "0"')

  # Trigger workflow
  local http_code=$(curl -s -w "%{http_code}" -X POST \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Content-Type: application/json" \
    https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
    -d "{\"ref\":\"$branch\",\"inputs\":{\"branch\":\"$branch\",\"build_type\":\"$build_type\"}}" \
    | tail -c 3)

  if [[ "$http_code" != "204" ]]; then
    echo "Error: Failed to trigger workflow (HTTP $http_code)" >&2
    return 1
  fi

  # Wait for new run to appear
  for ((i=1; i<=max_attempts; i++)); do
    sleep 3
    local latest_id=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
      | jq -r '.workflow_runs[0].id // "0"')

    if [[ "$latest_id" != "$baseline_id" && "$latest_id" != "0" ]]; then
      echo "$latest_id"
      return 0
    fi
  done

  echo "Error: Timeout waiting for run to appear" >&2
  return 1
}

# Usage
RUN_ID=$(trigger_and_get_run_id "main" "compile-only")
echo "Run ID: $RUN_ID"
```

## 8. One-Liner Examples

### Quick Status Check
```bash
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq -r '.workflow_runs[0] | "\(.status) - \(.conclusion // "in_progress") - \(.head_branch)"'
```
