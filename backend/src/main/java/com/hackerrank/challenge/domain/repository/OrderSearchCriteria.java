package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Filtros combinables con AND para el listado de pedidos de un vendedor.
 *
 * <p>
 * {@code createdFrom} y {@code createdBefore} ya vienen resueltos como límites
 * de instante (inclusive / exclusive): quien arma el criterio es responsable de
 * expandir el "hasta" al inicio del día siguiente en la zona horaria del
 * negocio
 * (ver DECISIONS.md, sección "Fechas"). El repositorio solo compara instantes,
 * sin
 * aplicar funciones sobre el campo filtrado.
 *
 * <p>
 * {@code buyerText} matchea contra nombre y email del comprador: substring,
 * case-insensitive y sin acentos es responsabilidad de la implementación del
 * repositorio, no de este objeto.
 */
public record OrderSearchCriteria(
    Set<OrderStatus> statuses,
    Instant createdFrom,
    Instant createdBefore,
    String buyerText) {

  public static OrderSearchCriteria none() {
    return new OrderSearchCriteria(null, null, null, null);
  }

  public Optional<Set<OrderStatus>> statusFilter() {
    return statuses == null || statuses.isEmpty() ? Optional.empty() : Optional.of(statuses);
  }

  public Optional<Instant> createdFromFilter() {
    return Optional.ofNullable(createdFrom);
  }

  public Optional<Instant> createdBeforeFilter() {
    return Optional.ofNullable(createdBefore);
  }

  public Optional<String> buyerTextFilter() {
    return buyerText == null || buyerText.isBlank() ? Optional.empty() : Optional.of(buyerText);
  }
}
