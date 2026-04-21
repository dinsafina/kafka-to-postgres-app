package com.kafkapp.consumer;

import com.kafkapp.model.Event;
import com.kafkapp.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventRepository repository;
    private final RestTemplate restTemplate;   // бин создадим ниже

    @KafkaListener(topics = "events-topic", groupId = "postgres-saver-group")
    public void consume(Event event) {
        log.info("Получено сообщение из Kafka: {}", event);

        try {
            // === Запрос к WireMock ===
            String wiremockUrl = "http://wiremock:8080/api/mock-response";
            ResponseEntity<String> response = restTemplate.getForEntity(wiremockUrl, String.class);

            String mockResponseBody = response.getBody();
            log.info("Ответ от WireMock: {}", mockResponseBody);

            // === Сохраняем в БД ===
            event.setWiremockResponse(mockResponseBody);
            repository.save(event);

            log.info("Событие успешно сохранено в Postgres с id={}", event.getId());

        } catch (Exception e) {
            log.error("Ошибка при обработке события", e);
            // Здесь можно добавить retry или dead-letter topic
        }
    }
}