package com.hackerrank.challenge.domain.exception;

/**
 * Dato invalido detectado al construir o modificar una entidad: campo obligatorio
 * ausente, valor fuera de rango, o referencia que no corresponde al agregado.
 *
 * <p>El handler centralizado de errores mapea esta excepcion a HTTP 400.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
