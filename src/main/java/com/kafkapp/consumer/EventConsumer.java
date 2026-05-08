package com.kafkapp.consumer;

import com.kafkapp.model.Event;
import com.kafkapp.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventRepository repository;
    private final RestTemplate restTemplate;

    @KafkaListener(topics = "events-topic", groupId = "postgres-saver-group")
    public void consume(Event event) {
        log.info("Получено сообщение из Kafka: {}", event);

        try {
            // Определяем желаемый статус (по умолчанию 200)
            int desiredStatus = (event.getMockStatus() != null) ? event.getMockStatus() : 200;

            // Формируем заголовок
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Mock-Status", String.valueOf(desiredStatus));
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String wiremockUrl = "http://wiremock:8080/api/mock-response";
            ResponseEntity<String> response = restTemplate.exchange(
                    wiremockUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // Успешный ответ (даже если статус 4xx/5xx – он пришёл от WireMock, это не исключение)
            event.setWiremockResponse(response.getBody());
            event.setStatusCode(response.getStatusCodeValue());
            event.setErrorMessage(null);
            log.info("Ответ от WireMock (статус {}): {}", response.getStatusCodeValue(), response.getBody());

        } catch (HttpServerErrorException | HttpClientErrorException e) {
            // Этот блок может не сработать, если RestTemplate настроен не бросать исключения на 4xx/5xx.
            // Оставлен на случай кастомной конфигурации.
            log.error("WireMock вернул ошибку: статус {}, тело {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            event.setWiremockResponse(e.getResponseBodyAsString());
            event.setStatusCode(e.getStatusCode().value());
            event.setErrorMessage(e.getMessage());

        } catch (RestClientException e) {
            // Ошибки соединения, таймауты и т.д.
            log.error("Не удалось вызвать WireMock: {}", e.getMessage());
            event.setWiremockResponse(null);
            event.setStatusCode(500);
            event.setErrorMessage(e.getMessage());
        }

        // Сохраняем событие в БД в любом случае
        repository.save(event);
        log.info("Событие сохранено в Postgres с id={}, статус={}", event.getId(), event.getStatusCode());
    }
}