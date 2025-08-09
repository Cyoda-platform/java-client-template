#!/bin/bash

# Script to run the Java Client Template in Prototype Mode
# This script starts the application with InMemoryEntityService as the primary bean

echo "🚀 Starting Java Client Template in Prototype Mode"
echo "=================================================="
echo ""
echo "Features:"
echo "✅ InMemoryEntityService as primary EntityService"
echo "✅ All workflow orchestrators enabled"
echo "✅ Mock gRPC components (no external connections)"
echo "✅ Swagger UI available"
echo ""
echo "Starting application..."
echo ""

# Run the prototype application test
./gradlew test --tests PrototypeApplicationTest -Dprototype.enabled=true

echo ""
echo "Application stopped."
