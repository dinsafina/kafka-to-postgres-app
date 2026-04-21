1. Для запуска контейнера использовать команду 
docker compose up --build через git bash
в месте, где находится файл docker-compose.yml
2. Создание топика через Offset Explorer: в разделе topics создать топик
Например: events-topic
3. Перейти в partition > Data > знак + > Add Single Message
4. Создать в поле Value JSON, например: 
{
   "eventId": "evt-12345",
   "userId": "user-99999",
   "type": "ORDER_CREATED",
   "amount": 4999.99,
   "currency": "RUB",
   "timestamp": "2026-04-19T10:00:00Z"
   }
5. В разделе Data нажать на значок Play- появится сообщение
6. Создание топика через консоль bash:
  Создание топика:
docker exec -it kafka kafka-topics --create --topic events-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
 Отправка сообщения топик, например:
   docker exec -it kafka bash -c "echo '{\"eventId\":\"evt-12345\",\"userId\":\"user-99999\",\"type\":\"ORDER_CREATED\",\"amount\":4999.99,\"currency\":\"RUB\",\"timestamp\":\"2026-04-19T10:00:00Z\"}' | kafka-console-producer --topic events-topic --bootstrap-server localhost:9092"
7. Проверить, что топик готов: docker exec -it kafka kafka-topics --list --bootstrap-server kafka:9092
7. Проверка логов приложения: docker logs kafka-to-postgres-app --tail 20
8. Для создания талицы выполнить команду в терминале:
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

SELECT 'Таблица events успешно создана!' AS message;
"
8. Остановить все и удалить старые данные, в т.ч. БД:
docker compose down -v

9. Полезные команды:
* посмотреть запущенные контейнеры в Docker: docker ps -a
* посмотреть логи приложения: docker logs kafka-to-postgres-app --tail 20
* Проверить, что WireMock получил запрос от приложения
  Откройте в браузере: http://localhost:8081/__admin/requests
