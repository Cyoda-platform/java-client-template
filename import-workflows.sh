#!/bin/bash

# Workflow Import Script using User Credentials
# This script imports workflows to Cyoda using REST API with user authentication

set -e

# Configuration
CYODA_HOST="cyoda-develop.kube3.cyoda.org"
USERNAME="demo.user"
PASSWORD="k33pS8fe!!"
CLIENT_ID="cyoda-ui"
ENTITY_VERSION="1"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "🔄 Starting workflow import to Cyoda..."
echo "Host: https://${CYODA_HOST}"
echo ""

# Step 1: Get OAuth2 token using password grant
echo "🔑 Authenticating..."
TOKEN_RESPONSE=$(curl -s -X POST "https://${CYODA_HOST}/api/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n "${CLIENT_ID}:" | base64)" \
  -d "grant_type=password&username=${USERNAME}&password=${PASSWORD}")

# Extract access token
ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
    echo -e "${RED}❌ Failed to get access token${NC}"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ Authentication successful${NC}"
echo ""

# Function to import a single workflow
import_workflow() {
    local entity_name=$1
    local workflow_file=$2
    
    echo "📦 Importing workflow for: ${entity_name}"
    
    # Read workflow file
    if [ ! -f "$workflow_file" ]; then
        echo -e "${RED}❌ Workflow file not found: ${workflow_file}${NC}"
        return 1
    fi
    
    # Read workflow content
    WORKFLOW_CONTENT=$(cat "$workflow_file")
    
    # Wrap in required format: {"workflows": [content], "importMode": "REPLACE"}
    WRAPPED_CONTENT=$(cat <<EOF
{
  "workflows": [${WORKFLOW_CONTENT}],
  "importMode": "REPLACE"
}
EOF
)
    
    # Import workflow
    IMPORT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "https://${CYODA_HOST}/api/cyoda/model/${entity_name}/${ENTITY_VERSION}/workflow/import" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$WRAPPED_CONTENT")
    
    # Extract HTTP status code (last line)
    HTTP_CODE=$(echo "$IMPORT_RESPONSE" | tail -n1)
    RESPONSE_BODY=$(echo "$IMPORT_RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
        echo -e "${GREEN}✅ Successfully imported workflow for: ${entity_name}${NC}"
    else
        echo -e "${RED}❌ Failed to import workflow for: ${entity_name}${NC}"
        echo "HTTP Status: $HTTP_CODE"
        echo "Response: $RESPONSE_BODY"
        return 1
    fi
    
    echo ""
}

# Import all workflows
echo "📋 Importing workflows..."
echo ""

import_workflow "CatFact" "src/main/resources/workflow/catfact/version_1/CatFact.json"
import_workflow "EmailCampaign" "src/main/resources/workflow/emailcampaign/version_1/EmailCampaign.json"
import_workflow "Subscriber" "src/main/resources/workflow/subscriber/version_1/Subscriber.json"
import_workflow "Interaction" "src/main/resources/workflow/interaction/version_1/Interaction.json"

echo -e "${GREEN}🎉 Workflow import completed!${NC}"

