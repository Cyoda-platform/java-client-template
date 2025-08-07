#!/bin/bash

# Trigger GitHub Actions build and watch status
# Usage: ./trigger-and-watch.sh <branch> [build_type]

set -e

# Configuration
REPO="Cyoda-platform/java-client-template"
WORKFLOW_FILE="build.yml"

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
    exit 1
fi

echo -e "${BLUE}🚀 Triggering GitHub Actions build...${NC}"
echo -e "Branch: ${GREEN}$BRANCH${NC}"
echo -e "Build Type: ${YELLOW}$BUILD_TYPE${NC}"

# Trigger the workflow
response=$(curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/$REPO/actions/workflows/$WORKFLOW_FILE/dispatches" \
  -d "{
    \"ref\": \"$BRANCH\",
    \"inputs\": {
      \"branch\": \"$BRANCH\",
      \"build_type\": \"$BUILD_TYPE\"
    }
  }")

if [[ -n "$response" ]]; then
    echo -e "${RED}Error triggering workflow:${NC}"
    echo "$response"
    exit 1
fi

echo -e "${GREEN}✅ Workflow triggered successfully!${NC}"
echo -e "${BLUE}⏳ Waiting for workflow to start...${NC}"

# Wait a moment for the workflow to appear
sleep 5

# Get the latest run ID
echo "Fetching latest run..."
run_id=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
    "https://api.github.com/repos/$REPO/actions/workflows/$WORKFLOW_FILE/runs?per_page=1" \
    | jq -r '.workflow_runs[0].id')

if [[ "$run_id" == "null" ]] || [[ -z "$run_id" ]]; then
    echo -e "${RED}Could not find the triggered workflow run${NC}"
    exit 1
fi

echo -e "${GREEN}Found run ID: $run_id${NC}"
echo -e "${BLUE}🔗 View in browser: https://github.com/$REPO/actions/runs/$run_id${NC}"

# Check if gh CLI is available for watching
if command -v gh &> /dev/null; then
    echo -e "\n${BLUE}👀 Watching workflow progress...${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop watching (workflow will continue)${NC}\n"
    gh run watch "$run_id" || true
else
    echo -e "\n${YELLOW}💡 Install GitHub CLI (gh) to watch progress in real-time${NC}"
    echo -e "${BLUE}Or check status with: ./scripts/check-build-status.sh $run_id${NC}"
fi

# Final status check
echo -e "\n${BLUE}📊 Final Status Check:${NC}"
./scripts/check-build-status.sh "$run_id"
