package com.hackerrank.challenge.infrastructure.persistence;

import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store principal en memoria para {@link Question}. Ver
 * {@link InMemoryOrderRepository}.
 */
@Repository
public class InMemoryQuestionRepository implements QuestionRepository {

  private final Map<UUID, Question> store = new ConcurrentHashMap<>();

  @Override
  public Question save(Question question) {
    store.put(question.getId(), question);
    return question;
  }

  @Override
  public Optional<Question> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Question> findByOrderId(UUID orderId) {
    return store.values().stream()
        .filter(question -> question.getOrderId().equals(orderId))
        .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
        .toList();
  }

  @Override
  public List<Question> findUnresolved() {
    return store.values().stream()
        .filter(Question::isUnresolved)
        .toList();
  }
}
