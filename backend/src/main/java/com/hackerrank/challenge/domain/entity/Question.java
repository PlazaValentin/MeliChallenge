package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.exception.BusinessRuleException;
import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pregunta del comprador sobre un pedido, opcionalmente referida a un producto
 * puntual de ese pedido.
 *
 * <p>
 * Admite una sola respuesta: es un turno de la conversacion, no un hilo. Si el
 * comprador necesita repreguntar, crea otra pregunta sobre el mismo pedido.
 *
 * <p>
 * Se construye unicamente con {@link #on(Order, UUID, String, Instant)}, que
 * recibe el pedido para poder verificar que el producto referenciado le
 * pertenezca.
 * Ese chequeo no puede hacerse teniendo solo el {@code orderId}, y es la razon
 * por la
 * que no hay constructor publico.
 */
public final class Question {

    private final UUID id;
    private final UUID orderId;
    private final UUID productId;
    private final String questionText;
    private final Instant createdAt;
    private String answerText;
    private QuestionStatus status;

    private Question(UUID id, UUID orderId, UUID productId, String questionText, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.questionText = questionText;
        this.createdAt = createdAt;
        this.status = QuestionStatus.initial();
    }

    /**
     * Crea una pregunta sobre un pedido.
     *
     * <p>
     * Se permite preguntar sobre pedidos en estado terminal: que el pedido este
     * cerrado no significa que el post-venta lo este.
     *
     * @param productId opcional; si viene, debe ser un producto de ese pedido.
     */
    public static Question on(Order order, UUID productId, String questionText, Instant createdAt) {
        if (order == null) {
            throw new DomainValidationException("El pedido de la pregunta es obligatorio.");
        }
        if (questionText == null || questionText.isBlank()) {
            throw new DomainValidationException("El texto de la pregunta es obligatorio.");
        }
        if (createdAt == null) {
            throw new DomainValidationException("La fecha de la pregunta es obligatoria.");
        }
        if (productId != null && !order.containsProduct(productId)) {
            throw new DomainValidationException(
                    "El producto indicado no forma parte de este pedido.");
        }
        return new Question(
                UUID.randomUUID(), order.getId(), productId, questionText.trim(), createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    /**
     * Vacio cuando la pregunta es sobre el pedido en general y no sobre un item.
     */
    public Optional<UUID> getProductId() {
        return Optional.ofNullable(productId);
    }

    public String getQuestionText() {
        return questionText;
    }

    public Optional<String> getAnswerText() {
        return Optional.ofNullable(answerText);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public boolean isUnresolved() {
        return status.isUnresolved();
    }

    /**
     * Registra la respuesta del vendedor y pasa la pregunta a ANSWERED.
     *
     * <p>
     * Una pregunta ya respondida no admite otra respuesta: la transicion falla y
     * el texto anterior queda intacto.
     */
    public void answer(String text) {
        if (text == null || text.isBlank()) {
            throw new DomainValidationException("El texto de la respuesta es obligatorio.");
        }
        transitionTo(QuestionStatus.ANSWERED);
        this.answerText = text.trim();
    }

    /**
     * Cierre explicito por parte del vendedor. Solo se puede resolver lo
     * respondido.
     */
    public void resolve() {
        transitionTo(QuestionStatus.RESOLVED);
    }

    private void transitionTo(QuestionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "No se puede pasar la pregunta de " + status + " a " + target + ".");
        }
        this.status = target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Question question && id.equals(question.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
