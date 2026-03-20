#!/bin/bash

# TMS API - Run with Real Cyoda Credentials
# This script starts the application with real Cyoda EU instance

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         TMS API - Starting with Real Cyoda Credentials        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Set Java path
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

# Cyoda Configuration
export CYODA_HOST="client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net"
export CYODA_CLIENT_ID="kLXY45"
export CYODA_CLIENT_SECRET="OAIsUzQMP4LoW19JTwoi"

echo "📋 Configuration:"
echo "   Cyoda Host: $CYODA_HOST"
echo "   Client ID: $CYODA_CLIENT_ID"
echo "   Client Secret: ••••••••••••••••••••"
echo ""

echo "🔨 Building project..."
./gradlew clean build -x test > /dev/null 2>&1
echo "✅ Build successful"
echo ""

echo "🚀 Starting application..."
echo "   Server: http://localhost:8080/api"
echo "   Swagger UI: http://localhost:8080/api"
echo ""
echo "Press Ctrl+C to stop"
echo ""

./gradlew bootRun \
  --args="--app.config.cyoda-host=$CYODA_HOST --app.config.cyoda-client-id=$CYODA_CLIENT_ID --app.config.cyoda-client-secret=$CYODA_CLIENT_SECRET"

