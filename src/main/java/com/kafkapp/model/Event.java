package com.kafkapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "user_id")
    private String userId;

    private String type;
    private BigDecimal amount;
    private String currency;

    @Column(name = "timestamp")
    private ZonedDateTime timestamp;

    @Column(name = "wiremock_response", columnDefinition = "TEXT")
    private String wiremockResponse;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt = ZonedDateTime.now();
}