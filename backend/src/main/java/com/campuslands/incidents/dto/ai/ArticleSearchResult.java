package com.campuslands.incidents.dto.ai;

// Artículo encontrado por búsqueda semántica
public record ArticleSearchResult(Long id, String title, boolean usableAsSource, double similarity) {
}
