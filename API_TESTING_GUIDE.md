# TMS API — Полное руководство по ручному тестированию

## 🚀 Подготовка

### 1. Запустить приложение
```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
./gradlew bootRun --args='--app.config.cyoda-host=localhost --app.config.cyoda-client-id=prototype --app.config.cyoda-client-secret=prototype'
```

### 2. Открыть Swagger UI
```
http://localhost:8080/api
```

### 3. Получить токены
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

TESTER_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}' | jq -r '.token')

echo "Admin: $ADMIN_TOKEN"
echo "Tester: $TESTER_TOKEN"
```

---

## 📋 Порядок тестирования

### ФАЗА 1: AUTHENTICATION (2 эндпоинта)

#### 1.1 POST /login (Admin)
```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq
```
✅ Ожидаемый результат: HTTP 200, token, role="ADMIN"

#### 1.2 POST /login (Tester)
```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}' | jq
```
✅ Ожидаемый результат: HTTP 200, role="TESTER"

---

### ФАЗА 2: PROJECTS (5 эндпоинтов)

#### 2.1 POST /projects (Create)
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"E-Commerce","description":"Testing"}' | jq
```
💾 Сохранить: `PROJECT_ID`

#### 2.2 GET /projects (List)
```bash
curl http://localhost:8080/api/projects \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

#### 2.3 GET /projects/{id}
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

#### 2.4 PUT /projects/{id} (Update)
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"E-Commerce v2","description":"Updated"}' | jq
```
✅ Проверить: name обновлён, createdAt сохранён, status="ACTIVE"

#### 2.5 DELETE /projects/{id}
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

### ФАЗА 3: SUITES (5 эндпоинтов)

#### 3.1 POST /projects/{id}/suites
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Auth Tests","description":"Login tests"}' | jq
```
💾 Сохранить: `SUITE_ID`

#### 3.2-3.5 GET, PUT, DELETE (аналогично Projects)

---

### ФАЗА 4: TEST CASES (5 эндпоинтов)

#### 4.1 POST /projects/{id}/suites/{id}/cases
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"title":"Valid Login","description":"Test","priority":"HIGH"}' | jq
```
💾 Сохранить: `CASE_ID`
✅ Проверить: title сохранено (не name)

#### 4.2-4.5 GET, PUT, DELETE

---

### ФАЗА 5: TEST STEPS (5 эндпоинтов)

#### 5.1 POST /projects/{id}/suites/{id}/cases/{id}/steps
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"description":"Open page","expectedResult":"Page loads"}' | jq
```
💾 Сохранить: `STEP_ID`
✅ Проверить: description сохранено (не action)

#### 5.2-5.5 GET, PUT, DELETE

---

### ФАЗА 6: TEST RUNS (7 эндпоинтов)

#### 6.1 POST /projects/{id}/runs
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{"name":"Sprint 1","environment":"QA"}' | jq
```
💾 Сохранить: `RUN_ID`
✅ Проверить: name сохранено (не title), status="CREATED"

#### 6.2-6.4 GET, PUT

#### 6.5 POST /projects/{id}/runs/{id}/complete
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID/complete \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq
```
✅ Проверить: status="COMPLETED", completedAt заполнен

#### 6.6 POST /projects/{id}/runs/{id}/unlock
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID/unlock \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: status="UNLOCKED"

#### 6.7 DELETE /projects/{id}/runs/{id}

---

### ФАЗА 7: ADMIN GRPC (2 эндпоинта)

#### 7.1 GET /admin/grpc/status
```bash
curl http://localhost:8080/api/admin/grpc/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

#### 7.2 POST /admin/grpc/reconnect
```bash
curl -X POST "http://localhost:8080/api/admin/grpc/reconnect?force=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

## ✅ Финальный чеклист

- [ ] Все эндпоинты возвращают HTTP 200/201
- [ ] Все DTO поля правильно маппированы
- [ ] PUT сохраняет createdAt и status
- [ ] Токены работают для обоих пользователей
- [ ] Swagger UI доступен
- [ ] Все ошибки возвращают правильные коды

