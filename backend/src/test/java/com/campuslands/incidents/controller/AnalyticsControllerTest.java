package com.campuslands.incidents.controller;

//lo que hicimos aca fuie que creamos un test unitario para el controller de analytics, para probar que los filtros lleguen al servicio y que las listas se devuelvan como arreglos JSON. Tambien probamos que si el rango de fechas esta invertido o si la prioridad es desconocida


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.dto.analytics.CategoryCount;
import com.campuslands.incidents.dto.analytics.SummaryResponse;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.exception.GlobalExceptionHandler;
import com.campuslands.incidents.service.analytics.AnalyticsService;

class AnalyticsControllerTest {

    private final AnalyticsService service = mock(AnalyticsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void filtersArriveAtTheService() throws Exception {
        AnalyticsFilter expected = new AnalyticsFilter(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1L, IncidentPriority.P2);
        when(service.summary(expected)).thenReturn(new SummaryResponse(10, 8, 80.0, 5.5,
                new CategoryCount(1L, "Conectividad", 10, 100.0)));

        mvc.perform(get("/api/analytics/summary")
                        .param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("categoryId", "1").param("priority", "P2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.resolutionRate").value(80.0))
                .andExpect(jsonPath("$.topCategory.category").value("Conectividad"));
    }

    @Test
    void listsAreReturnedAsJsonArrays() throws Exception {
        when(service.byCategory(any())).thenReturn(List.of(new CategoryCount(1L, "Conectividad", 41, 27.33)));

        mvc.perform(get("/api/analytics/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidents").value(41))
                .andExpect(jsonPath("$[0].percentage").value(27.33));
    }

    @Test
    void reversedDateRangeIs400() throws Exception {
        mvc.perform(get("/api/analytics/weekly-trend").param("from", "2026-11-01").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());

        verify(service, never()).weeklyTrend(any());
    }

    @Test
    void unknownPriorityIs400() throws Exception {
        mvc.perform(get("/api/analytics/by-priority").param("priority", "P9"))
                .andExpect(status().isBadRequest());

        verify(service, never()).byPriority(any());
    }
}
