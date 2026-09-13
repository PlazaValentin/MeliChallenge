package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.Order;

import java.util.UUID;

/**
 * Respuesta de las acciones que cambian el estado de un pedido.
 *
 * <p>
 * Devuelve solo el id: quien disparo la accion ya sabe que estado pidio, y si
 * necesita el recurso actualizado lo consulta (ver DECISIONS.md). El campo se
 * llama {@code orderId} y no {@code id} para que el nombre diga de que recurso
 * se trata sin depender del contexto de la llamada.
 */
public record OrderIdResponse(UUID orderId) {

  public static OrderIdResponse from(Order order) {
    return new OrderIdResponse(order.getId());
  }
}
