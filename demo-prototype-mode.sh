#!/bin/bash

# Demo script for Java Client Template Prototype Mode
# This script demonstrates the InMemoryEntityService with workflow orchestrators

echo "🚀 Java Client Template - Prototype Mode Demo"
echo "=============================================="
echo ""

# Check if the application is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo "❌ Application is not running on localhost:8080"
    echo ""
    echo "To start the application in prototype mode:"
    echo "  ./gradlew bootRun --args='--spring.profiles.active=prototype'"
    echo ""
    echo "Or set the environment variable:"
    echo "  export SPRING_PROFILES_ACTIVE=prototype"
    echo "  ./gradlew bootRun"
    exit 1
fi

echo "✅ Application is running"
echo ""

# Function to make HTTP requests and show responses
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4
    
    echo "📡 $description"
    echo "   $method $url"
    
    if [ -n "$data" ]; then
        echo "   Data: $data"
        response=$(curl -s -X $method -H "Content-Type: application/json" -d "$data" "$url")
    else
        response=$(curl -s -X $method "$url")
    fi
    
    echo "   Response: $response"
    echo ""
    
    # Extract technicalId if present
    if echo "$response" | grep -q "technicalId"; then
        echo "$response" | grep -o '"technicalId":"[^"]*"' | cut -d'"' -f4
    fi
}

echo "🎯 Demo Scenario: Nobel Prize Data Management System"
echo "=================================================="
echo ""

# 1. Create a Job
echo "1️⃣  Creating a Nobel Prize Data Ingestion Job..."
job_data='{"jobName":"Nobel Prize Data 2024","status":"SCHEDULED"}'
job_id=$(make_request "POST" "http://localhost:8080/api/jobs" "$job_data" "Create Job")

# 2. Create Subscribers
echo "2️⃣  Creating Subscribers..."
subscriber1_data='{"subscriberId":"admin-001","contactType":"email","contactAddress":"admin@university.edu","active":true}'
subscriber1_id=$(make_request "POST" "http://localhost:8080/api/subscribers" "$subscriber1_data" "Create Active Subscriber")

subscriber2_data='{"subscriberId":"researcher-002","contactType":"email","contactAddress":"researcher@institute.org","active":false}'
subscriber2_id=$(make_request "POST" "http://localhost:8080/api/subscribers" "$subscriber2_data" "Create Inactive Subscriber")

# 3. Create a Laureate
echo "3️⃣  Creating a Nobel Laureate..."
laureate_data='{"firstname":"Marie","surname":"Curie","born":"1867-11-07","year":"1903","category":"Physics","motivation":"for her work on radioactivity"}'
laureate_id=$(make_request "POST" "http://localhost:8080/api/laureates" "$laureate_data" "Create Laureate")

# 4. Get all entities
echo "4️⃣  Retrieving all entities..."
make_request "GET" "http://localhost:8080/api/jobs" "" "Get All Jobs"
make_request "GET" "http://localhost:8080/api/subscribers" "" "Get All Subscribers"
make_request "GET" "http://localhost:8080/api/laureates" "" "Get All Laureates"

# 5. Update Job status
echo "5️⃣  Updating Job status (triggers workflow orchestrator)..."
if [ -n "$job_id" ]; then
    updated_job_data='{"jobName":"Nobel Prize Data 2024","status":"RUNNING"}'
    make_request "PUT" "http://localhost:8080/api/jobs/$job_id" "$updated_job_data" "Update Job to RUNNING"
    
    completed_job_data='{"jobName":"Nobel Prize Data 2024","status":"SUCCEEDED"}'
    make_request "PUT" "http://localhost:8080/api/jobs/$job_id" "$completed_job_data" "Update Job to SUCCEEDED"
fi

# 6. Update Subscriber status
echo "6️⃣  Updating Subscriber status (triggers workflow orchestrator)..."
if [ -n "$subscriber2_id" ]; then
    activated_subscriber_data='{"subscriberId":"researcher-002","contactType":"email","contactAddress":"researcher@institute.org","active":true}'
    make_request "PUT" "http://localhost:8080/api/subscribers/$subscriber2_id" "$activated_subscriber_data" "Activate Subscriber"
fi

# 7. Get individual entities
echo "7️⃣  Retrieving individual entities..."
if [ -n "$job_id" ]; then
    make_request "GET" "http://localhost:8080/api/jobs/$job_id" "" "Get Job by ID"
fi

if [ -n "$subscriber1_id" ]; then
    make_request "GET" "http://localhost:8080/api/subscribers/$subscriber1_id" "" "Get Subscriber by ID"
fi

if [ -n "$laureate_id" ]; then
    make_request "GET" "http://localhost:8080/api/laureates/$laureate_id" "" "Get Laureate by ID"
fi

echo "✅ Demo completed successfully!"
echo ""
echo "🔍 What happened behind the scenes:"
echo "   • InMemoryEntityService stored all entities in memory with auto-generated UUIDs"
echo "   • Workflow orchestrators were triggered automatically on entity creation and updates:"
echo "     - JobWorkflowOrchestrator: Processed job lifecycle (SCHEDULED → RUNNING → SUCCEEDED)"
echo "     - SubscriberWorkflowOrchestrator: Evaluated subscriber activation status"
echo "     - LaureateWorkflowOrchestrator: Validated and enriched laureate data"
echo "   • Processors and criteria were executed as part of the workflow orchestration"
echo "   • All operations were logged for visibility"
echo ""
echo "📋 Check the application logs to see the workflow orchestrator execution details!"
echo ""
echo "🧪 To run tests:"
echo "   ./gradlew test --tests ControllerPrototypeTest"
echo "   ./gradlew test --tests InMemoryEntityServiceTest"
