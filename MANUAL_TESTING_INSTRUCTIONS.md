# 🧪 TMS API — Полное руководство по ручному тестированию

## 📌 Быстрый старт

### Вариант 1: Автоматическое тестирование (рекомендуется)
```bash
# Убедитесь что приложение запущено, затем:
bash TESTING_SCRIPT.sh
```

### Вариант 2: Ручное тестирование через Swagger UI
```
http://localhost:8080/api
```

### Вариант 3: Ручное тестирование через curl
Следуйте инструкциям ниже

---

## 🚀 Подготовка

### Шаг 1: Запустить приложение
```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
./gradlew bootRun --args='--app.config.cyoda-host=localhost --app.config.cyoda-client-id=prototype --app.config.cyoda-client-secret=prototype'
```

### Шаг 2: Получить токены (в отдельном терминале)
```bash
# Admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Tester token
TESTER_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}' | jq -r '.token')

# Проверить
echo "Admin: $ADMIN_TOKEN"
echo "Tester: $TESTER_TOKEN"
```

---

## 📋 Полный порядок тестирования (31 эндпоинт)

### ФАЗА 1: AUTHENTICATION (2 эндпоинта)

**1.1 POST /login (Admin)**
```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq
```
✅ Проверить:
- HTTP 200
- Поле `token` содержит JWT
- Поле `role` = "ADMIN"
- Поле `expiresAt` содержит дату

**1.2 POST /login (Tester)**
```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}' | jq
```
✅ Проверить:
- HTTP 200
- Поле `role` = "TESTER"

---

### ФАЗА 2: PROJECTS (5 эндпоинтов)

**2.1 POST /projects (Create)**
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name":"E-Commerce Platform",
    "description":"Testing e-commerce website"
  }' | jq
```
💾 Сохранить `PROJECT_ID` из ответа

**2.2 GET /projects (List all)**
```bash
curl http://localhost:8080/api/projects \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: Массив содержит созданный проект

**2.3 GET /projects/{id} (Get by ID)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: Один проект с полной информацией

**2.4 PUT /projects/{id} (Update)**
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name":"E-Commerce Platform v2",
    "description":"Updated description"
  }' | jq
```
✅ Проверить:
- `name` обновлён на "E-Commerce Platform v2"
- `createdAt` сохранён (не изменился)
- `status` = "ACTIVE"

**2.5 DELETE /projects/{id} (Soft delete)**
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: HTTP 200

---

### ФАЗА 3: SUITES (5 эндпоинтов)

**3.1 POST /projects/{id}/suites (Create)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name":"Authentication Tests",
    "description":"Login, logout, password reset"
  }' | jq
```
💾 Сохранить `SUITE_ID`

**3.2 GET /projects/{id}/suites (List)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**3.3 GET /projects/{id}/suites/{id} (Get by ID)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**3.4 PUT /projects/{id}/suites/{id} (Update)**
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "name":"Authentication & Authorization Tests",
    "description":"Updated suite"
  }' | jq
```
✅ Проверить: `createdAt` сохранён

**3.5 DELETE /projects/{id}/suites/{id} (Delete)**
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

### ФАЗА 4: TEST CASES (5 эндпоинтов)

**4.1 POST /projects/{id}/suites/{id}/cases (Create)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "title":"Valid Login with Email",
    "description":"User logs in with valid email and password",
    "priority":"HIGH"
  }' | jq
```
💾 Сохранить `CASE_ID`
✅ **ВАЖНО**: Проверить что поле `title` сохранено (не `name`)

**4.2 GET /projects/{id}/suites/{id}/cases (List)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**4.3 GET /projects/{id}/suites/{id}/cases/{id} (Get by ID)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**4.4 PUT /projects/{id}/suites/{id}/cases/{id} (Update)**
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "title":"Valid Login with Email or Phone",
    "description":"User can login with email or phone number",
    "priority":"CRITICAL"
  }' | jq
```
✅ Проверить:
- `title` обновлён
- `status` = "ACTIVE"
- `createdAt` сохранён

**4.5 DELETE /projects/{id}/suites/{id}/cases/{id} (Delete)**
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

### ФАЗА 5: TEST STEPS (5 эндпоинтов)

**5.1 POST /projects/{id}/suites/{id}/cases/{id}/steps (Create)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "description":"Navigate to login page",
    "expectedResult":"Login form is displayed"
  }' | jq
```
💾 Сохранить `STEP_ID`
✅ **ВАЖНО**: Проверить что поле `description` сохранено (не `action`)

**5.2 GET /projects/{id}/suites/{id}/cases/{id}/steps (List)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**5.3 GET /projects/{id}/suites/{id}/cases/{id}/steps/{id} (Get by ID)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps/$STEP_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**5.4 PUT /projects/{id}/suites/{id}/cases/{id}/steps/{id} (Update)**
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps/$STEP_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "description":"Navigate to login page and wait for load",
    "expectedResult":"Login form is displayed within 3 seconds"
  }' | jq
```

**5.5 DELETE /projects/{id}/suites/{id}/cases/{id}/steps/{id} (Delete)**
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases/$CASE_ID/steps/$STEP_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

### ФАЗА 6: TEST RUNS (7 эндпоинтов)

**6.1 POST /projects/{id}/runs (Create)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{
    "name":"Sprint 1 - Regression Testing",
    "environment":"QA"
  }' | jq
```
💾 Сохранить `RUN_ID`
✅ **ВАЖНО**: Проверить что поле `name` сохранено (не `title`), `status` = "CREATED"

**6.2 GET /projects/{id}/runs (List)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/runs \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq
```

**6.3 GET /projects/{id}/runs/{id} (Get by ID)**
```bash
curl http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq
```

**6.4 PUT /projects/{id}/runs/{id} (Update)**
```bash
curl -X PUT http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{
    "name":"Sprint 1 - Full Regression Testing",
    "environment":"Staging"
  }' | jq
```
✅ Проверить:
- `name` обновлён
- `status` = "CREATED"
- `createdAt` сохранён

**6.5 POST /projects/{id}/runs/{id}/complete (Complete)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID/complete \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq
```
✅ Проверить:
- `status` = "COMPLETED"
- `completedAt` заполнен

**6.6 POST /projects/{id}/runs/{id}/unlock (Unlock)**
```bash
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID/unlock \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: `status` = "UNLOCKED"

**6.7 DELETE /projects/{id}/runs/{id} (Delete)**
```bash
curl -X DELETE http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

### ФАЗА 7: ADMIN GRPC (2 эндпоинта)

**7.1 GET /admin/grpc/status (Check status)**
```bash
curl http://localhost:8080/api/admin/grpc/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: JSON с `connectionState`, `observerState`

**7.2 POST /admin/grpc/reconnect (Reconnect)**
```bash
curl -X POST "http://localhost:8080/api/admin/grpc/reconnect?force=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```
✅ Проверить: Сообщение о переподключении

---

## ✅ Финальный чеклист

- [ ] **ФАЗА 1**: Оба логина работают, токены получены
- [ ] **ФАЗА 2**: Все 5 Project эндпоинтов работают, PUT сохраняет createdAt
- [ ] **ФАЗА 3**: Все 5 Suite эндпоинтов работают
- [ ] **ФАЗА 4**: Все 5 TestCase эндпоинтов работают, `title` сохранено
- [ ] **ФАЗА 5**: Все 5 TestStep эндпоинтов работают, `description` сохранено
- [ ] **ФАЗА 6**: Все 7 TestRun эндпоинтов работают, `name` сохранено, complete/unlock работают
- [ ] **ФАЗА 7**: Оба gRPC эндпоинта работают
- [ ] Все эндпоинты возвращают HTTP 200/201
- [ ] Все DTO поля правильно маппированы
- [ ] PUT операции сохраняют `createdAt` и `status`
- [ ] Swagger UI доступен на http://localhost:8080/api

---

## 🐛 Если что-то не работает

1. **Проверить логи приложения** — ищите ERROR или WARN
2. **Убедиться что токен не истёк** — токены действуют 24 часа
3. **Проверить что используются правильные ID** — скопируйте из предыдущего ответа
4. **Запустить unit тесты** — `./gradlew test` (все должны пройти)
5. **Перезапустить приложение** — иногда помогает

---

## 📊 Итого

- **Всего эндпоинтов**: 31
- **Фаз тестирования**: 7
- **Время на полное тестирование**: ~15-20 минут
- **Требуемые инструменты**: curl, jq (опционально)

---

## 🎯 Успешное завершение

Если все чеклисты пройдены ✅, то прототип **полностью готов к использованию**!
