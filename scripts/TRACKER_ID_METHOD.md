# GitHub Actions with Unique Tracker ID Method

This is the most reliable method to trigger a workflow and get the exact run ID by using a unique tracker identifier.

## Prerequisites

```bash
export GITHUB_TOKEN="your_github_token_here"
```

## Method Overview

1. **Generate unique tracker ID**
2. **Trigger workflow with tracker ID**
3. **Find run by filtering on tracker ID**

## 1. Complete Example

```bash
#!/bin/bash

# Configuration
OWNER="Cyoda-platform"
REPO="java-client-template"
WORKFLOW="build.yml"
BRANCH="your-branch-name"
BUILD_TYPE="compile-only"

# Generate unique tracker ID
TRACKER_ID="build-$(date +%s)-$$-$(openssl rand -hex 4)"
echo "Generated Tracker ID: $TRACKER_ID"

# Trigger workflow with tracker ID
echo "Triggering workflow..."
HTTP_CODE=$(curl -s -w "%{http_code}" -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW}/dispatches" \
  -d "{
    \"ref\": \"${BRANCH}\",
    \"inputs\": {
      \"branch\": \"${BRANCH}\",
      \"build_type\": \"${BUILD_TYPE}\",
      \"tracker_id\": \"${TRACKER_ID}\"
    }
  }" | tail -c 3)

if [[ "$HTTP_CODE" != "204" ]]; then
    echo "Error: Failed to trigger workflow (HTTP $HTTP_CODE)"
    exit 1
fi

echo "✅ Workflow triggered successfully!"

# Wait for run to appear and find by tracker ID
echo "Waiting for run to appear..."
MAX_ATTEMPTS=15
RUN_ID=""

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    sleep 3
    
    RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW}/runs" \
      | jq -r --arg tracker "$TRACKER_ID" \
      '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)
    
    if [[ -n "$RUN_ID" && "$RUN_ID" != "null" ]]; then
        echo "✅ Found run with tracker ID!"
        break
    fi
    
    echo "Attempt $i/$MAX_ATTEMPTS: Run not found yet..."
done

if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
    echo "❌ Error: Could not find run with tracker ID: $TRACKER_ID"
    exit 1
fi

echo "🎉 Success!"
echo "Tracker ID: $TRACKER_ID"
echo "Run ID: $RUN_ID"
echo "URL: https://github.com/${OWNER}/${REPO}/actions/runs/${RUN_ID}"
```

## 2. Step-by-Step Breakdown

### Step 1: Generate Unique Tracker ID

```bash
# Method 1: Timestamp + Process ID + Random
TRACKER_ID="build-$(date +%s)-$$-$(openssl rand -hex 4)"

# Method 2: UUID (if available)
TRACKER_ID="build-$(uuidgen)"

# Method 3: Simple timestamp
TRACKER_ID="build-$(date +%Y%m%d-%H%M%S)-$(shuf -i 1000-9999 -n 1)"

echo "Tracker ID: $TRACKER_ID"
```

### Step 2: Trigger with Tracker ID

```bash
curl -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d "{
    \"ref\": \"main\",
    \"inputs\": {
      \"branch\": \"main\",
      \"build_type\": \"compile-only\",
      \"tracker_id\": \"${TRACKER_ID}\"
    }
  }"
```

### Step 3: Find Run by Tracker ID

```bash
# Wait for run to appear
sleep 5

# Find run by tracker ID
RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
  | jq -r --arg tracker "$TRACKER_ID" \
  '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)

echo "Run ID: $RUN_ID"
```

### Step 4: Monitor the Run

```bash
# Get run status
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/runs/${RUN_ID}" \
  | jq '{id, status, conclusion, html_url}'
```

## 3. One-Liner Functions

### Trigger and Get Run ID
```bash
trigger_with_tracker() {
    local branch="$1"
    local build_type="$2"
    local tracker_id="build-$(date +%s)-$$-$(openssl rand -hex 4)"
    
    # Trigger
    curl -X POST -H "Authorization: token ${GITHUB_TOKEN}" \
      -H "Content-Type: application/json" \
      https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
      -d "{\"ref\":\"$branch\",\"inputs\":{\"branch\":\"$branch\",\"build_type\":\"$build_type\",\"tracker_id\":\"$tracker_id\"}}"
    
    # Wait and find
    sleep 5
    local run_id=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
      | jq -r --arg tracker "$tracker_id" \
      '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)
    
    echo "Tracker: $tracker_id"
    echo "Run ID: $run_id"
}

# Usage
trigger_with_tracker "main" "compile-only"
```

## 4. Advanced Filtering

### Get All Runs with Tracker IDs
```bash
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
  | jq -r '.workflow_runs[] | select(.inputs.tracker_id != null) | {id, tracker_id: .inputs.tracker_id, status, conclusion}'
```

### Find Multiple Runs by Pattern
```bash
# Find all runs with tracker IDs starting with "build-"
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
  | jq -r '.workflow_runs[] | select(.inputs.tracker_id | test("^build-")) | {id, tracker_id: .inputs.tracker_id}'
```

## 5. Error Handling

```bash
find_run_by_tracker() {
    local tracker_id="$1"
    local max_attempts=15
    
    for ((i=1; i<=max_attempts; i++)); do
        local run_id=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
          "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs" \
          | jq -r --arg tracker "$tracker_id" \
          '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)
        
        if [[ -n "$run_id" && "$run_id" != "null" ]]; then
            echo "$run_id"
            return 0
        fi
        
        sleep 3
    done
    
    echo "Error: Run not found for tracker: $tracker_id" >&2
    return 1
}
```

## 6. Complete Production Script

```bash
#!/bin/bash
set -e

# Configuration
REPO="Cyoda-platform/java-client-template"
WORKFLOW="build.yml"

trigger_and_monitor() {
    local branch="$1"
    local build_type="$2"
    
    # Generate tracker
    local tracker_id="build-$(date +%s)-$$-$(openssl rand -hex 4)"
    echo "🏷️  Tracker ID: $tracker_id"
    
    # Trigger
    echo "🚀 Triggering workflow..."
    local http_code=$(curl -s -w "%{http_code}" -X POST \
      -H "Authorization: token ${GITHUB_TOKEN}" \
      -H "Content-Type: application/json" \
      "https://api.github.com/repos/${REPO}/actions/workflows/${WORKFLOW}/dispatches" \
      -d "{\"ref\":\"$branch\",\"inputs\":{\"branch\":\"$branch\",\"build_type\":\"$build_type\",\"tracker_id\":\"$tracker_id\"}}" \
      | tail -c 3)
    
    [[ "$http_code" == "204" ]] || { echo "❌ Trigger failed (HTTP $http_code)"; exit 1; }
    
    # Find run
    echo "🔍 Finding run..."
    local run_id=""
    for ((i=1; i<=15; i++)); do
        sleep 3
        run_id=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
          "https://api.github.com/repos/${REPO}/actions/workflows/${WORKFLOW}/runs" \
          | jq -r --arg tracker "$tracker_id" \
          '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)
        
        [[ -n "$run_id" && "$run_id" != "null" ]] && break
        echo "⏳ Attempt $i/15..."
    done
    
    [[ -n "$run_id" && "$run_id" != "null" ]] || { echo "❌ Run not found"; exit 1; }
    
    echo "✅ Found run: $run_id"
    echo "🔗 URL: https://github.com/${REPO}/actions/runs/${run_id}"
    
    # Return both values
    echo "TRACKER_ID=$tracker_id"
    echo "RUN_ID=$run_id"
}

# Usage
trigger_and_monitor "main" "compile-only"
```

## Advantages of Tracker ID Method

✅ **100% Reliable** - No race conditions  
✅ **Multi-user Safe** - Each trigger gets unique ID  
✅ **Debuggable** - Easy to trace specific runs  
✅ **Filterable** - Can find runs by pattern  
✅ **Auditable** - Clear tracking in workflow logs
