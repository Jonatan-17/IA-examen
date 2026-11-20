package com.campuslands.incidents.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campuslands.incidents.dto.ai.ArticleSearchResult;
import com.campuslands.incidents.dto.ai.ClassificationResponse;
import com.campuslands.incidents.dto.ai.SimilarIncidentResponse;
import com.campuslands.incidents.dto.ai.TextRequest;
import com.campuslands.incidents.dto.indexing.IndexingResult;
import com.campuslands.incidents.dto.knowledge.KnowledgeArticleResponse;
import com.campuslands.incidents.service.ai.AiToolsService;
import com.campuslands.incidents.service.ai.IncidentIndexingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Funciones de IA que pasan por n8n (el frontend nunca llama a n8n directamente)
@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "AI tools")
public class AiToolsController {

    private final AiToolsService aiTools;
    private final IncidentIndexingService incidentIndexing;

    public AiToolsController(AiToolsService aiTools, IncidentIndexingService incidentIndexing) {
        this.aiTools = aiTools;
        this.incidentIndexing = incidentIndexing;
    }

    @PostMapping("/incidents/classify")
    @Operation(summary = "Suggest category and priority for an incident text")
    public ClassificationResponse classify(@Valid @RequestBody TextRequest request) {
        return aiTools.classify(request.fullText());
    }

    @PostMapping("/incidents/similar")
    @Operation(summary = "Resolved incidents similar to a text (excludeId skips one incident)")
    public List<SimilarIncidentResponse> similar(@Valid @RequestBody TextRequest request,
                                                 @RequestParam(required = false) Long excludeId) {
        return aiTools.similarIncidents(request.fullText(), excludeId);
    }

    @PostMapping("/incidents/reindex")
    @Operation(summary = "Index every resolved incident in the vector store (the rest are removed)")
    public ResponseEntity<IndexingResult> reindex() {
        IndexingResult result = incidentIndexing.reindexAll();
        return ResponseEntity.status(result.successful() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(result);
    }

    @PostMapping("/incidents/{id}/article-draft")
    @Operation(summary = "Create an unverified knowledge article drafted by the AI from a resolved incident")
    public ResponseEntity<KnowledgeArticleResponse> draft(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiTools.draftArticle(id));
    }

    @PostMapping("/incidents/{id}/summary")
    @Operation(summary = "Short AI summary of the incident and its timeline")
    public Map<String, String> summary(@PathVariable Long id) {
        return Map.of("summary", aiTools.summarize(id));
    }

    @GetMapping("/knowledge-articles/search")
    @Operation(summary = "Semantic search in the knowledge base")
    public List<ArticleSearchResult> search(
            @RequestParam @NotBlank(message = "Escribe qué buscar") @Size(max = 500) String q) {
        return aiTools.searchArticles(q.trim());
    }
}
