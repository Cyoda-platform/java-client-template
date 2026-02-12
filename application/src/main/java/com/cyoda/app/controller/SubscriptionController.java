package com.cyoda.app.controller;

import com.cyoda.app.dto.SubscriberDto;
import com.cyoda.app.entity.Subscriber;
import com.cyoda.app.repository.SubscriberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriberRepository repository;

    public SubscriptionController(SubscriberRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscriberDto dto) {
        if (dto.getEmail() == null || dto.getEmail().isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(dto.getEmail());
        subscriber.setOptInTimestamp(OffsetDateTime.now());
        subscriber.setUnsubscribed(false);
        repository.save(subscriber);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam("email") String email) {
        Optional<Subscriber> maybe = repository.findById(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Subscriber subscriber = maybe.get();
        subscriber.setUnsubscribed(true);
        subscriber.setUnsubscribeTimestamp(OffsetDateTime.now());
        repository.save(subscriber);

        return ResponseEntity.ok().build();
    }
}
