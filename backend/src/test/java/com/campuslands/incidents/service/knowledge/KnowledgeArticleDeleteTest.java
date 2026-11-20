package com.campuslands.incidents.service.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.campuslands.incidents.entity.KnowledgeArticle;
import com.campuslands.incidents.exception.ConflictException;
import com.campuslands.incidents.repository.KnowledgeArticleRepository;
import com.campuslands.incidents.repository.ResponseSourceRepository;

class KnowledgeArticleDeleteTest {

    private final KnowledgeArticleRepository articles = mock(KnowledgeArticleRepository.class);
    private final ResponseSourceRepository sources = mock(ResponseSourceRepository.class);
    private final KnowledgeArticleService service =
            new KnowledgeArticleService(articles, sources, mock(ApplicationEventPublisher.class));
    private final KnowledgeArticle article = new KnowledgeArticle("t", "p", "s", "c", "sol");

    @Test
    void deletesUnusedArticle() {
        when(articles.findById(3L)).thenReturn(Optional.of(article));
        service.delete(3L);
        verify(articles).delete(article);
    }

    @Test
    void refusesArticleUsedAsSource() {
        when(articles.findById(3L)).thenReturn(Optional.of(article));
        when(sources.existsByArticleId(3L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(ConflictException.class);
        verify(articles, never()).delete(article);
    }
}
