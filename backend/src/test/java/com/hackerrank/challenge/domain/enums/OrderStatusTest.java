package com.hackerrank.challenge.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

  @ParameterizedTest(name = "{0} puede pasar a {1}")
  @CsvSource({
      "PENDING, CONFIRMED",
      "PENDING, CANCELLED",
      "CONFIRMED, SHIPPED",
      "CONFIRMED, CANCELLED",
      "SHIPPED, DELIVERED"
  })
  void admiteLasTransicionesDelCicloDeVida(OrderStatus from, OrderStatus to) {
    assertThat(from.canTransitionTo(to)).isTrue();
  }

  @ParameterizedTest(name = "{0} no puede pasar a {1}")
  @CsvSource({
      // Saltearse etapas del ciclo
      "PENDING, SHIPPED",
      "PENDING, DELIVERED",
      "CONFIRMED, DELIVERED",
      // Un envio en camino ya no se cancela
      "SHIPPED, CANCELLED",
      // Retrocesos
      "CONFIRMED, PENDING",
      "SHIPPED, CONFIRMED",
      "DELIVERED, SHIPPED",
      // Desde estados terminales no se sale
      "DELIVERED, CANCELLED",
      "DELIVERED, PENDING",
      "CANCELLED, PENDING",
      "CANCELLED, CONFIRMED",
      "CANCELLED, DELIVERED"
  })
  void rechazaLasTransicionesFueraDelCicloDeVida(OrderStatus from, OrderStatus to) {
    assertThat(from.canTransitionTo(to)).isFalse();
  }

  /**
   * Pedir el estado actual es una transicion invalida y no un no-op idempotente
   * (ver DECISIONS.md): por eso el endpoint responde 409.
   */
  @ParameterizedTest(name = "{0} no admite transicion hacia si mismo")
  @EnumSource(OrderStatus.class)
  void ningunEstadoAdmiteTransicionHaciaSiMismo(OrderStatus status) {
    assertThat(status.canTransitionTo(status)).isFalse();
  }

  @ParameterizedTest(name = "{0} rechaza un destino nulo")
  @EnumSource(OrderStatus.class)
  void rechazaUnDestinoNulo(OrderStatus status) {
    assertThat(status.canTransitionTo(null)).isFalse();
  }

  @Test
  @DisplayName("todo pedido nace pendiente")
  void todoPedidoNacePendiente() {
    assertThat(OrderStatus.initial()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  @DisplayName("entregado y cancelado son los unicos estados terminales")
  void entregadoYCanceladoSonLosUnicosEstadosTerminales() {
    assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
    assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
    assertThat(OrderStatus.SHIPPED.isTerminal()).isFalse();
  }
}
