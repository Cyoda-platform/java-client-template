#!/bin/bash

# TMS API - Get Authentication Tokens
# This script obtains JWT tokens for admin and tester users

BASE_URL="http://localhost:8080/api"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║              TMS API - Getting Authentication Tokens           ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Check if curl is available
if ! command -v curl &> /dev/null; then
    echo "❌ Error: curl is not installed"
    exit 1
fi

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo "⚠️  Warning: jq is not installed, showing raw JSON"
    echo ""
fi

echo "🔐 Logging in as Admin..."
ADMIN_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

if command -v jq &> /dev/null; then
    ADMIN_TOKEN=$(echo $ADMIN_RESPONSE | jq -r '.token')
    ADMIN_ROLE=$(echo $ADMIN_RESPONSE | jq -r '.role')
    ADMIN_EXPIRES=$(echo $ADMIN_RESPONSE | jq -r '.expiresAt')
else
    ADMIN_TOKEN=$(echo $ADMIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    ADMIN_ROLE="ADMIN"
    ADMIN_EXPIRES="24 hours"
fi

echo "✅ Admin login successful"
echo "   Role: $ADMIN_ROLE"
echo "   Expires: $ADMIN_EXPIRES"
echo ""

echo "🔐 Logging in as Tester..."
TESTER_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}')

if command -v jq &> /dev/null; then
    TESTER_TOKEN=$(echo $TESTER_RESPONSE | jq -r '.token')
    TESTER_ROLE=$(echo $TESTER_RESPONSE | jq -r '.role')
    TESTER_EXPIRES=$(echo $TESTER_RESPONSE | jq -r '.expiresAt')
else
    TESTER_TOKEN=$(echo $TESTER_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    TESTER_ROLE="TESTER"
    TESTER_EXPIRES="24 hours"
fi

echo "✅ Tester login successful"
echo "   Role: $TESTER_ROLE"
echo "   Expires: $TESTER_EXPIRES"
echo ""

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                      TOKENS OBTAINED                          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

echo "📋 ADMIN TOKEN:"
echo "export ADMIN_TOKEN=\"$ADMIN_TOKEN\""
echo ""

echo "📋 TESTER TOKEN:"
echo "export TESTER_TOKEN=\"$TESTER_TOKEN\""
echo ""

echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "✅ Tokens are ready to use!"
echo ""
echo "To use these tokens in your current shell, run:"
echo ""
echo "export ADMIN_TOKEN=\"$ADMIN_TOKEN\""
echo "export TESTER_TOKEN=\"$TESTER_TOKEN\""
echo ""
echo "Or copy-paste the commands above into your terminal."
echo ""
echo "═══════════════════════════════════════════════════════════════════"

