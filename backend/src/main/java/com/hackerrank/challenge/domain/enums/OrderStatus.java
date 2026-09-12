package com.hackerrank.challenge.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida del pedido.
 *
 * <pre>
 * PENDING -> CONFIRMED -> SHIPPED -> DELIVERED
 * PENDING -> CANCELLED
 * CONFIRMED -> CANCELLED
 * </pre>
 *
 * <p>
 * DELIVERED y CANCELLED son terminales. Ningun estado admite transicion hacia
 * si mismo: pedir el estado actual es una transicion invalida, no un no-op.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED));
        transitions.put(CONFIRMED, EnumSet.of(SHIPPED, CANCELLED));
        transitions.put(SHIPPED, EnumSet.of(DELIVERED));
        transitions.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /** Estado con el que nace todo pedido. */
    public static OrderStatus initial() {
        return PENDING;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
