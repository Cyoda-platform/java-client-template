# 🔗 Cyoda Integration Guide

## ✅ Реальные Cyoda Credentials добавлены

Приложение теперь настроено на подключение к реальному Cyoda EU instance.

### 📋 Конфигурация

**Cyoda Host:** `client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net`

**Client ID:** `kLXY45`

**Client Secret:** `OAIsUzQMP4LoW19JTwoi`

**Grant Type:** `client_credentials`

---

## 🚀 Запуск с реальной Cyoda

### Вариант 1: Использовать скрипт (РЕКОМЕНДУЕТСЯ)

```bash
bash RUN_WITH_CYODA.sh
```

Скрипт автоматически:
- Устанавливает Java path
- Собирает проект
- Запускает приложение с реальными credentials

### Вариант 2: Ручной запуск

```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

./gradlew bootRun \
  --args='--app.config.cyoda-host=client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net \
          --app.config.cyoda-client-id=kLXY45 \
          --app.config.cyoda-client-secret=OAIsUzQMP4LoW19JTwoi'
```

### Вариант 3: Через environment variables

```bash
export CYODA_HOST="client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net"
export CYODA_CLIENT_ID="kLXY45"
export CYODA_CLIENT_SECRET="OAIsUzQMP4LoW19JTwoi"

./gradlew bootRun
```

---

## 📊 Что изменилось

### application.yml
```yaml
app:
  config:
    cyoda-host: client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net
    cyoda-client-id: kLXY45
    cyoda-client-secret: OAIsUzQMP4LoW19JTwoi
```

### gRPC Configuration
```
gRPC Address: grpc-client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net
gRPC Port: 443
```

---

## ✅ Проверка подключения

### 1. Запустить приложение
```bash
bash RUN_WITH_CYODA.sh
```

### 2. Проверить статус gRPC
```bash
# Получить токен
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Проверить gRPC статус
curl http://localhost:8080/api/admin/grpc/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

**Ожидаемый результат:**
```json
{
  "connectionState": "READY",
  "observerState": "READY",
  "lastKeepAliveTimestampMs": 1234567890
}
```

### 3. Проверить логи
Ищите в логах:
- ✅ `gRPC Managed Channel state changed: CONNECTING -> READY`
- ✅ `Keep alive received`
- ✅ `Stream Observer state changes: ... -> READY`

---

## 🔐 Безопасность

### ⚠️ ВАЖНО: Не коммитить credentials в git!

Credentials уже добавлены в `application.yml` для удобства разработки.

**Для production:**
1. Удалить credentials из `application.yml`
2. Использовать environment variables
3. Использовать secrets management (AWS Secrets Manager, HashiCorp Vault и т.д.)

### Использование environment variables (безопасно)

```bash
# Очистить application.yml
# Установить переменные окружения
export APP_CONFIG_CYODA_HOST="client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net"
export APP_CONFIG_CYODA_CLIENT_ID="kLXY45"
export APP_CONFIG_CYODA_CLIENT_SECRET="OAIsUzQMP4LoW19JTwoi"

# Запустить
./gradlew bootRun
```

---

## 📝 Файлы конфигурации

### .env.example
Пример файла с переменными окружения. Скопируйте в `.env` и обновите значения.

### RUN_WITH_CYODA.sh
Скрипт для запуска с реальными credentials.

### application.yml
Основной конфиг приложения (содержит credentials для разработки).

---

## 🧪 Тестирование с реальной Cyoda

### Запустить все тесты
```bash
bash TESTING_SCRIPT.sh
```

### Проверить gRPC соединение
```bash
curl http://localhost:8080/api/admin/grpc/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.connectionState'
```

### Переподключиться к Cyoda
```bash
curl -X POST "http://localhost:8080/api/admin/grpc/reconnect?force=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

## 🐛 Решение проблем

### Проблема: "Unable to resolve host"
```
Failed to resolve name. status=Status{code=UNAVAILABLE, 
description=Unable to resolve host grpc-client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net
```

**Решение:**
- Проверить интернет соединение
- Проверить что Cyoda сервер доступен
- Проверить DNS резолюцию: `nslookup client-a680fca7878e4c73854cfce50b42a108-dev.eu.cyoda.net`

### Проблема: "Failed to get access token"
```
Failed to get access token. Will not set the Bearer Token
```

**Решение:**
- Проверить что credentials правильные
- Проверить что Cyoda сервер доступен
- Проверить логи для деталей ошибки

### Проблема: "TRANSIENT_FAILURE"
```
connectionState: TRANSIENT_FAILURE
```

**Решение:**
- Это нормально при первом подключении
- Приложение автоматически переподключится
- Проверить логи для деталей

---

## 📊 Статус подключения

### Возможные состояния

| Состояние | Описание |
|---|---|
| `IDLE` | Неактивно |
| `CONNECTING` | Подключается |
| `READY` | ✅ Готово |
| `TRANSIENT_FAILURE` | Временная ошибка (переподключается) |
| `SHUTDOWN` | Отключено |

---

## ✨ Итог

Приложение теперь полностью интегрировано с реальной Cyoda EU instance.

**Запустить:**
```bash
bash RUN_WITH_CYODA.sh
```

**Тестировать:**
```bash
bash TESTING_SCRIPT.sh
```

**Swagger UI:**
```
http://localhost:8080/api
```

