package com.hackerrank.challenge.infrastructure.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ajustes de serializacion JSON comunes a toda la API.
 */
@Configuration
public class JacksonConfig {

  /**
   * Decimales con los que viaja el dinero en los DTOs. Coincide con la escala que
   * valida el dominio, pero se declara aparte porque aquella es una invariante
   * del
   * modelo y esta es una regla de presentacion: son la misma cifra por decision,
   * no
   * por dependencia.
   */
  private static final int MONEY_SCALE = 2;

  /**
   * Fuerza a que todo importe salga con dos decimales, como exige el contrato.
   *
   * <p>
   * {@link BigDecimal} conserva la escala con la que fue construido, asi que sin
   * esto un monto redondo saldria como {@code 48990.0} segun de donde venga el
   * valor. Se resuelve una sola vez en la serializacion y no monto por monto,
   * para
   * que ningun campo nuevo pueda olvidarse de aplicarlo.
   *
   * <p>
   * El redondeo es defensivo: los montos ya llegan con la escala correcta
   * validada
   * por el dominio, y un total solo suma o multiplica sin introducir decimales
   * nuevos.
   */
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer moneyScaleCustomizer() {
    return builder -> builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
      @Override
      public void serialize(
          BigDecimal value, JsonGenerator generator, SerializerProvider provider)
          throws IOException {
        generator.writeNumber(value.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
      }
    });
  }
}
