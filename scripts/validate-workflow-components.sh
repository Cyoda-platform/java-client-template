#!/bin/bash

# Workflow Component Validation Script
# This script validates that all processors and criteria referenced in workflow JSON files
# have corresponding Java implementation classes.

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directories
WORKFLOW_DIR="src/main/java/com/java_template/application/workflow"
PROCESSOR_DIR="src/main/java/com/java_template/application/processor"
CRITERION_DIR="src/main/java/com/java_template/application/criterion"

echo -e "${BLUE}🔍 Starting workflow component validation...${NC}"
echo "Workflow directory: $WORKFLOW_DIR"
echo "Processor directory: $PROCESSOR_DIR"
echo "Criterion directory: $CRITERION_DIR"
echo

# Check if directories exist
if [ ! -d "$WORKFLOW_DIR" ]; then
    echo -e "${RED}❌ Error: Workflow directory does not exist: $WORKFLOW_DIR${NC}"
    exit 1
fi

if [ ! -d "$PROCESSOR_DIR" ]; then
    echo -e "${RED}❌ Error: Processor directory does not exist: $PROCESSOR_DIR${NC}"
    exit 1
fi

if [ ! -d "$CRITERION_DIR" ]; then
    echo -e "${RED}❌ Error: Criterion directory does not exist: $CRITERION_DIR${NC}"
    exit 1
fi

# Find all workflow JSON files
workflow_files=($(find "$WORKFLOW_DIR" -name "*.json" -type f | sort))

if [ ${#workflow_files[@]} -eq 0 ]; then
    echo -e "${RED}❌ Error: No workflow JSON files found in $WORKFLOW_DIR${NC}"
    exit 1
fi

echo "Found ${#workflow_files[@]} workflow files"

# Arrays to store all processors and criteria
declare -a all_processors
declare -a all_criteria
declare -A workflow_processors
declare -A workflow_criteria

# Extract processors and criteria from each workflow file
for workflow_file in "${workflow_files[@]}"; do
    workflow_name=$(basename "$workflow_file")
    echo -e "\n${BLUE}📄 Processing workflow: $workflow_name${NC}"
    
    # Extract processors using jq
    processors=$(jq -r '
        .states // {} | 
        to_entries[] | 
        .value.transitions[]? | 
        .processors[]? | 
        .name // empty
    ' "$workflow_file" 2>/dev/null | sort -u)
    
    # Extract criteria using jq
    criteria=$(jq -r '
        .states // {} | 
        to_entries[] | 
        .value.transitions[]? | 
        .criterion.function.name // empty
    ' "$workflow_file" 2>/dev/null | sort -u)
    
    # Store processors for this workflow
    if [ -n "$processors" ]; then
        echo "  📦 Processors: $(echo "$processors" | tr '\n' ' ')"
        while IFS= read -r processor; do
            if [ -n "$processor" ]; then
                all_processors+=("$processor")
                workflow_processors["$processor"]+="$workflow_name "
            fi
        done <<< "$processors"
    fi
    
    # Store criteria for this workflow
    if [ -n "$criteria" ]; then
        echo "  🎯 Criteria: $(echo "$criteria" | tr '\n' ' ')"
        while IFS= read -r criterion; do
            if [ -n "$criterion" ]; then
                all_criteria+=("$criterion")
                workflow_criteria["$criterion"]+="$workflow_name "
            fi
        done <<< "$criteria"
    fi
    
    if [ -z "$processors" ] && [ -z "$criteria" ]; then
        echo "  ℹ️  No processors or criteria found"
    fi
done

# Remove duplicates and sort
unique_processors=($(printf '%s\n' "${all_processors[@]}" | sort -u))
unique_criteria=($(printf '%s\n' "${all_criteria[@]}" | sort -u))

echo -e "\n${BLUE}🔍 Validation Summary:${NC}"
echo "Total unique processors to validate: ${#unique_processors[@]}"
echo "Total unique criteria to validate: ${#unique_criteria[@]}"
echo

# Validate processors
missing_processors=()
if [ ${#unique_processors[@]} -gt 0 ]; then
    echo -e "${BLUE}🔍 Validating processors...${NC}"
    for processor in "${unique_processors[@]}"; do
        expected_file="$PROCESSOR_DIR/$processor.java"
        if [ -f "$expected_file" ]; then
            echo -e "${GREEN}✅ Processor found: $processor${NC}"
        else
            echo -e "${RED}❌ Missing processor: $processor${NC}"
            echo -e "${RED}   Expected file: $expected_file${NC}"
            echo -e "${RED}   Referenced in workflows: ${workflow_processors[$processor]}${NC}"
            missing_processors+=("$processor")
        fi
    done
else
    echo "ℹ️  No processors to validate"
fi

# Validate criteria
missing_criteria=()
if [ ${#unique_criteria[@]} -gt 0 ]; then
    echo -e "\n${BLUE}🔍 Validating criteria...${NC}"
    for criterion in "${unique_criteria[@]}"; do
        expected_file="$CRITERION_DIR/$criterion.java"
        if [ -f "$expected_file" ]; then
            echo -e "${GREEN}✅ Criterion found: $criterion${NC}"
        else
            echo -e "${RED}❌ Missing criterion: $criterion${NC}"
            echo -e "${RED}   Expected file: $expected_file${NC}"
            echo -e "${RED}   Referenced in workflows: ${workflow_criteria[$criterion]}${NC}"
            missing_criteria+=("$criterion")
        fi
    done
else
    echo "ℹ️  No criteria to validate"
fi

# Final result
echo
if [ ${#missing_processors[@]} -eq 0 ] && [ ${#missing_criteria[@]} -eq 0 ]; then
    echo -e "${GREEN}✅ All workflow components validation passed!${NC}"
    echo -e "${GREEN}✅ All ${#unique_processors[@]} processors found${NC}"
    echo -e "${GREEN}✅ All ${#unique_criteria[@]} criteria found${NC}"
    exit 0
else
    echo -e "${RED}❌ Workflow component validation failed!${NC}"
    if [ ${#missing_processors[@]} -gt 0 ]; then
        echo -e "${RED}❌ Missing ${#missing_processors[@]} processors: ${missing_processors[*]}${NC}"
    fi
    if [ ${#missing_criteria[@]} -gt 0 ]; then
        echo -e "${RED}❌ Missing ${#missing_criteria[@]} criteria: ${missing_criteria[*]}${NC}"
    fi
    exit 1
fi
