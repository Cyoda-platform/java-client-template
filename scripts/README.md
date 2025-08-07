# Build Status Scripts

This directory contains scripts to interact with GitHub Actions workflows for build status monitoring.

## Setup

1. **Set GitHub Token**:
   ```bash
   export GITHUB_TOKEN="your_github_token_here"
   ```

2. **Install Python dependencies** (for Python script):
   ```bash
   pip install requests
   ```

## Python Script Usage

### Check Build Status
```bash
# Check latest build status
python scripts/build_status.py

# Check specific run
python scripts/build_status.py 1234567890

# Check latest (explicit)
python scripts/build_status.py latest
```

### Trigger New Build
```bash
# Trigger compilation check
python scripts/build_status.py trigger --branch main --build-type compile-only

# Trigger standard build
python scripts/build_status.py trigger --branch feature-branch --build-type standard

# Trigger both JARs
python scripts/build_status.py trigger --branch main --build-type both
```

## Curl Examples

### Quick Status Check
```bash
# Get latest run status using /runs endpoint
curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq '.workflow_runs[0] | {id, status, conclusion, head_branch}'
```

### Trigger and Get Run ID
```bash
# 1. Get baseline run ID
BASELINE_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq -r '.workflow_runs[0].id')

# 2. Trigger workflow
curl -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/dispatches \
  -d '{
    "ref": "your-branch-name",
    "inputs": {
      "branch": "your-branch-name",
      "build_type": "compile-only"
    }
  }'

# 3. Wait and get new run ID
sleep 5
NEW_RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/Cyoda-platform/java-client-template/actions/workflows/build.yml/runs?per_page=1" \
  | jq -r '.workflow_runs[0].id')

echo "New Run ID: $NEW_RUN_ID"
```

## Build Types

- **`compile-only`**: Only compile Java code, no tests or JAR building
- **`test-only`**: Run all tests without building JARs
- **`workflow-validation`**: Validate workflow components (processors and criteria)
- **`standard`**: Run tests and build main application JAR
- **`workflow-import`**: Build workflow import tool JAR
- **`both`**: Build both standard and workflow-import JARs

## Status Values

- **Status**: `queued` → `in_progress` → `completed`
- **Conclusion**: `success` ✅ | `failure` ❌ | `cancelled` 🚫

## Files

- **`build_status.py`**: Python script for status checking and triggering
- **`CURL_INSTRUCTIONS.md`**: Comprehensive curl examples
- **`check-build-status.sh`**: Bash script for status checking
- **`trigger-and-watch.sh`**: Bash script for triggering and watching
- **`validate-workflow-components.sh`**: Bash script for validating workflow components

## Examples

### Typical Workflow

1. **Trigger compilation check**:
   ```bash
   python scripts/build_status.py trigger --branch my-feature --build-type compile-only
   ```

2. **Check status**:
   ```bash
   python scripts/build_status.py latest
   ```

3. **If compilation passes, trigger full build**:
   ```bash
   python scripts/build_status.py trigger --branch my-feature --build-type standard
   ```

### Monitoring Loop
```bash
# Monitor until completion
while true; do
  STATUS=$(python scripts/build_status.py latest | grep "Status:" | awk '{print $2}')
  if [[ "$STATUS" == "COMPLETED" ]]; then
    break
  fi
  echo "Still running... checking again in 30s"
  sleep 30
done
```

## Workflow Component Validation

### Overview
The `validate-workflow-components.sh` script validates that all processors and criteria referenced in workflow JSON files have corresponding Java implementation classes.

### Usage
```bash
# Run validation locally
./scripts/validate-workflow-components.sh

# Trigger validation in GitHub Actions
python scripts/build_status.py trigger --branch main --build-type workflow-validation
```

### What it validates
- **Processors**: Every processor in `transitions[].processors[].name` must have a corresponding Java class in `src/main/java/com/java_template/application/processor/`
- **Criteria**: Every criterion in `transitions[].criterion.function.name` must have a corresponding Java class in `src/main/java/com/java_template/application/criterion/`

### Example output
```bash
🔍 Starting workflow component validation...
Found 3 workflow files

📄 Processing workflow: Job.json
  📦 Processors: DataIngestionProcessor JobValidationProcessor SubscribersNotifierProcessor
  🎯 Criteria: IngestionFailureCriterion IngestionSuccessCriterion

✅ All workflow components validation passed!
✅ All 6 processors found
✅ All 2 criteria found
```

### Requirements
- `jq` (JSON processor) - install with `sudo apt-get install jq` or `brew install jq`
