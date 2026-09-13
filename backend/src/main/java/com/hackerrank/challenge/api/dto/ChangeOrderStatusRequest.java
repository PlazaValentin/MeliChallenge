package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Estado al que se quiere llevar el pedido.
 *
 * <p>
 * Solo se valida que venga: un valor que no pertenece al ciclo de vida falla al
 * deserializarse y nunca llega hasta aca, asi que no hace falta enumerarlos.
 * Que
 * la transicion sea posible desde el estado actual no es formato sino regla de
 * negocio, y la decide {@link OrderStatus} (409, no 400).
 */
public record ChangeOrderStatusRequest(
    @NotNull(message = "Es obligatorio.") OrderStatus status) {
}
