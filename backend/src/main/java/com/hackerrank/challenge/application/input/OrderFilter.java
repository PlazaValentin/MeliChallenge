package com.hackerrank.challenge.application.input;

import com.hackerrank.challenge.domain.enums.OrderStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * Filtros del listado de pedidos tal como llegan desde afuera, con las fechas
 * todavia como {@link LocalDate}.
 *
 * <p>
 * Es deliberado que no sea el {@code OrderSearchCriteria} del repositorio: la
 * expansion del rango a instantes (inclusive en ambos extremos, cortando el dia
 * en la zona horaria del negocio) la hace el service, no quien arma el filtro.
 * Asi el borde de entrada no necesita saber nada de husos horarios.
 *
 * <p>
 * Todos los campos son opcionales; nulo o vacio significa "sin filtrar por
 * eso".
 */
public record OrderFilter(
    Set<OrderStatus> statuses,
    LocalDate from,
    LocalDate to,
    String buyerText) {

  public static OrderFilter none() {
    return new OrderFilter(null, null, null, null);
  }
}
