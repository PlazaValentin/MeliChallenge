package com.hackerrank.challenge.infrastructure.persistence.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Espejo 1:1 de la estructura de {@code seed/seed-data.json}. Son DTOs de
 * lectura
 * puros: no validan nada y no construyen entidades. Esa responsabilidad es de
 * {@link SeedLoader}, que pasa estos valores por los constructores del dominio
 * para que las invariantes se validen también en el seed (ver DECISIONS.md).
 */
public record SeedDataFile(
    List<SeedSeller> sellers,
    List<SeedProduct> products,
    List<SeedOrder> orders,
    List<SeedQuestion> questions) {

  public record SeedSeller(UUID id, String name, String email) {
  }

  public record SeedProduct(UUID id, UUID sellerId, String name, BigDecimal price) {
  }

  public record SeedOrder(
      UUID id,
      UUID sellerId,
      String buyerName,
      String buyerEmail,
      String status,
      long offsetDays,
      List<SeedOrderLine> lines) {
  }

  public record SeedOrderLine(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
  }

  public record SeedQuestion(
      UUID id,
      UUID orderId,
      UUID productId,
      String text,
      long offsetHours,
      String status,
      String answerText) {
  }
}
