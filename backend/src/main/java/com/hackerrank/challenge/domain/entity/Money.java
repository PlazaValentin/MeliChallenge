package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;

/**
 * Reglas comunes de representacion monetaria: decimal con dos posiciones y sin
 * valores negativos.
 *
 * <p>
 * No se aplica redondeo: cantidad por precio unitario no genera decimales
 * nuevos
 * y la suma de subtotales tampoco, asi que se exige que el valor de entrada ya
 * tenga
 * la escala correcta en lugar de corregirlo silenciosamente.
 */
final class Money {

    static final int SCALE = 2;

    private Money() {
    }

    static BigDecimal require(BigDecimal value, String fieldDescription) {
        if (value == null) {
            throw new DomainValidationException(fieldDescription + " es obligatorio.");
        }
        if (value.signum() < 0) {
            throw new DomainValidationException(fieldDescription + " no puede ser negativo.");
        }
        if (value.scale() > SCALE) {
            throw new DomainValidationException(
                    fieldDescription + " no puede tener mas de " + SCALE + " decimales.");
        }
        return value.setScale(SCALE);
    }
}
