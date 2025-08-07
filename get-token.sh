#!/bin/bash

# Script to get OAuth2 token from Cyoda and use it with MCP server

set -e

# Load environment variables
if [ -f ".env" ]; then
    source .env
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔐 Getting OAuth2 token from Cyoda...${NC}"

# Get OAuth2 token
TOKEN_RESPONSE=$(curl -s -X POST "https://${CYODA_HOST}/api/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n "${CYODA_CLIENT_ID}:${CYODA_CLIENT_SECRET}" | base64)" \
  -d "grant_type=client_credentials")

if [ $? -eq 0 ]; then
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
    
    if [ "$ACCESS_TOKEN" != "null" ] && [ -n "$ACCESS_TOKEN" ]; then
        echo -e "${GREEN}✅ Token obtained successfully${NC}"
        echo -e "${BLUE}Token: ${ACCESS_TOKEN:0:50}...${NC}"
        
        # Test MCP server with the token
        echo -e "${YELLOW}🧪 Testing MCP server with token...${NC}"
        
        MCP_RESPONSE=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8002/tools)
        
        if echo "$MCP_RESPONSE" | jq -e '.tools' > /dev/null 2>&1; then
            TOOL_COUNT=$(echo "$MCP_RESPONSE" | jq '.tools | length')
            echo -e "${GREEN}✅ MCP server authenticated successfully${NC}"
            echo -e "${BLUE}Available tools: $TOOL_COUNT${NC}"
            
            # Show some available tools
            echo -e "${YELLOW}🔧 Available deployment tools:${NC}"
            echo "$MCP_RESPONSE" | jq -r '.tools[] | select(.name | contains("deploy")) | "  • " + .name + ": " + .description'
            
        else
            echo -e "${RED}❌ MCP server authentication failed${NC}"
            echo -e "${BLUE}Response: $MCP_RESPONSE${NC}"
        fi
        
        # Export token for use in other scripts
        export CYODA_ACCESS_TOKEN="$ACCESS_TOKEN"
        echo -e "${GREEN}✅ Token exported as CYODA_ACCESS_TOKEN${NC}"
        
    else
        echo -e "${RED}❌ Failed to extract access token${NC}"
        echo -e "${BLUE}Response: $TOKEN_RESPONSE${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ Failed to get token from Cyoda${NC}"
    exit 1
fi
