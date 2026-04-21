# Kafka → Postgres Demo

Пример приложения, которое читает события из Kafka, отправляет запрос в WireMock и сохраняет результат в PostgreSQL.

---

# Архитектура

```
Producer → Kafka → kafka-to-postgres-app → WireMock → Postgres
```

Приложение:

* читает сообщения из Kafka
* отправляет HTTP запрос в WireMock
* сохраняет событие и ответ WireMock в PostgreSQL

---

# Стек

* Java 17
* Spring Boot
* Apache Kafka
* PostgreSQL
* WireMock
* Docker / Docker Compose

---

# Быстрый старт

## 1. Запуск контейнеров

В директории проекта:

```bash
docker compose up --build
```

Будут запущены:

* Kafka
* Zookeeper (если используется)
* PostgreSQL
* WireMock
* kafka-to-postgres-app

---

# Отправка события в Kafka

## Вариант 1 — Offset Explorer

1. Открыть раздел **Topics**
2. Создать топик:

```
events-topic
```

3. Перейти:

```
Partition → Data → + → Add Single Message
```

4. Вставить JSON в поле **Value**:

```json
{
  "eventId": "evt-12345",
  "userId": "user-99999",
  "type": "ORDER_CREATED",
  "amount": 4999.99,
  "currency": "RUB",
  "timestamp": "2026-04-19T10:00:00Z"
}
```

5. Нажать **Play**

---

## Вариант 2 — Через консоль

### Создание топика

```bash
docker exec -it kafka kafka-topics \
--create \
--topic events-topic \
--bootstrap-server localhost:9092 \
--partitions 1 \
--replication-factor 1
```

### Отправка сообщения

```bash
docker exec -it kafka bash -c "echo '{
\"eventId\":\"evt-12345\",
\"userId\":\"user-99999\",
\"type\":\"ORDER_CREATED\",
\"amount\":4999.99,
\"currency\":\"RUB\",
\"timestamp\":\"2026-04-19T10:00:00Z\"
}' | kafka-console-producer \
--topic events-topic \
--bootstrap-server localhost:9092"
```

---

# Проверка работы

## Проверка логов приложения

```bash
docker logs kafka-to-postgres-app --tail 50
```

Ожидаемый результат:

* сообщение получено из Kafka
* выполнен запрос в WireMock
* запись сохранена в PostgreSQL

---

# Проверка WireMock

Открыть в браузере:

```
http://localhost:8081/__admin/requests
```

Здесь можно увидеть входящие запросы от приложения.

---

# Создание таблицы в PostgreSQL

Если таблица не создана автоматически:

```bash
docker exec -it postgres psql -U postgres -d kafkadb -c "
CREATE TABLE IF NOT EXISTS events (
    id SERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(10,2),
    currency VARCHAR(10),
    timestamp TIMESTAMP WITH TIME ZONE,
    wiremock_response TEXT,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_event_id ON events(event_id);
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);
"
```

---

# Проверка данных в БД

```bash
docker exec -it postgres psql -U postgres -d kafkadb
```

SQL:

```sql
SELECT * FROM events;
```

---

# Полезные команды

## Список контейнеров

```bash
docker ps -a
```

## Логи приложения

```bash
docker logs kafka-to-postgres-app --tail 50
```

## Список топиков

```bash
docker exec -it kafka kafka-topics --list --bootstrap-server kafka:9092
```

---

# Остановка проекта

Остановить контейнеры:

```bash
docker compose down
```

Удалить контейнеры и volumes (включая БД):

```bash
docker compose down -v
```

---

# Структура проекта

```
.
├── docker-compose.yml
├── Dockerfile
├── kafka-to-postgres-app
├── wiremock
└── README.md
```

---

# Поток обработки события

1. Сообщение отправляется в Kafka
2. Приложение читает сообщение
3. Приложение отправляет HTTP запрос в WireMock
4. WireMock возвращает mock response
5. Приложение сохраняет данные в PostgreSQL

---

# Пример события

```json
{
  "eventId": "evt-12345",
  "userId": "user-99999",
  "type": "ORDER_CREATED",
  "amount": 4999.99,
  "currency": "RUB",
  "timestamp": "2026-04-19T10:00:00Z"
}
```

---

# Порты

| Сервис   | Порт |
| -------- | ---- |
| Kafka    | 9092 |
| Postgres | 5432 |
| WireMock | 8081 |
| App      | 8080 |

---
