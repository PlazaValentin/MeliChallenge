package com.hackerrank.challenge.api.dto;

import java.util.List;

/**
 * Envoltorio comun de los listados de la API.
 *
 * <p>
 * La clave es {@code items} y no el nombre del recurso a proposito: permite un
 * unico tipo reutilizable y hace que agregar metadata de paginacion sea un
 * cambio en un solo lugar, en linea con que la paginacion esta declarada como
 * proxima prioridad (ver DECISIONS.md).
 */
public record ListResponse<T>(List<T> items) {

  public static <T> ListResponse<T> of(List<T> items) {
    return new ListResponse<>(items);
  }
}
