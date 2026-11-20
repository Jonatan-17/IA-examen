package com.campuslands.incidents.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.campuslands.incidents.dto.history.RatingRequest;
import com.campuslands.incidents.entity.AiQuery;
import com.campuslands.incidents.entity.AiResponse;
import com.campuslands.incidents.entity.KnowledgeArticle;
import com.campuslands.incidents.entity.Rating;
import com.campuslands.incidents.entity.ResponseSource;
import com.campuslands.incidents.repository.AiResponseRepository;
import com.campuslands.incidents.repository.RatingRepository;

class RatingFeedbackTest {

    private final AiResponseRepository responses = mock(AiResponseRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private final RatingService service = new RatingService(responses, ratings);
    private final KnowledgeArticle article = new KnowledgeArticle("t", "p", "s", "c", "sol");

    private void responseWithSource() {
        AiResponse response = mock(AiResponse.class);
        ResponseSource source = mock(ResponseSource.class);
        when(source.getArticle()).thenReturn(article);
        when(response.getSources()).thenReturn(List.of(source));
        when(response.getQuery()).thenReturn(mock(AiQuery.class));
        when(responses.findWithQueryById(1L)).thenReturn(Optional.of(response));
        when(ratings.saveAndFlush(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void lowRatingMarksArticlesForReview() {
        responseWithSource();
        service.rate(1L, new RatingRequest(2, "No sirvió"));
        assertThat(article.isNeedsReview()).isTrue();
    }

    @Test
    void goodRatingDoesNotMarkArticles() {
        responseWithSource();
        service.rate(1L, new RatingRequest(5, null));
        assertThat(article.isNeedsReview()).isFalse();
    }
}
