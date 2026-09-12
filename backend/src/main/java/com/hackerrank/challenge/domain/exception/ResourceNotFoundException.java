package com.hackerrank.challenge.domain.exception;

import java.util.UUID;

/**
 * El recurso pedido tiene un identificador sintacticamente valido pero no
 * existe
 * en el sistema.
 *
 * <p>
 * Se distingue de {@link DomainValidationException} (el dato de entrada esta
 * mal
 * formado) y de {@link BusinessRuleException} (el recurso existe pero la
 * operacion no es admisible en su estado actual).
 *
 * <p>
 * El handler centralizado de errores mapea esta excepcion a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  /**
   * Mensaje uniforme para los tres recursos consultables por id (vendedor,
   * pedido y pregunta), segun lo decidido en DECISIONS.md.
   */
  public static ResourceNotFoundException of(String resourceName, UUID id) {
    return new ResourceNotFoundException(
        "No se encontro " + resourceName + " con id " + id + ".");
  }
}
