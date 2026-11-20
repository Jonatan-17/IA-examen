package com.campuslands.incidents.service.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.campuslands.incidents.client.N8nClient;
import com.campuslands.incidents.config.AiFeaturesProperties;
import com.campuslands.incidents.dto.ai.ArticleSearchResult;
import com.campuslands.incidents.dto.ai.ClassificationResponse;
import com.campuslands.incidents.dto.ai.N8nContracts.CategoryOption;
import com.campuslands.incidents.dto.ai.N8nContracts.ClassifyRequest;
import com.campuslands.incidents.dto.ai.N8nContracts.ClassifyResult;
import com.campuslands.incidents.dto.ai.N8nContracts.DraftRequest;
import com.campuslands.incidents.dto.ai.N8nContracts.DraftResult;
import com.campuslands.incidents.dto.ai.N8nContracts.SearchHit;
import com.campuslands.incidents.dto.ai.N8nContracts.SearchRequest;
import com.campuslands.incidents.dto.ai.N8nContracts.SearchResponse;
import com.campuslands.incidents.dto.ai.N8nContracts.SummaryRequest;
import com.campuslands.incidents.dto.ai.N8nContracts.SummaryResult;
import com.campuslands.incidents.dto.ai.SimilarIncidentResponse;
import com.campuslands.incidents.dto.knowledge.KnowledgeArticleRequest;
import com.campuslands.incidents.dto.knowledge.KnowledgeArticleResponse;
import com.campuslands.incidents.entity.Category;
import com.campuslands.incidents.entity.Incident;
import com.campuslands.incidents.entity.IncidentActivity;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.exception.AiServiceException;
import com.campuslands.incidents.exception.ConflictException;
import com.campuslands.incidents.exception.ResourceNotFoundException;
import com.campuslands.incidents.repository.CategoryRepository;
import com.campuslands.incidents.repository.IncidentActivityRepository;
import com.campuslands.incidents.repository.IncidentRepository;
import com.campuslands.incidents.repository.KnowledgeArticleRepository;
import com.campuslands.incidents.service.knowledge.KnowledgeArticleService;

// Funciones de IA: clasificar, buscar similares, buscar artículos y redactar borradores
@Service
public class AiToolsService {

    private static final int SIMILAR_TOP_K = 3;
    private static final int ARTICLE_TOP_K = 10;
    private static final String MISSING = "Por completar";

    private final N8nClient n8nClient;
    private final AiFeaturesProperties props;
    private final CategoryRepository categoryRepository;
    private final IncidentRepository incidentRepository;
    private final KnowledgeArticleRepository articleRepository;
    private final IncidentActivityRepository activityRepository;
    private final KnowledgeArticleService articleService;

    public AiToolsService(N8nClient n8nClient, AiFeaturesProperties props,
                          CategoryRepository categoryRepository, IncidentRepository incidentRepository,
                          KnowledgeArticleRepository articleRepository,
                          IncidentActivityRepository activityRepository,
                          KnowledgeArticleService articleService) {
        this.n8nClient = n8nClient;
        this.props = props;
        this.categoryRepository = categoryRepository;
        this.incidentRepository = incidentRepository;
        this.articleRepository = articleRepository;
        this.activityRepository = activityRepository;
        this.articleService = articleService;
    }

    // Sugiere categoría y prioridad; se valida que existan antes de devolverlas
    public ClassificationResponse classify(String text) {
        List<Category> categories = categoryRepository.findAll(Sort.by("name"));
        List<CategoryOption> options = categories.stream()
                .map(c -> new CategoryOption(c.getId(), c.getName(), c.getDescription()))
                .toList();
        ClassifyResult result = n8nClient.post(props.tasksPath(),
                new ClassifyRequest("classify", text, options), ClassifyResult.class);
        if (result == null) {
            throw new AiServiceException("AI service returned an empty classification");
        }

        Category category = categories.stream()
                .filter(c -> c.getId().equals(result.categoryId()))
                .findFirst()
                .orElse(null);
        IncidentPriority priority = parsePriority(result.priority());
        if (category == null || priority == null) {
            throw new AiServiceException("AI service returned an invalid classification");
        }
        return new ClassificationResponse(category.getId(), category.getName(), priority, result.reason());
    }

    // Incidentes resueltos parecidos (se omite excludeId, p. ej. el propio incidente)
    public List<SimilarIncidentResponse> similarIncidents(String text, Long excludeId) {
        Map<Long, Double> scores = search("incidents", text, props.similarIncidentsThreshold(), SIMILAR_TOP_K + 1);
        if (scores.isEmpty()) {
            return List.of();
        }
        List<Incident> incidents = incidentRepository.findAll(
                (root, q, cb) -> root.get("id").in(scores.keySet()), Sort.by("id"));
        return incidents.stream()
                .filter(i -> !i.getId().equals(excludeId) && i.getStatus().isResolved())
                .map(i -> new SimilarIncidentResponse(i.getId(), i.getTitle(), i.getCategory().getName(),
                        i.getStatus(), i.getResolution(), i.getResolvedAt(), scores.get(i.getId())))
                .sorted(Comparator.comparingDouble(SimilarIncidentResponse::similarity).reversed())
                .limit(SIMILAR_TOP_K)
                .toList();
    }

    // Búsqueda semántica en la base de conocimiento, sin generar respuesta
    public List<ArticleSearchResult> searchArticles(String text) {
        Map<Long, Double> scores = search("articles", text, props.articleSearchThreshold(), ARTICLE_TOP_K);
        return articleRepository.findAllById(scores.keySet()).stream()
                .map(a -> new ArticleSearchResult(a.getId(), a.getTitle(), a.isUsableAsSource(), scores.get(a.getId())))
                .sorted(Comparator.comparingDouble(ArticleSearchResult::similarity).reversed())
                .toList();
    }

    // Crea un artículo SIN verificar a partir de un incidente resuelto
    public KnowledgeArticleResponse draftArticle(Long incidentId) {
        Incident incident = incidentRepository.findWithCategoryAndUserById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
        if (!incident.getStatus().isResolved()) {
            throw new ConflictException("Solo se pueden documentar incidentes resueltos");
        }
        List<String> comments = activityRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId).stream()
                .map(IncidentActivity::getComment)
                .filter(c -> c != null && !c.isBlank())
                .toList();
        DraftResult draft = n8nClient.post(props.tasksPath(), new DraftRequest("draft-article",
                incident.getTitle(), incident.getCategory().getName(), incident.getDescription(),
                incident.getResolution(), comments), DraftResult.class);
        if (draft == null) {
            throw new AiServiceException("AI service returned an empty draft");
        }
        return articleService.create(new KnowledgeArticleRequest(
                limit(draft.title(), 200), limit(draft.problem(), 5000), limit(draft.symptoms(), 5000),
                limit(draft.cause(), 5000), limit(draft.solution(), 5000), false));
    }

    // Resumen corto del incidente y su bitácora
    public String summarize(Long incidentId) {
        Incident incident = incidentRepository.findWithCategoryAndUserById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
        List<String> events = activityRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId).stream()
                .map(a -> a.getCreatedAt() + " " + a.getType()
                        + (a.getToStatus() == null ? "" : " -> " + a.getToStatus())
                        + (a.getComment() == null ? "" : ": " + a.getComment()))
                .toList();
        SummaryResult result = n8nClient.post(props.tasksPath(), new SummaryRequest("summarize",
                incident.getTitle(), incident.getDescription(), incident.getStatus().name(),
                incident.getResolution(), events), SummaryResult.class);
        if (result == null || result.summary() == null || result.summary().isBlank()) {
            throw new AiServiceException("AI service returned an empty summary");
        }
        return result.summary().trim();
    }

    // id -> similitud de los resultados de Qdrant
    private Map<Long, Double> search(String collection, String text, double threshold, int topK) {
        SearchResponse response = n8nClient.post(props.searchPath(),
                new SearchRequest(collection, text, threshold, topK), SearchResponse.class);
        if (response == null) {
            throw new AiServiceException("AI service returned an empty search response");
        }
        return response.hits().stream()
                .collect(Collectors.toMap(SearchHit::id, SearchHit::score, Math::max));
    }

    private static IncidentPriority parsePriority(String value) {
        try {
            return value == null ? null : IncidentPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return MISSING;
        }
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
