package com.cyoda.app.repository;

import com.cyoda.app.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, String> {
    List<Subscriber> findByUnsubscribedFalse();
}
