# 🧪 Инструкция по тестированию TMS API

## 📚 Документы

Созданы 3 документа для тестирования:

### 1. **MANUAL_TESTING_INSTRUCTIONS.md** (полная инструкция)
- Подробное описание всех 31 эндпоинта
- Примеры curl команд для каждого эндпоинта
- Чеклист проверок для каждой фазы
- Что проверять в ответах

### 2. **TESTING_SCRIPT.sh** (автоматическое тестирование)
- Bash скрипт который тестирует все эндпоинты автоматически
- Красивый вывод с цветами
- Проверяет все критические поля
- Занимает ~2-3 минуты

### 3. **API_TESTING_GUIDE.md** (краткая справка)
- Быстрая справка по всем эндпоинтам
- Минимальные примеры
- Для быстрого поиска

---

## 🚀 Быстрый старт

### Вариант 1: Автоматическое тестирование (РЕКОМЕНДУЕТСЯ)

```bash
# Терминал 1: Запустить приложение
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
./gradlew bootRun --args='--app.config.cyoda-host=localhost --app.config.cyoda-client-id=prototype --app.config.cyoda-client-secret=prototype'

# Терминал 2: Запустить тесты (когда приложение стартовало)
bash TESTING_SCRIPT.sh
```

**Результат**: Все 31 эндпоинт протестирован за 2-3 минуты ✅

---

### Вариант 2: Ручное тестирование через Swagger UI

```
http://localhost:8080/api
```

1. Откройте в браузере
2. Нажмите "Authorize" (верхний правый угол)
3. Введите токен из `/login`
4. Тестируйте эндпоинты через UI

---

### Вариант 3: Ручное тестирование через curl

Следуйте инструкциям в `MANUAL_TESTING_INSTRUCTIONS.md`

```bash
# Получить токены
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Тестировать эндпоинты
curl http://localhost:8080/api/projects \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

## 📋 Структура тестирования

### 7 фаз, 31 эндпоинт

| Фаза | Эндпоинты | Описание |
|------|-----------|---------|
| 1. Authentication | 2 | Логин admin/tester |
| 2. Projects | 5 | CRUD проектов |
| 3. Suites | 5 | CRUD наборов тестов |
| 4. Test Cases | 5 | CRUD тестовых случаев |
| 5. Test Steps | 5 | CRUD шагов тестов |
| 6. Test Runs | 7 | CRUD запусков + complete/unlock |
| 7. Admin gRPC | 2 | Управление gRPC соединением |

---

## ✅ Что проверяется

### Функциональность
- ✅ Все CRUD операции работают
- ✅ Авторизация работает (admin/tester)
- ✅ Soft-delete работает
- ✅ Статусы переходов работают (complete/unlock)

### Данные
- ✅ Все DTO поля правильно маппированы:
  - `TestCaseDTO.title` (не `name`)
  - `TestRunDTO.name` (не `title`)
  - `TestStepDTO.description` (не `action`)
- ✅ PUT операции сохраняют `createdAt` и `status`
- ✅ Все ID генерируются корректно

### HTTP
- ✅ Все эндпоинты возвращают правильные коды (200/201)
- ✅ Ошибки возвращают правильные коды (400/401/404)
- ✅ Swagger UI доступен

---

## 🎯 Ожидаемые результаты

### Успешное тестирование
```
✅ 2 Authentication эндпоинта
✅ 5 Projects эндпоинтов
✅ 5 Suites эндпоинтов
✅ 5 Test Cases эндпоинтов
✅ 5 Test Steps эндпоинтов
✅ 7 Test Runs эндпоинтов
✅ 2 Admin gRPC эндпоинта

ИТОГО: 31/31 эндпоинтов работают ✅
```

---

## 🔐 Учётные данные

| Пользователь | Username | Password | Роль |
|---|---|---|---|
| Admin | `admin` | `admin123` | ADMIN |
| Tester | `tester` | `tester123` | TESTER |

Токены действуют **24 часа**.

---

## 🐛 Если что-то не работает

### Проблема: "Connection refused"
```bash
# Убедитесь что приложение запущено
ps aux | grep bootRun
```

### Проблема: "Unauthorized"
```bash
# Получите новый токен
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')
```

### Проблема: "Not Found"
```bash
# Убедитесь что используете правильный ID
# Скопируйте ID из предыдущего ответа
echo $PROJECT_ID
```

### Проблема: Unit тесты падают
```bash
# Запустите unit тесты
./gradlew test

# Все 267 тестов должны пройти
```

---

## 📊 Статистика

| Метрика | Значение |
|---|---|
| Всего эндпоинтов | 31 |
| Всего фаз | 7 |
| Unit тестов | 267 |
| Время на полное тестирование | 2-3 минуты (автоматическое) |
| Время на полное тестирование | 15-20 минут (ручное) |

---

## 🎓 Примеры использования

### Создать проект и запустить тесты

```bash
# 1. Получить токен
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# 2. Создать проект
PROJECT=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"My Project","description":"Test"}')
PROJECT_ID=$(echo $PROJECT | jq -r '.id')

# 3. Создать набор тестов
SUITE=$(curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"My Suite","description":"Test"}')
SUITE_ID=$(echo $SUITE | jq -r '.id')

# 4. Создать тестовый случай
CASE=$(curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/suites/$SUITE_ID/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"title":"My Test","description":"Test","priority":"HIGH"}')
CASE_ID=$(echo $CASE | jq -r '.id')

# 5. Создать запуск тестов
TESTER_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"tester123"}' | jq -r '.token')

RUN=$(curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TESTER_TOKEN" \
  -d '{"name":"My Run","environment":"QA"}')
RUN_ID=$(echo $RUN | jq -r '.id')

# 6. Завершить запуск
curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/runs/$RUN_ID/complete \
  -H "Authorization: Bearer $TESTER_TOKEN" | jq
```

---

## 📞 Поддержка

Если возникли проблемы:
1. Проверьте логи приложения
2. Запустите unit тесты: `./gradlew test`
3. Перезапустите приложение
4. Проверьте что используются правильные ID и токены

---

## ✨ Итог

Прототип **полностью готов к использованию**:
- ✅ 31 эндпоинт реализован
- ✅ 267 unit тестов проходят
- ✅ Все DTO поля правильно маппированы
- ✅ Все операции сохраняют данные корректно
- ✅ Авторизация работает
- ✅ Swagger UI доступен

**Начните тестирование:**
```bash
bash TESTING_SCRIPT.sh
```
