package com.hackerrank.challenge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Respuesta del vendedor a una pregunta.
 *
 * <p>
 * Que la pregunta admita ser respondida en su estado actual es regla de negocio
 * y la decide el dominio (409), no este DTO.
 */
public record AnswerQuestionRequest(
    @NotBlank(message = "Es obligatorio.") @Size(max = AnswerQuestionRequest.MAX_LENGTH, message = "No puede superar los {max} caracteres.") String answerText) {

  /** Limite defensivo contra un payload desproporcionado, no una regla de negocio. */
  static final int MAX_LENGTH = 2000;
}
