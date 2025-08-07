#!/bin/bash

# GitHub Build Status Checker
# Usage: ./check-build-status.sh [run_id|latest]

set -e

# Configuration
REPO="Cyoda-platform/java-client-template"
WORKFLOW_FILE="build.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if GitHub token is set
if [[ -z "$GITHUB_TOKEN" ]]; then
    echo -e "${RED}Error: GITHUB_TOKEN environment variable is not set${NC}"
    echo "Please set your GitHub token: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

# Function to format duration
format_duration() {
    local seconds=$1
    if [[ $seconds -lt 60 ]]; then
        echo "${seconds}s"
    elif [[ $seconds -lt 3600 ]]; then
        echo "$((seconds / 60))m $((seconds % 60))s"
    else
        echo "$((seconds / 3600))h $(((seconds % 3600) / 60))m $((seconds % 60))s"
    fi
}

# Function to get status emoji
get_status_emoji() {
    case $1 in
        "success") echo "✅" ;;
        "failure") echo "❌" ;;
        "cancelled") echo "🚫" ;;
        "in_progress") echo "🔄" ;;
        "queued") echo "⏳" ;;
        "pending") echo "⏳" ;;
        *) echo "❓" ;;
    esac
}

# Function to get latest run
get_latest_run() {
    curl -s -H "Authorization: token $GITHUB_TOKEN" \
        "https://api.github.com/repos/$REPO/actions/workflows/$WORKFLOW_FILE/runs?per_page=1" \
        | jq -r '.workflow_runs[0].id'
}

# Function to get run details
get_run_details() {
    local run_id=$1
    curl -s -H "Authorization: token $GITHUB_TOKEN" \
        "https://api.github.com/repos/$REPO/actions/runs/$run_id"
}

# Function to display run status
display_run_status() {
    local run_data="$1"
    
    local run_id=$(echo "$run_data" | jq -r '.id')
    local status=$(echo "$run_data" | jq -r '.status')
    local conclusion=$(echo "$run_data" | jq -r '.conclusion // "in_progress"')
    local branch=$(echo "$run_data" | jq -r '.head_branch')
    local commit_sha=$(echo "$run_data" | jq -r '.head_sha')
    local commit_short=${commit_sha:0:7}
    local actor=$(echo "$run_data" | jq -r '.actor.login')
    local created_at=$(echo "$run_data" | jq -r '.created_at')
    local updated_at=$(echo "$run_data" | jq -r '.updated_at')
    local html_url=$(echo "$run_data" | jq -r '.html_url')
    
    # Calculate duration
    local created_timestamp=$(date -d "$created_at" +%s)
    local updated_timestamp=$(date -d "$updated_at" +%s)
    local duration=$((updated_timestamp - created_timestamp))
    
    # Get build inputs if available
    local build_type="N/A"
    local target_branch="N/A"
    if echo "$run_data" | jq -e '.inputs' > /dev/null 2>&1; then
        build_type=$(echo "$run_data" | jq -r '.inputs.build_type // "N/A"')
        target_branch=$(echo "$run_data" | jq -r '.inputs.branch // "N/A"')
    fi
    
    echo -e "\n${BLUE}📊 GitHub Actions Build Status${NC}"
    echo -e "${BLUE}================================${NC}"
    echo -e "Run ID:       ${YELLOW}$run_id${NC}"
    echo -e "Status:       $(get_status_emoji $conclusion) ${conclusion^^}"
    echo -e "Branch:       ${GREEN}$branch${NC}"
    echo -e "Target:       ${GREEN}$target_branch${NC}"
    echo -e "Build Type:   ${YELLOW}$build_type${NC}"
    echo -e "Commit:       ${YELLOW}$commit_short${NC}"
    echo -e "Actor:        ${GREEN}$actor${NC}"
    echo -e "Duration:     $(format_duration $duration)"
    echo -e "URL:          ${BLUE}$html_url${NC}"
    
    if [[ "$status" == "in_progress" ]]; then
        echo -e "\n${YELLOW}⏳ Build is still running...${NC}"
    elif [[ "$conclusion" == "success" ]]; then
        echo -e "\n${GREEN}✅ Build completed successfully!${NC}"
    elif [[ "$conclusion" == "failure" ]]; then
        echo -e "\n${RED}❌ Build failed. Check the logs for details.${NC}"
    fi
}

# Main logic
if [[ $# -eq 0 ]] || [[ "$1" == "latest" ]]; then
    echo "Getting latest build status..."
    run_id=$(get_latest_run)
    if [[ "$run_id" == "null" ]] || [[ -z "$run_id" ]]; then
        echo -e "${RED}No workflow runs found${NC}"
        exit 1
    fi
else
    run_id="$1"
fi

echo "Fetching details for run ID: $run_id"
run_data=$(get_run_details "$run_id")

if [[ $(echo "$run_data" | jq -r '.message // empty') == "Not Found" ]]; then
    echo -e "${RED}Run ID $run_id not found${NC}"
    exit 1
fi

display_run_status "$run_data"

# If run is in progress, offer to watch it
if [[ $(echo "$run_data" | jq -r '.status') == "in_progress" ]]; then
    echo -e "\n${BLUE}💡 Tip: You can watch this run with:${NC}"
    echo "gh run watch $run_id"
fi
