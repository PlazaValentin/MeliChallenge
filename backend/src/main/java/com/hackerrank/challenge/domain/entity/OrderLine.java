package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea de un pedido: un producto, su cantidad y el precio unitario al momento
 * de
 * la compra.
 *
 * <p>
 * El nombre del producto se copia junto al precio: la linea es un registro
 * historico de lo que se compro, no una vista del catalogo actual. Si el
 * vendedor
 * renombra o repreciar el producto, los pedidos ya emitidos no cambian.
 */
public final class OrderLine {

    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderLine(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
        if (productId == null) {
            throw new DomainValidationException("El producto de la linea es obligatorio.");
        }
        if (productName == null || productName.isBlank()) {
            throw new DomainValidationException("El nombre del producto de la linea es obligatorio.");
        }
        if (quantity <= 0) {
            throw new DomainValidationException("La cantidad de la linea debe ser mayor a cero.");
        }
        this.productId = productId;
        this.productName = productName.trim();
        this.quantity = quantity;
        this.unitPrice = Money.require(unitPrice, "El precio unitario de la linea");
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** Precio unitario por cantidad. No introduce decimales nuevos. */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
