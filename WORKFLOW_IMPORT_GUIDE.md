# 🔄 Workflow Import Guide — Импорт workflows на Cyoda

## 📋 Что такое Workflow?

Workflow — это конфигурация состояний и переходов для сущностей (entities) в Cyoda.

Структура:
```
src/main/resources/workflow/
├── TestCase/
│   └── version_1/
│       └── TestCase.json
├── TestRun/
│   └── version_1/
│       └── TestRun.json
└── ...
```

---

## 🚀 Как импортировать workflows на Cyoda

### Вариант 1: Автоматический импорт (РЕКОМЕНДУЕТСЯ)

```bash
# Запустить WorkflowImportTool
java -cp "build/libs/*:build/resources/main" \
  com.java_template.common.tool.WorkflowImportTool
```

**Что происходит:**
1. Инструмент подключается к Cyoda используя credentials из `application.yml`
2. Сканирует все workflow JSON файлы в `src/main/resources/workflow/`
3. Импортирует каждый workflow на Cyoda
4. Выводит результаты импорта

**Ожидаемый результат:**
```
🔄 Starting workflow import into Cyoda...
✅ Found workflow file for TestCase: src/main/resources/workflow/TestCase/version_1/TestCase.json
✅ Found workflow file for TestRun: src/main/resources/workflow/TestRun/version_1/TestRun.json
✅ Workflow import process completed.
```

---

### Вариант 2: Через скрипт (проще)

Создам скрипт для импорта:

```bash
bash IMPORT_WORKFLOWS.sh
```

---

## 📁 Структура workflow файла

Пример `TestCase.json`:

```json
{
  "version": "1.0",
  "name": "TestCase Workflow",
  "desc": "Workflow for test case management",
  "initialState": "CREATED",
  "active": true,
  "states": {
    "CREATED": {
      "name": "Created",
      "desc": "Test case created",
      "transitions": [
        {
          "target": "READY",
          "processor": "PrepareTestCaseProcessor"
        }
      ]
    },
    "READY": {
      "name": "Ready",
      "desc": "Test case ready for execution",
      "transitions": [
        {
          "target": "EXECUTED",
          "processor": "ExecuteTestCaseProcessor"
        }
      ]
    },
    "EXECUTED": {
      "name": "Executed",
      "desc": "Test case executed"
    }
  }
}
```

---

## 🔍 Проверка импорта

### 1. Проверить что workflows импортированы

```bash
# Получить токен
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Проверить gRPC статус (должен быть READY)
curl http://localhost:8080/api/admin/grpc/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

### 2. Проверить в Cyoda UI

Откройте Cyoda и проверьте что workflows появились в Model Configuration.

---

## 🛠️ Импорт modes

При импорте можно использовать разные режимы:

| Mode | Описание |
|---|---|
| `REPLACE` | Заменить существующие workflows (по умолчанию) |
| `MERGE` | Добавить новые workflows, оставить старые |
| `ACTIVATE` | Активировать импортированные, деактивировать остальные |

---

## ⚠️ Важно

1. **Credentials должны быть установлены** в `application.yml`:
   ```yaml
   app:
     config:
       cyoda-host: client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net
       cyoda-client-id: kLXY45
       cyoda-client-secret: OAIsUzQMP4LoW19JTwoi
   ```

2. **Workflow файлы должны быть валидны** — используйте `WorkflowValidationSuite`

3. **Все processors и criteria должны быть реализованы** в Java коде

---

## 🧪 Валидация workflows

Перед импортом проверьте что все workflows валидны:

```bash
# Запустить валидацию
java -cp "build/libs/*:build/resources/main" \
  com.java_template.common.tool.WorkflowValidationSuite
```

**Проверяет:**
- ✅ Все processors существуют как Java классы
- ✅ Все criteria существуют как Java классы
- ✅ Все требования из документации реализованы в workflows

---

## 📝 Workflow файлы в проекте

Текущие workflows:

```
src/main/resources/workflow/
├── TestCase/version_1/TestCase.json
├── TestRun/version_1/TestRun.json
├── TestStep/version_1/TestStep.json
└── ...
```

---

## 🎯 Полный процесс

1. **Убедитесь что приложение запущено:**
   ```bash
   bash RUN_WITH_CYODA.sh
   ```

2. **Валидируйте workflows:**
   ```bash
   java -cp "build/libs/*:build/resources/main" \
     com.java_template.common.tool.WorkflowValidationSuite
   ```

3. **Импортируйте workflows:**
   ```bash
   java -cp "build/libs/*:build/resources/main" \
     com.java_template.common.tool.WorkflowImportTool
   ```

4. **Проверьте результат:**
   ```bash
   curl http://localhost:8080/api/admin/grpc/status \
     -H "Authorization: Bearer $ADMIN_TOKEN" | jq
   ```

---

## 🐛 Решение проблем

### Проблема: "Failed to get access token"
- Проверьте credentials в `application.yml`
- Убедитесь что Cyoda сервер доступен

### Проблема: "Workflow validation failed"
- Запустите `WorkflowValidationSuite`
- Исправьте ошибки в workflow JSON файлах
- Убедитесь что все processors реализованы

### Проблема: "Import mode not supported"
- Используйте только: `REPLACE`, `MERGE`, `ACTIVATE`
- По умолчанию используется `REPLACE`

---

## ✨ Итог

Workflows импортируются на Cyoda через `WorkflowImportTool`.

**Быстрый старт:**
```bash
java -cp "build/libs/*:build/resources/main" \
  com.java_template.common.tool.WorkflowImportTool
```

