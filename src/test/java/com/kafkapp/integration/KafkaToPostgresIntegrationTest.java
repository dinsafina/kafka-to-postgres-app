package com.kafkapp.integration;

import com.kafkapp.model.Event;
import com.kafkapp.repository.EventRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class KafkaToPostgresIntegrationTest {

    // PostgreSQL контейнер
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // Kafka контейнер
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    // WireMock контейнер
    @Container
    static GenericContainer<?> wiremock = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.2"))
            .withExposedPorts(8080)
            .withCommand("--verbose"); // можно добавить --global-response-templating

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EventRepository eventRepository;

    // Динамически переопределяем свойства приложения для тестов
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Подключаемся к контейнеру PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Подключаемся к контейнеру Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Адрес WireMock (внутри Docker сети тестов)
        registry.add("wiremock.url", () -> "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080));
        // Для совместимости с Kafka (если нужны топики)
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
    }

    @Test
    @Disabled
    void testFullFlowSuccess() throws Exception {
        // 1. Подготовка JSON сообщения для Kafka
        String eventJson = """
                {
                    "eventId": "int-test-1",
                    "userId": "user-int",
                    "type": "INTEGRATION",
                    "amount": 199.99,
                    "currency": "USD",
                    "timestamp": "2026-04-21T12:00:00Z",
                    "mockStatus": 200
                }
                """;

        // 2. Отправляем сообщение в топик events-topic
        kafkaTemplate.send("events-topic", eventJson).get();

        // 3. Ожидаем, что событие сохранится в БД (асинхронно, максимум 10 секунд)
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Event saved = eventRepository.findByEventId("int-test-1")
                            .orElseThrow(() -> new AssertionError("Событие не найдено"));
                    assertEquals("user-int", saved.getUserId());
                    assertEquals("INTEGRATION", saved.getType());
                    assertEquals(199.99, saved.getAmount().doubleValue());
                    assertEquals("USD", saved.getCurrency());
                    assertEquals(200, saved.getStatusCode()); // Успешный ответ от WireMock
                    assertNotNull(saved.getWiremockResponse());
                    assertNull(saved.getErrorMessage());
                });
    }

    @Test
    @Disabled
    void testFullFlowWithError500() throws Exception {
        // Отправляем сообщение с mockStatus = 500, чтобы WireMock вернул ошибку
        String eventJson = """
                {
                    "eventId": "int-test-500",
                    "userId": "user-500",
                    "type": "ERROR_FLOW",
                    "amount": 0,
                    "currency": "RUB",
                    "timestamp": "2026-04-21T13:00:00Z",
                    "mockStatus": 500
                }
                """;

        kafkaTemplate.send("events-topic", eventJson).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Event saved = eventRepository.findByEventId("int-test-500")
                            .orElseThrow(() -> new AssertionError("Событие не найдено"));
                    assertEquals(500, saved.getStatusCode());
                    assertNotNull(saved.getWiremockResponse());
                    assertTrue(saved.getWiremockResponse().contains("Internal Server Error"));
                    assertNull(saved.getErrorMessage()); // нет ошибки соединения
                });
    }

    @Test
    @Disabled
    void testFullFlowWiremockUnavailable() throws Exception {
        // Останавливаем WireMock контейнер, чтобы симулировать недоступность сервиса
        wiremock.stop();

        String eventJson = """
                {
                    "eventId": "int-test-unreachable",
                    "userId": "user-unreachable",
                    "type": "NO_WIREMOCK",
                    "amount": 0,
                    "currency": "EUR",
                    "timestamp": "2026-04-21T14:00:00Z",
                    "mockStatus": 200
                }
                """;

        kafkaTemplate.send("events-topic", eventJson).get();

        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    Event saved = eventRepository.findByEventId("int-test-unreachable")
                            .orElseThrow(() -> new AssertionError("Событие не найдено"));
                    assertEquals(500, saved.getStatusCode()); // приложение выставило статус 500
                    assertNull(saved.getWiremockResponse());
                    assertNotNull(saved.getErrorMessage());
                    assertTrue(saved.getErrorMessage().contains("Connection refused") ||
                            saved.getErrorMessage().contains("Unreachable"));
                });

        // Перезапускаем WireMock для последующих тестов
        wiremock.start();
    }
}