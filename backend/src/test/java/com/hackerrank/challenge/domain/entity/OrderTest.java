package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.exception.BusinessRuleException;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

  @Nested
  @DisplayName("Construccion")
  class Construction {

    @Test
    @DisplayName("un pedido nace pendiente")
    void unPedidoNacePendiente() {
      assertThat(TestData.anOrder().build().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rechazaConstruirseSinId() {
      assertThatThrownBy(() -> new Order(null, UUID.randomUUID(), buyer(), lines(), TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaConstruirseSinVendedor() {
      assertThatThrownBy(() -> new Order(UUID.randomUUID(), null, buyer(), lines(), TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaConstruirseSinComprador() {
      assertThatThrownBy(
          () -> new Order(UUID.randomUUID(), UUID.randomUUID(), null, lines(), TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaConstruirseSinFecha() {
      assertThatThrownBy(
          () -> new Order(UUID.randomUUID(), UUID.randomUUID(), buyer(), lines(), null))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("un pedido sin lineas no existe")
    void unPedidoSinLineasNoExiste() {
      assertThatThrownBy(
          () -> new Order(UUID.randomUUID(), UUID.randomUUID(), buyer(), List.of(), TestData.NOW))
          .isInstanceOf(DomainValidationException.class);

      assertThatThrownBy(
          () -> new Order(UUID.randomUUID(), UUID.randomUUID(), buyer(), null, TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("una linea nula se rechaza como dato invalido")
    void unaLineaNulaSeRechazaComoDatoInvalido() {
      List<OrderLine> withNull = new ArrayList<>();
      withNull.add(TestData.line("1000.00", 1));
      withNull.add(null);

      assertThatThrownBy(() -> TestData.anOrder().withLines(withNull).build())
          .isInstanceOf(DomainValidationException.class);
    }

    /**
     * La validacion de lineas nulas recorre la lista en lugar de usar
     * contains(null), que sobre una lista inmutable lanza NPE en vez de
     * responder false. Con List.of -la forma natural de construir un pedido- el
     * constructor fallaba antes de validar nada, y el handler centralizado lo
     * reportaba como 500.
     */
    @Test
    @DisplayName("un pedido se construye con una lista inmutable de lineas")
    void unPedidoSeConstruyeConUnaListaInmutableDeLineas() {
      List<OrderLine> immutableLines = List.of(TestData.line("1000.00", 1));

      Order order = new Order(
          UUID.randomUUID(), UUID.randomUUID(), buyer(), immutableLines, TestData.NOW);

      assertThat(order.getLines()).hasSize(1);
    }

    @Test
    @DisplayName("las lineas del pedido no se pueden modificar desde afuera")
    void lasLineasDelPedidoNoSePuedenModificarDesdeAfuera() {
      Order order = TestData.anOrder().build();

      assertThatThrownBy(() -> order.getLines().add(TestData.line("1.00", 1)))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    private Buyer buyer() {
      return new Buyer("Comprador", "comprador@test.com");
    }

    private List<OrderLine> lines() {
      return List.of(TestData.line("1000.00", 1));
    }
  }

  @Nested
  @DisplayName("Total")
  class Total {

    @Test
    @DisplayName("el total es la suma de los subtotales de las lineas")
    void elTotalEsLaSumaDeLosSubtotalesDeLasLineas() {
      Order order = TestData.anOrder()
          .withLines(List.of(TestData.line("1500.50", 2), TestData.line("999.00", 3)))
          .build();

      assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5998.00"));
    }

    @Test
    @DisplayName("el total siempre tiene dos decimales")
    void elTotalSiempreTieneDosDecimales() {
      Order order = TestData.anOrder().withLines(List.of(TestData.line("1000.00", 1))).build();

      assertThat(order.getTotalAmount().scale()).isEqualTo(Money.SCALE);
    }
  }

  @Nested
  @DisplayName("Transiciones")
  class Transitions {

    @Test
    @DisplayName("un pedido avanza por el ciclo de vida")
    void unPedidoAvanzaPorElCicloDeVida() {
      Order order = TestData.anOrder().build();

      order.transitionTo(OrderStatus.CONFIRMED);
      order.transitionTo(OrderStatus.SHIPPED);
      order.transitionTo(OrderStatus.DELIVERED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("una transicion prohibida deja el estado intacto")
    void unaTransicionProhibidaDejaElEstadoIntacto() {
      Order order = TestData.anOrder().build();

      assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
          .isInstanceOf(BusinessRuleException.class);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("un pedido entregado ya no cambia de estado")
    void unPedidoEntregadoYaNoCambiaDeEstado() {
      Order order = TestData.anOrder().withStatus(OrderStatus.DELIVERED).build();

      assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
          .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("pedir el estado que el pedido ya tiene es una transicion invalida")
    void pedirElEstadoQueElPedidoYaTieneEsUnaTransicionInvalida() {
      Order order = TestData.anOrder().build();

      assertThatThrownBy(() -> order.transitionTo(OrderStatus.PENDING))
          .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rechazaUnEstadoDestinoNulo() {
      Order order = TestData.anOrder().build();

      assertThatThrownBy(() -> order.transitionTo(null))
          .isInstanceOf(DomainValidationException.class);
    }
  }

  @Nested
  @DisplayName("Pertenencia")
  class Ownership {

    @Test
    @DisplayName("el pedido reconoce a su vendedor y desconoce a los demas")
    void elPedidoReconoceASuVendedorYDesconoceALosDemas() {
      UUID sellerId = UUID.randomUUID();
      Order order = TestData.anOrder().withSellerId(sellerId).build();

      assertThat(order.belongsTo(sellerId)).isTrue();
      assertThat(order.belongsTo(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("el pedido reconoce los productos de sus lineas")
    void elPedidoReconoceLosProductosDeSusLineas() {
      OrderLine line = TestData.line("1000.00", 1);
      Order order = TestData.anOrder().withLines(List.of(line)).build();

      assertThat(order.containsProduct(line.getProductId())).isTrue();
      assertThat(order.containsProduct(UUID.randomUUID())).isFalse();
      assertThat(order.containsProduct(null)).isFalse();
    }
  }
}
