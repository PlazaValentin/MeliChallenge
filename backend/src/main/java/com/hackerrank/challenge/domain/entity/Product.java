package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Producto del catalogo de un vendedor.
 *
 * <p>
 * El precio de catalogo es el precio vigente hoy; no es el que se factura. Al
 * comprar, la linea del pedido copia el precio del momento, de modo que un
 * cambio
 * de catalogo posterior no altera pedidos ya realizados.
 */
public final class Product {

    private final UUID id;
    private final UUID sellerId;
    private final String name;
    private final BigDecimal price;

    public Product(UUID id, UUID sellerId, String name, BigDecimal price) {
        this.id = requireId(id, "El id del producto es obligatorio.");
        this.sellerId = requireId(sellerId, "El vendedor del producto es obligatorio.");
        this.name = requireName(name);
        this.price = Money.require(price, "El precio del producto");
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean belongsTo(UUID candidateSellerId) {
        return sellerId.equals(candidateSellerId);
    }

    private static UUID requireId(UUID value, String message) {
        if (value == null) {
            throw new DomainValidationException(message);
        }
        return value;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("El nombre del producto es obligatorio.");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Product product && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
