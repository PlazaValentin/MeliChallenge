package com.hackerrank.challenge.api.controller;

import com.hackerrank.challenge.application.service.OrderService;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Solo se ejercita la validacion del rango de fechas: es logica propia del
 * controller, porque involucra dos campos y ninguna anotacion puede expresar
 * que un valor sea coherente con otro. El resto de la validacion es Bean
 * Validation y testearla seria testear a Spring.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock
  private OrderService orderService;

  @InjectMocks
  private OrderController orderController;

  private final UUID sellerId = UUID.randomUUID();

  /**
   * Un rango al reves no es una busqueda sin resultados sino una consulta mal
   * armada: se corta antes de llegar al service.
   */
  @Test
  @DisplayName("un rango con el desde posterior al hasta es una consulta invalida")
  void unRangoConElDesdePosteriorAlHastaEsUnaConsultaInvalida() {
    LocalDate from = LocalDate.of(2026, 3, 10);
    LocalDate to = LocalDate.of(2026, 3, 1);

    assertThatThrownBy(() -> orderController.listOrders(sellerId, null, from, to, null))
        .isInstanceOf(DomainValidationException.class);

    verify(orderService, never()).listSellerOrders(any(), any());
  }

  @Test
  @DisplayName("un rango de un solo dia es valido")
  void unRangoDeUnSoloDiaEsValido() {
    LocalDate sameDay = LocalDate.of(2026, 3, 10);
    given(orderService.listSellerOrders(any(), any())).willReturn(List.of());

    assertThatCode(() -> orderController.listOrders(sellerId, null, sameDay, sameDay, null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("un rango abierto en cualquiera de sus extremos es valido")
  void unRangoAbiertoEnCualquieraDeSusExtremosEsValido() {
    LocalDate date = LocalDate.of(2026, 3, 10);
    given(orderService.listSellerOrders(any(), any())).willReturn(List.of());

    assertThatCode(() -> orderController.listOrders(sellerId, null, date, null, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> orderController.listOrders(sellerId, null, null, date, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> orderController.listOrders(sellerId, null, null, null, null))
        .doesNotThrowAnyException();
  }
}
