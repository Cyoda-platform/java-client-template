#!/bin/bash

# Example: Trigger GitHub Actions workflow and monitor progress
# Usage: ./example_trigger_and_monitor.sh <branch> [build_type]

set -e

# Configuration
OWNER="Cyoda-platform"
REPO="java-client-template"
WORKFLOW_NAME="build.yml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check arguments
if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <branch> [build_type]"
    echo "Build types: compile-only, standard, workflow-import, both"
    exit 1
fi

BRANCH="$1"
BUILD_TYPE="${2:-standard}"

# Check if GitHub token is set
if [[ -z "$GITHUB_TOKEN" ]]; then
    echo -e "${RED}Error: GITHUB_TOKEN environment variable is not set${NC}"
    echo "Please set your GitHub token: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

echo -e "${BLUE}🚀 GitHub Actions Workflow Trigger Example${NC}"
echo -e "${BLUE}===========================================${NC}"
echo -e "Repository: ${GREEN}${OWNER}/${REPO}${NC}"
echo -e "Workflow: ${GREEN}${WORKFLOW_NAME}${NC}"
echo -e "Branch: ${GREEN}${BRANCH}${NC}"
echo -e "Build Type: ${YELLOW}${BUILD_TYPE}${NC}"
echo ""

# Step 1: Get baseline (latest run before triggering)
echo -e "${BLUE}📋 Step 1: Getting baseline run ID...${NC}"
BASELINE_RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_NAME}/runs?per_page=1" \
  | jq -r '.workflow_runs[0].id // "none"')

echo -e "Baseline run ID: ${YELLOW}${BASELINE_RUN_ID}${NC}"

# Step 2: Trigger the workflow
echo -e "\n${BLUE}🚀 Step 2: Triggering workflow...${NC}"
HTTP_CODE=$(curl -s -w "%{http_code}" -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_NAME}/dispatches" \
  -d "{
    \"ref\": \"${BRANCH}\",
    \"inputs\": {
      \"branch\": \"${BRANCH}\",
      \"build_type\": \"${BUILD_TYPE}\"
    }
  }" | tail -c 3)

if [[ "$HTTP_CODE" != "204" ]]; then
    echo -e "${RED}❌ Failed to trigger workflow (HTTP ${HTTP_CODE})${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Workflow triggered successfully!${NC}"

# Step 3: Wait for new run to appear
echo -e "\n${BLUE}⏳ Step 3: Waiting for new run to appear...${NC}"
NEW_RUN_ID=""
MAX_ATTEMPTS=15

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    sleep 3
    
    LATEST_RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_NAME}/runs?per_page=1" \
      | jq -r '.workflow_runs[0].id // "none"')
    
    if [[ "$LATEST_RUN_ID" != "$BASELINE_RUN_ID" && "$LATEST_RUN_ID" != "none" ]]; then
        NEW_RUN_ID="$LATEST_RUN_ID"
        echo -e "${GREEN}✅ Found new run! ID: ${NEW_RUN_ID}${NC}"
        break
    fi
    
    echo -e "Attempt ${i}/${MAX_ATTEMPTS}: Still waiting..."
done

if [[ -z "$NEW_RUN_ID" ]]; then
    echo -e "${RED}❌ Timeout: New run did not appear within expected time${NC}"
    echo -e "${BLUE}💡 Check manually: https://github.com/${OWNER}/${REPO}/actions${NC}"
    exit 1
fi

# Step 4: Get initial run details
echo -e "\n${BLUE}📊 Step 4: Getting run details...${NC}"
RUN_DATA=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${NEW_RUN_ID}")

STATUS=$(echo "$RUN_DATA" | jq -r '.status')
CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion // "in_progress"')
HTML_URL=$(echo "$RUN_DATA" | jq -r '.html_url')

echo -e "Run ID: ${YELLOW}${NEW_RUN_ID}${NC}"
echo -e "Status: ${YELLOW}${STATUS}${NC}"
echo -e "Conclusion: ${YELLOW}${CONCLUSION}${NC}"
echo -e "URL: ${BLUE}${HTML_URL}${NC}"

# Step 5: Monitor progress (optional)
echo -e "\n${BLUE}👀 Step 5: Monitoring progress...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop monitoring (workflow will continue)${NC}"

while [[ "$STATUS" != "completed" ]]; do
    sleep 10
    
    RUN_DATA=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${NEW_RUN_ID}")
    
    NEW_STATUS=$(echo "$RUN_DATA" | jq -r '.status')
    NEW_CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion // "in_progress"')
    
    if [[ "$NEW_STATUS" != "$STATUS" ]]; then
        STATUS="$NEW_STATUS"
        echo -e "Status changed to: ${YELLOW}${STATUS}${NC}"
    fi
    
    if [[ "$STATUS" == "completed" ]]; then
        CONCLUSION="$NEW_CONCLUSION"
        break
    fi
    
    echo -e "$(date '+%H:%M:%S') - Still running... (${STATUS})"
done

# Step 6: Final results
echo -e "\n${BLUE}🏁 Step 6: Final Results${NC}"
echo -e "${BLUE}========================${NC}"

case "$CONCLUSION" in
    "success")
        echo -e "${GREEN}✅ Build completed successfully!${NC}"
        ;;
    "failure")
        echo -e "${RED}❌ Build failed${NC}"
        ;;
    "cancelled")
        echo -e "${YELLOW}🚫 Build was cancelled${NC}"
        ;;
    *)
        echo -e "${YELLOW}❓ Build finished with status: ${CONCLUSION}${NC}"
        ;;
esac

echo -e "\nRun ID: ${YELLOW}${NEW_RUN_ID}${NC}"
echo -e "View details: ${BLUE}${HTML_URL}${NC}"

# Show artifacts if build was successful
if [[ "$CONCLUSION" == "success" && "$BUILD_TYPE" != "compile-only" ]]; then
    echo -e "\n${BLUE}📦 Checking for artifacts...${NC}"
    ARTIFACTS=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${NEW_RUN_ID}/artifacts")
    
    ARTIFACT_COUNT=$(echo "$ARTIFACTS" | jq '.total_count')
    if [[ "$ARTIFACT_COUNT" -gt 0 ]]; then
        echo -e "${GREEN}Found ${ARTIFACT_COUNT} artifact(s):${NC}"
        echo "$ARTIFACTS" | jq -r '.artifacts[] | "- \(.name) (\(.size_in_bytes) bytes)"'
    fi
fi

echo -e "\n${GREEN}🎉 Workflow monitoring complete!${NC}"
