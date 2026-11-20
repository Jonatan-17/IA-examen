package com.campuslands.incidents.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.campuslands.incidents.entity.SlaPolicy;
import com.campuslands.incidents.entity.enums.IncidentPriority;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, IncidentPriority> {
}
