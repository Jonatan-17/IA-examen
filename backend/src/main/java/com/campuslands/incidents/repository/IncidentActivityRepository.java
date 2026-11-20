package com.campuslands.incidents.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.campuslands.incidents.entity.IncidentActivity;

public interface IncidentActivityRepository extends JpaRepository<IncidentActivity, Long> {

    // Bitácora en orden cronológico, con el usuario cargado
    @EntityGraph(attributePaths = "user")
    List<IncidentActivity> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
