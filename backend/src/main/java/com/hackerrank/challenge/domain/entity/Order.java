package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.exception.BusinessRuleException;
import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido recibido por un vendedor.
 *
 * <p>
 * Nace siempre en {@link OrderStatus#PENDING} y solo cambia de estado a traves
 * de
 * {@link #transitionTo(OrderStatus)}, que valida la transicion contra el ciclo
 * de
 * vida. No hay setter de estado: no es posible construir ni dejar un pedido en
 * un
 * estado al que no se pueda llegar legitimamente.
 *
 * <p>
 * Las preguntas no se guardan aca: se referencian por {@code orderId} desde
 * {@link Question}. El total tampoco se persiste, se deriva de las lineas.
 */
public final class Order {

    private final UUID id;
    private final UUID sellerId;
    private final Buyer buyer;
    private final List<OrderLine> lines;
    private final Instant createdAt;
    private OrderStatus status;

    /**
     * @param createdAt fecha de la compra; se recibe desde afuera para que el
     *                  dataset
     *                  pre-cargado pueda representar pedidos de distinta
     *                  antiguedad.
     */
    public Order(UUID id, UUID sellerId, Buyer buyer, List<OrderLine> lines, Instant createdAt) {
        if (id == null) {
            throw new DomainValidationException("El id del pedido es obligatorio.");
        }
        if (sellerId == null) {
            throw new DomainValidationException("El vendedor del pedido es obligatorio.");
        }
        if (buyer == null) {
            throw new DomainValidationException("El comprador del pedido es obligatorio.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new DomainValidationException("El pedido debe tener al menos una linea.");
        }
        if (lines.contains(null)) {
            throw new DomainValidationException("El pedido no puede tener lineas vacias.");
        }
        if (createdAt == null) {
            throw new DomainValidationException("La fecha del pedido es obligatoria.");
        }
        this.id = id;
        this.sellerId = sellerId;
        this.buyer = buyer;
        this.lines = List.copyOf(lines);
        this.createdAt = createdAt;
        this.status = OrderStatus.initial();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public Buyer getBuyer() {
        return buyer;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** Suma de los subtotales de las lineas. Se calcula, no se guarda. */
    public BigDecimal getTotalAmount() {
        return lines.stream()
                .map(OrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Money.SCALE);
    }

    public boolean belongsTo(UUID candidateSellerId) {
        return sellerId.equals(candidateSellerId);
    }

    /**
     * Indica si el pedido incluye ese producto, para validar preguntas sobre un
     * item.
     */
    public boolean containsProduct(UUID productId) {
        return productId != null
                && lines.stream().anyMatch(line -> line.getProductId().equals(productId));
    }

    /**
     * Avanza el pedido al estado destino.
     *
     * <p>
     * Pedir el estado actual tambien es invalido: el ciclo de vida no define
     * transiciones hacia si mismo, asi que se rechaza en lugar de tratarse como
     * no-op.
     */
    public void transitionTo(OrderStatus target) {
        if (target == null) {
            throw new DomainValidationException("El estado destino del pedido es obligatorio.");
        }
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "No se puede pasar el pedido de " + status + " a " + target + ".");
        }
        this.status = target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Order order && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
