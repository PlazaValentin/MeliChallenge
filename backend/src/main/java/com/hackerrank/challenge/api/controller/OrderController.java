package com.hackerrank.challenge.api.controller;

import com.hackerrank.challenge.api.dto.ListResponse;
import com.hackerrank.challenge.api.dto.OrderDetailResponse;
import com.hackerrank.challenge.api.dto.OrderSummaryResponse;
import com.hackerrank.challenge.application.input.OrderFilter;
import com.hackerrank.challenge.application.service.OrderService;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pedidos de un vendedor. El vendedor es parte de la ruta y no un filtro
 * incidental, por ser una entidad del modelo (ver DECISIONS.md).
 */
@RestController
@Validated
@RequestMapping("/api/sellers/{sellerId}/orders")
public class OrderController {

  /**
   * Tope del texto de busqueda de comprador. No es una regla de negocio sino un
   * limite defensivo: mas alla de un nombre y un email no hay nada que buscar, y
   * acotarlo evita recorrer todos los pedidos comparando contra una cadena
   * arbitrariamente larga.
   */
  private static final int MAX_BUYER_TEXT_LENGTH = 120;

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * Listado de pedidos del vendedor, con filtros combinables con AND.
   *
   * <p>
   * El estado se repite en el query string para pasar varios
   * ({@code ?status=PENDING&status=SHIPPED}); un valor que no pertenece al ciclo
   * de vida no llega hasta aca: Spring falla al convertirlo y el handler lo
   * traduce a 400. Un estado valido sin resultados devuelve 200 con lista vacia.
   *
   * <p>
   * Las fechas entran como {@code LocalDate} y se pasan tal cual: expandir el
   * rango a instantes en la zona horaria del negocio es responsabilidad del
   * service, no del borde HTTP.
   */
  @GetMapping
  public ListResponse<OrderSummaryResponse> listOrders(
      @PathVariable @NotNull UUID sellerId,
      @RequestParam(name = "status", required = false) Set<OrderStatus> statuses,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Size(max = MAX_BUYER_TEXT_LENGTH, message = "No puede superar los {max} caracteres.") String buyer) {

    requireValidRange(from, to);

    OrderFilter filter = new OrderFilter(normalize(statuses), from, to, buyer);
    List<OrderSummaryResponse> items = orderService.listSellerOrders(sellerId, filter).stream()
        .map(OrderSummaryResponse::from)
        .toList();

    return ListResponse.of(items);
  }

  /**
   * Detalle del pedido con sus lineas y preguntas. El pedido se busca dentro del
   * vendedor de la ruta: uno que pertenezca a otro vendedor responde 404.
   */
  @GetMapping("/{orderId}")
  public OrderDetailResponse getOrder(
      @PathVariable @NotNull UUID sellerId,
      @PathVariable @NotNull UUID orderId) {

    return OrderDetailResponse.from(orderService.findOrderDetail(sellerId, orderId));
  }

  /**
   * Se valida a mano porque involucra dos campos: ninguna anotacion de campo
   * puede expresar que un valor sea coherente con otro. Un rango al reves no es
   * una busqueda sin resultados, es una consulta mal armada.
   */
  private void requireValidRange(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new DomainValidationException(
          "La fecha desde no puede ser posterior a la fecha hasta.");
    }
  }

  /** Preserva el orden de llegada para que el filtro sea estable al depurar. */
  private Set<OrderStatus> normalize(Set<OrderStatus> statuses) {
    return statuses == null || statuses.isEmpty() ? null : new LinkedHashSet<>(statuses);
  }
}
