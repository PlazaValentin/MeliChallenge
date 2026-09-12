package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Vendedor: dueno de un catalogo de productos y de los pedidos que recibe.
 *
 * <p>
 * El email es el destinatario de las notificaciones de preguntas prioritarias.
 */
public final class Seller {

    private final UUID id;
    private final String name;
    private final String email;

    public Seller(UUID id, String name, String email) {
        this.id = requireId(id);
        this.name = requireText(name, "El nombre del vendedor es obligatorio.");
        this.email = requireText(email, "El email del vendedor es obligatorio.");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new DomainValidationException("El id del vendedor es obligatorio.");
        }
        return id;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(message);
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Seller seller && id.equals(seller.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
