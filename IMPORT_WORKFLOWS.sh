#!/bin/bash

# TMS API - Import Workflows to Cyoda
# This script imports all workflow definitions to the Cyoda platform

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║              TMS API - Importing Workflows to Cyoda            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Set Java path
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

# Check if build directory exists
if [ ! -d "build/libs" ]; then
    echo "🔨 Building project first..."
    ./gradlew clean build -x test > /dev/null 2>&1
    echo "✅ Build complete"
    echo ""
fi

echo "📋 Validating workflows before import..."
echo ""

# Run validation
java -cp "build/libs/*:build/resources/main" \
  com.java_template.common.tool.WorkflowValidationSuite

echo ""
echo "════════════════════════════════════════════════════════════════"
echo ""

echo "🔄 Importing workflows to Cyoda..."
echo ""

# Run import
java -cp "build/libs/*:build/resources/main" \
  com.java_template.common.tool.WorkflowImportTool

echo ""
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "✅ Workflow import completed!"
echo ""
echo "📝 Next steps:"
echo "   1. Check Cyoda UI to verify workflows were imported"
echo "   2. Run tests: bash TESTING_SCRIPT.sh"
echo "   3. Check gRPC status: curl http://localhost:8080/api/admin/grpc/status"
echo ""

