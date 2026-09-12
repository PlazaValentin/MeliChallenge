package com.hackerrank.challenge.api.error;

import java.util.List;

/**
 * Cuerpo unico de error de la API.
 *
 * <p>
 * {@code errors} existe siempre, aunque venga vacio, para que el frontend no
 * tenga que contemplar dos formas distintas de error (ver DECISIONS.md). Se usa
 * para listar varios fallos de validacion en una sola respuesta; los errores
 * que
 * no son de validacion vienen con el array vacio y toda la informacion en
 * {@code description}.
 *
 * @param description texto breve apto para mostrarse en un pop-up. Nunca lleva
 *                    stack trace ni detalle tecnico: eso queda solo en el log.
 */
public record ErrorResponse(int statusCode, String description, List<FieldError> errors) {

  /** Un fallo puntual, referido al campo que lo provoco. */
  public record FieldError(String field, String message) {
  }

  public static ErrorResponse of(int statusCode, String description) {
    return new ErrorResponse(statusCode, description, List.of());
  }

  public static ErrorResponse of(int statusCode, String description, List<FieldError> errors) {
    return new ErrorResponse(statusCode, description, List.copyOf(errors));
  }
}
