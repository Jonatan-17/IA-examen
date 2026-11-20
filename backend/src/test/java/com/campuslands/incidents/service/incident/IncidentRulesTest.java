package com.campuslands.incidents.service.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.campuslands.incidents.dto.incident.IncidentStatusUpdateRequest;
import com.campuslands.incidents.entity.Category;
import com.campuslands.incidents.entity.Incident;
import com.campuslands.incidents.entity.User;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.entity.enums.IncidentStatus;
import com.campuslands.incidents.exception.ConflictException;
import com.campuslands.incidents.repository.CategoryRepository;
import com.campuslands.incidents.repository.IncidentActivityRepository;
import com.campuslands.incidents.repository.IncidentRepository;
import com.campuslands.incidents.repository.SlaPolicyRepository;
import com.campuslands.incidents.repository.UserRepository;

class IncidentRulesTest {

    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final IncidentService service = new IncidentService(incidentRepository, mock(CategoryRepository.class),
            mock(UserRepository.class), mock(SlaPolicyRepository.class), mock(IncidentActivityRepository.class),
            "America/Bogota");

    private Incident incident(IncidentPriority priority) {
        Category category = new Category("Conectividad", null);
        ReflectionTestUtils.setField(category, "id", 1L);
        User user = new User("Laura", "laura@empresa.com", "Finanzas");
        ReflectionTestUtils.setField(user, "id", 1L);
        Incident incident = new Incident("Sin internet", "No hay red", category, user, priority);
        ReflectionTestUtils.setField(incident, "id", 7L);
        ReflectionTestUtils.setField(incident, "createdAt", Instant.now().minusSeconds(3600));
        when(incidentRepository.findWithCategoryAndUserById(7L)).thenReturn(Optional.of(incident));
        return incident;
    }

    @Test
    void cannotCloseAnOpenIncident() {
        incident(IncidentPriority.P3);
        assertThatThrownBy(() -> service.changeStatus(7L, new IncidentStatusUpdateRequest(IncidentStatus.CLOSED)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IncidentService.CLOSE_ONLY_RESOLVED);
    }

    @Test
    void criticalIncidentNeedsResolutionNote() {
        incident(IncidentPriority.P1);
        assertThatThrownBy(() -> service.changeStatus(7L, new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IncidentService.P1_NEEDS_RESOLUTION);
    }

    @Test
    void criticalIncidentResolvesWithNote() {
        Incident incident = incident(IncidentPriority.P1);
        var response = service.changeStatus(7L,
                new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Se reinició el router", null));
        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolution()).isEqualTo("Se reinició el router");
    }
}
