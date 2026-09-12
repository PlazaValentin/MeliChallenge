package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.entity.Question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de persistencia para {@link Question}.
 *
 * <p>
 * No expone un filtro por {@code sellerId} en {@link #findUnresolved()}: la
 * pregunta no conoce al vendedor (solo al pedido), así que cruzar por vendedor
 * es
 * responsabilidad del service, combinando este método con
 * {@link OrderRepository#findBySeller}. Mantener los repositorios sin conocerse
 * entre sí evita acoplar la persistencia de preguntas a la de pedidos.
 */
public interface QuestionRepository {

  Question save(Question question);

  Optional<Question> findById(UUID id);

  /** Preguntas de un pedido, para el detalle embebido (ver DECISIONS.md). */
  List<Question> findByOrderId(UUID orderId);

  /** Preguntas en OPEN o ANSWERED, de todos los vendedores. */
  List<Question> findUnresolved();
}
