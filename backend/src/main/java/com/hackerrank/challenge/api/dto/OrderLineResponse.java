package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.OrderLine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea del pedido. El subtotal viaja calculado desde el backend y no se deja
 * para el frontend, para no repartir logica de negocio entre las dos puntas
 * (ver DECISIONS.md).
 */
public record OrderLineResponse(
    UUID productId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal) {

  public static OrderLineResponse from(OrderLine line) {
    return new OrderLineResponse(
        line.getProductId(),
        line.getProductName(),
        line.getQuantity(),
        line.getUnitPrice(),
        line.getLineTotal());
  }
}
