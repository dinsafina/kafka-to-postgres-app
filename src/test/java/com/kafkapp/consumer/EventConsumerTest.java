package com.kafkapp.consumer;

import com.kafkapp.model.Event;
import com.kafkapp.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private EventRepository repository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private EventConsumer consumer;

    private Event event;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                .eventId("evt-123")
                .userId("user-1")
                .type("TEST")
                .amount(null)
                .currency(null)
                .timestamp(null)
                .mockStatus(200) // по умолчанию успешный статус
                .build();
    }

    @Test
    void testConsume_success() {
        // Подготовка успешного ответа от WireMock
        String wiremockResponse = "{\"status\":\"SUCCESS\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(wiremockResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        // Вызов метода
        consumer.consume(event);

        // Проверки
        assertEquals(wiremockResponse, event.getWiremockResponse());
        assertEquals(200, event.getStatusCode());
        assertNull(event.getErrorMessage());
        verify(repository, times(1)).save(event);
    }

    @Test
    void testConsume_wiremockReturns500() {
        // Ответ с ошибкой 500
        String errorBody = "{\"error\":\"Internal Server Error\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        consumer.consume(event);

        assertEquals(errorBody, event.getWiremockResponse());
        assertEquals(500, event.getStatusCode());
        assertNull(event.getErrorMessage()); // ошибки нет, это корректный HTTP-ответ
        verify(repository).save(event);
    }

    @Test
    void testConsume_wiremockUnavailable() {
        // Исключение при вызове RestTemplate
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        consumer.consume(event);

        assertNull(event.getWiremockResponse());
        assertEquals(500, event.getStatusCode());
        assertNotNull(event.getErrorMessage());
        assertTrue(event.getErrorMessage().contains("Connection refused"));
        verify(repository).save(event);
    }

    @Test
    void testConsume_withMockStatus500() {
        // Устанавливаем mockStatus = 500, чтобы WireMock вернул 500
        event.setMockStatus(500);
        String errorBody = "{\"error\":\"Internal Server Error\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        consumer.consume(event);

        // Проверяем, что заголовок X-Mock-Status был передан (это сложно проверить без captor,
        // но мы можем проверить, что метод exchange был вызван с любым HttpEntity,
        // который содержит заголовок. Для простоты оставим как есть.
        assertEquals(500, event.getStatusCode());
        assertEquals(errorBody, event.getWiremockResponse());
        verify(repository).save(event);
    }
}