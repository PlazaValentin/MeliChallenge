package com.hackerrank.challenge.domain.exception;

/**
 * Violacion de una regla de negocio sobre un recurso que existe y cuyos datos
 * son sintacticamente correctos, pero cuya operacion no es admisible en el
 * estado actual del dominio (ej. una transicion de estado invalida).
 *
 * <p>El handler centralizado de errores mapea esta excepcion a HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
