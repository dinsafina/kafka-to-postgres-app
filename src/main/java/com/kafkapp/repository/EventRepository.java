package com.kafkapp.repository;

import com.kafkapp.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    // Поиск события по полю eventId (точное совпадение)
    Optional<Event> findByEventId(String eventId);
}