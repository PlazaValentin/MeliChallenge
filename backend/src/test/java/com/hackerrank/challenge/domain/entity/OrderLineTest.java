package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLineTest {

  @Test
  @DisplayName("el subtotal es el precio unitario por la cantidad")
  void elSubtotalEsElPrecioUnitarioPorLaCantidad() {
    OrderLine line = line(new BigDecimal("1500.50"), 3);

    assertThat(line.getLineTotal()).isEqualByComparingTo(new BigDecimal("4501.50"));
  }

  @ParameterizedTest(name = "una cantidad de {0} no es valida")
  @ValueSource(ints = { 0, -1 })
  void rechazaUnaCantidadQueNoSeaPositiva(int quantity) {
    assertThatThrownBy(() -> line(new BigDecimal("1000.00"), quantity))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rechazaConstruirseSinProducto() {
    assertThatThrownBy(() -> new OrderLine(null, "Producto", 1, new BigDecimal("1000.00")))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("una linea sin nombre de producto no existe")
  void unaLineaSinNombreDeProductoNoExiste() {
    assertThatThrownBy(() -> new OrderLine(UUID.randomUUID(), null, 1, new BigDecimal("1000.00")))
        .isInstanceOf(DomainValidationException.class);

    assertThatThrownBy(() -> new OrderLine(UUID.randomUUID(), "  ", 1, new BigDecimal("1000.00")))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("un precio negativo no es dinero valido")
  void unPrecioNegativoNoEsDineroValido() {
    assertThatThrownBy(() -> line(new BigDecimal("-1.00"), 1))
        .isInstanceOf(DomainValidationException.class);
  }

  /**
   * El dominio exige la escala correcta en lugar de redondear en silencio: un
   * importe con mas decimales de los que el negocio maneja es un dato invalido.
   */
  @Test
  @DisplayName("un precio con mas de dos decimales se rechaza en vez de redondearse")
  void unPrecioConMasDeDosDecimalesSeRechazaEnVezDeRedondearse() {
    assertThatThrownBy(() -> line(new BigDecimal("1000.005"), 1))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rechazaConstruirseSinPrecio() {
    assertThatThrownBy(() -> line(null, 1)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("un precio con menos decimales se normaliza a la escala del dinero")
  void unPrecioConMenosDecimalesSeNormalizaALaEscalaDelDinero() {
    assertThat(line(new BigDecimal("1000"), 1).getUnitPrice().scale()).isEqualTo(Money.SCALE);
  }

  @Test
  @DisplayName("el nombre del producto se guarda sin espacios sobrantes")
  void elNombreDelProductoSeGuardaSinEspaciosSobrantes() {
    OrderLine line = new OrderLine(UUID.randomUUID(), "  Producto  ", 1, new BigDecimal("1.00"));

    assertThat(line.getProductName()).isEqualTo("Producto");
  }

  private static OrderLine line(BigDecimal unitPrice, int quantity) {
    return new OrderLine(UUID.randomUUID(), "Producto", quantity, unitPrice);
  }
}
