package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.OrderLine;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.QuestionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Pregunta tal como se ve dentro del detalle de un pedido.
 *
 * <p>
 * No lleva score ni prioridad individual: el vendedor ve la clasificacion
 * agregada del pedido, no el detalle de cada pregunta. El score por pregunta es
 * cosa de la cola de Operaciones (ver {@link UnresolvedQuestionResponse}).
 *
 * @param productId   nulo cuando la pregunta es sobre el pedido en general y no
 *                    sobre un item puntual.
 * @param productName nombre del producto referenciado, nulo junto con
 *                    {@code productId}. Viaja resuelto para que el frontend no
 *                    tenga que cruzarlo contra las lineas.
 * @param answerText  nulo mientras la pregunta no haya sido respondida.
 */
public record QuestionResponse(
    UUID id,
    UUID productId,
    String productName,
    String questionText,
    String answerText,
    QuestionStatus status,
    Instant createdAt) {

  /**
   * Resuelve el nombre del producto contra las lineas del pedido al que la
   * pregunta pertenece. El nombre sale de la linea y no del catalogo porque la
   * linea es el registro historico de lo que se compro: si el producto se
   * renombro despues, la pregunta sigue hablando del nombre que el comprador
   * vio.
   */
  public static QuestionResponse from(Question question, Order order) {
    return new QuestionResponse(
        question.getId(),
        question.getProductId().orElse(null),
        question.getProductId()
            .map(productId -> productNameIn(order, productId, question.getId()))
            .orElse(null),
        question.getQuestionText(),
        question.getAnswerText().orElse(null),
        question.getStatus(),
        question.getCreatedAt());
  }

  /**
   * Una invariante del dominio garantiza que el producto de una pregunta
   * pertenece a su pedido, asi que no encontrar la linea es una violacion de
   * integridad y no un caso de uso. Se corta con un error no contemplado (500),
   * con el mismo criterio que una pregunta que referencia un pedido inexistente:
   * devolver el nombre vacio esconderia el dato corrupto para siempre. El
   * mensaje lleva los tres ids para poder ubicar el caso sin exponer PII.
   */
  private static String productNameIn(Order order, UUID productId, UUID questionId) {
    return order.getLines().stream()
        .filter(line -> line.getProductId().equals(productId))
        .map(OrderLine::getProductName)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "La pregunta " + questionId + " referencia el producto " + productId
                + ", que no pertenece al pedido " + order.getId() + "."));
  }
}
