package com.campuslands.incidents.entity;

import com.campuslands.incidents.entity.enums.IncidentPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Horas máximas para resolver un incidente según su prioridad
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private IncidentPriority priority;

    @Column(name = "max_hours", nullable = false)
    private int maxHours;

    protected SlaPolicy() {
    }

    public SlaPolicy(IncidentPriority priority, int maxHours) {
        this.priority = priority;
        this.maxHours = maxHours;
    }

    public IncidentPriority getPriority() {
        return priority;
    }

    public int getMaxHours() {
        return maxHours;
    }

    public void setMaxHours(int maxHours) {
        this.maxHours = maxHours;
    }
}
