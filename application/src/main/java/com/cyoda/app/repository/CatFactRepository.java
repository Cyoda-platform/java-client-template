package com.cyoda.app.repository;

import com.cyoda.app.entity.CatFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatFactRepository extends JpaRepository<CatFact, String> {
}
