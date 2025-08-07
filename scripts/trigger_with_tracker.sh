#!/bin/bash

# Trigger GitHub Actions workflow with unique tracker ID
# Usage: ./trigger_with_tracker.sh <branch> [build_type]

set -e

# Configuration
REPO="Cyoda-platform/java-client-template"
WORKFLOW="build.yml"

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

echo -e "${BLUE}🚀 GitHub Actions Trigger with Tracker ID${NC}"
echo -e "${BLUE}=========================================${NC}"

# Step 1: Generate unique tracker ID
TRACKER_ID="build-$(date +%s)-$$-$(openssl rand -hex 4 2>/dev/null || echo $(shuf -i 1000-9999 -n 1))"
echo -e "🏷️  Tracker ID: ${YELLOW}${TRACKER_ID}${NC}"
echo -e "📂 Repository: ${GREEN}${REPO}${NC}"
echo -e "🌿 Branch: ${GREEN}${BRANCH}${NC}"
echo -e "🔧 Build Type: ${YELLOW}${BUILD_TYPE}${NC}"

# Step 2: Trigger workflow
echo -e "\n${BLUE}🚀 Step 1: Triggering workflow...${NC}"
HTTP_CODE=$(curl -s -w "%{http_code}" -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Content-Type: application/json" \
  "https://api.github.com/repos/${REPO}/actions/workflows/${WORKFLOW}/dispatches" \
  -d "{
    \"ref\": \"${BRANCH}\",
    \"inputs\": {
      \"branch\": \"${BRANCH}\",
      \"build_type\": \"${BUILD_TYPE}\",
      \"tracker_id\": \"${TRACKER_ID}\"
    }
  }" | tail -c 3)

if [[ "$HTTP_CODE" != "204" ]]; then
    echo -e "${RED}❌ Failed to trigger workflow (HTTP ${HTTP_CODE})${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Workflow triggered successfully!${NC}"

# Step 3: Find run by tracker ID
echo -e "\n${BLUE}🔍 Step 2: Finding run by tracker ID...${NC}"
MAX_ATTEMPTS=15
RUN_ID=""

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    sleep 3
    
    RUN_ID=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${REPO}/actions/workflows/${WORKFLOW}/runs" \
      | jq -r --arg tracker "$TRACKER_ID" \
      '.workflow_runs[] | select(.inputs.tracker_id == $tracker) | .id' | head -1)
    
    if [[ -n "$RUN_ID" && "$RUN_ID" != "null" ]]; then
        echo -e "${GREEN}✅ Found run with tracker ID!${NC}"
        break
    fi
    
    echo -e "⏳ Attempt ${i}/${MAX_ATTEMPTS}: Waiting for run to appear..."
done

if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
    echo -e "${RED}❌ Error: Could not find run with tracker ID: ${TRACKER_ID}${NC}"
    echo -e "${BLUE}💡 Check manually: https://github.com/${REPO}/actions${NC}"
    exit 1
fi

# Step 4: Get run details
echo -e "\n${BLUE}📊 Step 3: Getting run details...${NC}"
RUN_DATA=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
  "https://api.github.com/repos/${REPO}/actions/runs/${RUN_ID}")

STATUS=$(echo "$RUN_DATA" | jq -r '.status')
CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion // "in_progress"')
HTML_URL=$(echo "$RUN_DATA" | jq -r '.html_url')
CREATED_AT=$(echo "$RUN_DATA" | jq -r '.created_at')

echo -e "\n${BLUE}🎉 Success! Run Details:${NC}"
echo -e "${BLUE}========================${NC}"
echo -e "🏷️  Tracker ID: ${YELLOW}${TRACKER_ID}${NC}"
echo -e "🆔 Run ID: ${YELLOW}${RUN_ID}${NC}"
echo -e "📊 Status: ${YELLOW}${STATUS}${NC}"
echo -e "🏁 Conclusion: ${YELLOW}${CONCLUSION}${NC}"
echo -e "📅 Created: ${YELLOW}${CREATED_AT}${NC}"
echo -e "🔗 URL: ${BLUE}${HTML_URL}${NC}"

# Step 5: Offer to monitor
if [[ "$STATUS" == "in_progress" || "$STATUS" == "queued" ]]; then
    echo -e "\n${BLUE}👀 Monitoring Options:${NC}"
    echo -e "1. Monitor with this script: ${GREEN}./scripts/check-build-status.sh ${RUN_ID}${NC}"
    echo -e "2. Monitor with Python: ${GREEN}python scripts/build_status.py ${RUN_ID}${NC}"
    echo -e "3. Monitor with GitHub CLI: ${GREEN}gh run watch ${RUN_ID}${NC}"
    echo -e "4. View in browser: ${BLUE}${HTML_URL}${NC}"
    
    echo -e "\n${YELLOW}💡 Want to monitor now? (y/N)${NC}"
    read -r -n 1 MONITOR
    echo
    
    if [[ "$MONITOR" =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}👀 Monitoring run progress...${NC}"
        echo -e "${YELLOW}Press Ctrl+C to stop monitoring (workflow will continue)${NC}\n"
        
        while [[ "$STATUS" != "completed" ]]; do
            sleep 10
            
            RUN_DATA=$(curl -s -H "Authorization: token ${GITHUB_TOKEN}" \
              "https://api.github.com/repos/${REPO}/actions/runs/${RUN_ID}")
            
            NEW_STATUS=$(echo "$RUN_DATA" | jq -r '.status')
            NEW_CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion // "in_progress"')
            
            if [[ "$NEW_STATUS" != "$STATUS" ]]; then
                STATUS="$NEW_STATUS"
                echo -e "$(date '+%H:%M:%S') - Status changed to: ${YELLOW}${STATUS}${NC}"
            fi
            
            if [[ "$STATUS" == "completed" ]]; then
                CONCLUSION="$NEW_CONCLUSION"
                break
            fi
            
            echo -e "$(date '+%H:%M:%S') - Still running... (${STATUS})"
        done
        
        echo -e "\n${BLUE}🏁 Final Result:${NC}"
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
    fi
fi

echo -e "\n${GREEN}🎉 Tracker ID method completed successfully!${NC}"

# Output for scripting
echo -e "\n${BLUE}📋 For Scripting:${NC}"
echo "TRACKER_ID=${TRACKER_ID}"
echo "RUN_ID=${RUN_ID}"
echo "STATUS=${STATUS}"
echo "CONCLUSION=${CONCLUSION}"
