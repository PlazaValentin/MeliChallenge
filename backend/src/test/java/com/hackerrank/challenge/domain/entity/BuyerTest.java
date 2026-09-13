package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyerTest {

  @ParameterizedTest(name = "un comprador sin nombre valido no existe: [{0}]")
  @NullSource
  @ValueSource(strings = {"", "   "})
  void unCompradorSinNombreValidoNoExiste(String name) {
    assertThatThrownBy(() -> new Buyer(name, "comprador@test.com"))
        .isInstanceOf(DomainValidationException.class);
  }

  /**
   * El email es obligatorio aunque Buyer no sea una entidad: es lo que permite
   * distinguir compradores homonimos en la busqueda por texto libre.
   */
  @ParameterizedTest(name = "un comprador sin email valido no existe: [{0}]")
  @NullSource
  @ValueSource(strings = {"", "   "})
  void unCompradorSinEmailValidoNoExiste(String email) {
    assertThatThrownBy(() -> new Buyer("Comprador", email))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("los datos del comprador se guardan sin espacios sobrantes")
  void losDatosDelCompradorSeGuardanSinEspaciosSobrantes() {
    Buyer buyer = new Buyer("  Ana Perez  ", "  ana@test.com  ");

    assertThat(buyer.name()).isEqualTo("Ana Perez");
    assertThat(buyer.email()).isEqualTo("ana@test.com");
  }
}
