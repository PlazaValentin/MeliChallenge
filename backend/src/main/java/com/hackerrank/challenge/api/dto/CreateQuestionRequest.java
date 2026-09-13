package com.hackerrank.challenge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Alta de una pregunta sobre un pedido.
 *
 * @param productId    opcional: nulo cuando la pregunta es sobre el pedido en
 *                     general y no sobre un item puntual. Que el producto
 *                     pertenezca al pedido no se puede verificar aca (hace falta
 *                     el pedido), asi que lo valida el dominio y responde 400.
 * @param questionText obligatorio.
 */
public record CreateQuestionRequest(
    UUID productId,

    @NotBlank(message = "Es obligatorio.") @Size(max = CreateQuestionRequest.MAX_LENGTH, message = "No puede superar los {max} caracteres.") String questionText) {

  /** Limite defensivo contra un payload desproporcionado, no una regla de negocio. */
  static final int MAX_LENGTH = 2000;
}
