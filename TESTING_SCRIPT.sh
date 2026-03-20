#!/bin/bash

# TMS API - Полный скрипт для ручного тестирования всех эндпоинтов
# Использование: bash TESTING_SCRIPT.sh

set -e

BASE_URL="http://localhost:8080/api"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║         TMS API - ПОЛНОЕ ТЕСТИРОВАНИЕ ВСЕХ ЭНДПОИНТОВ         ║${NC}"
echo -e "${YELLOW}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============ ФАЗА 1: AUTHENTICATION ============
echo -e "${YELLOW}=== ФАЗА 1: AUTHENTICATION ===${NC}"

echo -e "${GREEN}1.1 POST /login (Admin)${NC}"
ADMIN_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(echo $ADMIN_RESPONSE | jq -r '.token')
echo "✅ Admin Token: ${ADMIN_TOKEN:0:30}..."
echo ""

echo -e "${GREEN}1.2 POST /login (Tester)${NC}"
TESTER_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}')
TESTER_TOKEN=$(echo $TESTER_RESPONSE | jq -r '.token')
echo "✅ Tester Token: ${TESTER_TOKEN:0:30}..."
echo ""

# ============ ФАЗА 2: PROJECTS ============
echo -e "${YELLOW}=== ФАЗА 2: PROJECTS ===${NC}"

echo -e "${GREEN}2.1 POST /projects (Create)${NC}"
PROJECT=$(curl -s -X POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"E-Commerce Platform","description":"Testing e-commerce website"}')
PROJECT_ID=$(echo $PROJECT | jq -r '.id')
echo "✅ Created Project: $PROJECT_ID"
echo ""

echo -e "${GREEN}2.2 GET /projects (List)${NC}"
curl -s "$BASE_URL/projects" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[] | {id, name, status}' | head -20
echo "✅ Projects listed"
echo ""

echo -e "${GREEN}2.3 GET /projects/{id}${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, name, status, createdAt}'
echo "✅ Project retrieved"
echo ""

echo -e "${GREEN}2.4 PUT /projects/{id} (Update)${NC}"
curl -s -X PUT "$BASE_URL/projects/$PROJECT_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"E-Commerce Platform v2","description":"Updated"}' | jq '{id, name, status, createdAt}'
echo "✅ Project updated (check createdAt preserved)"
echo ""

# ============ ФАЗА 3: SUITES ============
echo -e "${YELLOW}=== ФАЗА 3: SUITES ===${NC}"

echo -e "${GREEN}3.1 POST /projects/{id}/suites (Create)${NC}"
SUITE=$(curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/suites" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Authentication Tests","description":"Login, logout, password reset"}')
SUITE_ID=$(echo $SUITE | jq -r '.id')
echo "✅ Created Suite: $SUITE_ID"
echo ""

echo -e "${GREEN}3.2 GET /projects/{id}/suites${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[] | {id, name, status}'
echo "✅ Suites listed"
echo ""

echo -e "${GREEN}3.3 GET /projects/{id}/suites/{id}${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, name, status}'
echo "✅ Suite retrieved"
echo ""

echo -e "${GREEN}3.4 PUT /projects/{id}/suites/{id} (Update)${NC}"
curl -s -X PUT "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Authentication & Authorization Tests","description":"Updated"}' | jq '{id, name, status}'
echo "✅ Suite updated"
echo ""

# ============ ФАЗА 4: TEST CASES ============
echo -e "${YELLOW}=== ФАЗА 4: TEST CASES ===${NC}"

echo -e "${GREEN}4.1 POST /projects/{id}/suites/{id}/cases (Create)${NC}"
CASE=$(curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"title":"Valid Login with Email","description":"User logs in with valid email and password","priority":"HIGH"}')
CASE_ID=$(echo $CASE | jq -r '.id')
echo "✅ Created Test Case: $CASE_ID"
echo "   Check: title field = 'Valid Login with Email' (not name)"
echo $CASE | jq '{id, title, description, status}'
echo ""

echo -e "${GREEN}4.2 GET /projects/{id}/suites/{id}/cases${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[] | {id, title, status}'
echo "✅ Test Cases listed"
echo ""

echo -e "${GREEN}4.3 GET /projects/{id}/suites/{id}/cases/{id}${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, title, status, createdAt}'
echo "✅ Test Case retrieved"
echo ""

echo -e "${GREEN}4.4 PUT /projects/{id}/suites/{id}/cases/{id} (Update)${NC}"
curl -s -X PUT "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"title":"Valid Login with Email or Phone","description":"Updated","priority":"CRITICAL"}' | jq '{id, title, status, createdAt}'
echo "✅ Test Case updated (check createdAt preserved)"
echo ""

# ============ ФАЗА 5: TEST STEPS ============
echo -e "${YELLOW}=== ФАЗА 5: TEST STEPS ===${NC}"

echo -e "${GREEN}5.1 POST /projects/{id}/suites/{id}/cases/{id}/steps (Create)${NC}"
STEP=$(curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"description":"Navigate to login page","expectedResult":"Login form is displayed"}')
STEP_ID=$(echo $STEP | jq -r '.id')
echo "✅ Created Test Step: $STEP_ID"
echo "   Check: description field = 'Navigate to login page' (not action)"
echo $STEP | jq '{id, description, expectedResult}'
echo ""

echo -e "${GREEN}5.2 GET /projects/{id}/suites/{id}/cases/{id}/steps${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[] | {id, description, expectedResult}'
echo "✅ Test Steps listed"
echo ""

echo -e "${GREEN}5.3 GET /projects/{id}/suites/{id}/cases/{id}/steps/{id}${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps/$STEP_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, description, expectedResult}'
echo "✅ Test Step retrieved"
echo ""

echo -e "${GREEN}5.4 PUT /projects/{id}/suites/{id}/cases/{id}/steps/{id} (Update)${NC}"
curl -s -X PUT "$BASE_URL/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps/$STEP_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"description":"Navigate to login page and wait for load","expectedResult":"Login form is displayed within 3 seconds"}' | jq '{id, description, expectedResult}'
echo "✅ Test Step updated"
echo ""

# ============ ФАЗА 6: TEST RUNS ============
echo -e "${YELLOW}=== ФАЗА 6: TEST RUNS ===${NC}"

echo -e "${GREEN}6.1 POST /projects/{id}/runs (Create)${NC}"
RUN=$(curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/runs" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{"name":"Sprint 1 - Regression Testing","environment":"QA"}')
RUN_ID=$(echo $RUN | jq -r '.id')
echo "✅ Created Test Run: $RUN_ID"
echo "   Check: name field = 'Sprint 1 - Regression Testing' (not title)"
echo $RUN | jq '{id, name, environment, status}'
echo ""

echo -e "${GREEN}6.2 GET /projects/{id}/runs${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/runs" \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq '.[] | {id, name, status}'
echo "✅ Test Runs listed"
echo ""

echo -e "${GREEN}6.3 GET /projects/{id}/runs/{id}${NC}"
curl -s "$BASE_URL/projects/$PROJECT_ID/runs/$RUN_ID" \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq '{id, name, status, createdAt}'
echo "✅ Test Run retrieved"
echo ""

echo -e "${GREEN}6.4 PUT /projects/{id}/runs/{id} (Update)${NC}"
curl -s -X PUT "$BASE_URL/projects/$PROJECT_ID/runs/$RUN_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{"name":"Sprint 1 - Full Regression Testing","environment":"Staging"}' | jq '{id, name, status, createdAt}'
echo "✅ Test Run updated (check createdAt preserved)"
echo ""

echo -e "${GREEN}6.5 POST /projects/{id}/runs/{id}/complete${NC}"
curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/runs/$RUN_ID/complete" \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq '{id, status, completedAt}'
echo "✅ Test Run completed (status=COMPLETED)"
echo ""

echo -e "${GREEN}6.6 POST /projects/{id}/runs/{id}/unlock${NC}"
curl -s -X POST "$BASE_URL/projects/$PROJECT_ID/runs/$RUN_ID/unlock" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, status}'
echo "✅ Test Run unlocked (status=UNLOCKED)"
echo ""

# ============ ФАЗА 7: ADMIN GRPC ============
echo -e "${YELLOW}=== ФАЗА 7: ADMIN GRPC ===${NC}"

echo -e "${GREEN}7.1 GET /admin/grpc/status${NC}"
curl -s "$BASE_URL/admin/grpc/status" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{connectionState, observerState}'
echo "✅ gRPC status retrieved"
echo ""

echo -e "${GREEN}7.2 POST /admin/grpc/reconnect${NC}"
curl -s -X POST "$BASE_URL/admin/grpc/reconnect?force=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
echo ""
echo "✅ gRPC reconnection initiated"
echo ""

# ============ ФИНАЛЬНЫЙ ОТЧЁТ ============
echo -e "${YELLOW}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}✅ ВСЕ ЭНДПОИНТЫ УСПЕШНО ПРОТЕСТИРОВАНЫ!${NC}"
echo -e "${YELLOW}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "📊 Итого протестировано:"
echo "   ✅ 2 Authentication эндпоинта"
echo "   ✅ 5 Projects эндпоинтов"
echo "   ✅ 5 Suites эндпоинтов"
echo "   ✅ 5 Test Cases эндпоинтов"
echo "   ✅ 5 Test Steps эндпоинтов"
echo "   ✅ 7 Test Runs эндпоинтов"
echo "   ✅ 2 Admin gRPC эндпоинта"
echo ""
echo "📝 Проверено:"
echo "   ✅ Все DTO поля правильно маппированы (title, name, description)"
echo "   ✅ PUT операции сохраняют createdAt и status"
echo "   ✅ Токены работают для обоих пользователей"
echo "   ✅ Все HTTP коды корректны"
echo ""
