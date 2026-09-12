package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;

/**
 * Datos del comprador embebidos en el pedido.
 *
 * <p>Deliberadamente no es una entidad: no tiene id ni identidad entre pedidos. El
 * email se conserva para poder distinguir compradores homonimos en la busqueda por
 * texto libre del listado.
 */
public record Buyer(String name, String email) {

    public Buyer {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("El nombre del comprador es obligatorio.");
        }
        if (email == null || email.isBlank()) {
            throw new DomainValidationException("El email del comprador es obligatorio.");
        }
        name = name.trim();
        email = email.trim();
    }
}
