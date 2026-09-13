package com.hackerrank.challenge.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el origen del frontend (ver DECISIONS.md, "Contrato de la API").
 *
 * <p>
 * El origen viaja por configuracion y no como constante: el frontend puede
 * correr en otro puerto o en otra maquina sin recompilar el backend. La
 * propiedad no declara valor por defecto, con el mismo criterio que
 * {@code app.seed.enabled}: tiene que estar en {@code application.properties}
 * para que el comportamiento sea visible ahi y no haya que leer una anotacion
 * para saber quien puede llamar a la API.
 *
 * <p>
 * Vive en {@code infrastructure/config} junto al resto de la configuracion de
 * Spring, para no meter anotaciones del framework en el dominio.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final String allowedOrigin;

  public CorsConfig(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
    this.allowedOrigin = allowedOrigin;
  }

  /**
   * Se habilitan solo los metodos que el contrato usa. El {@code OPTIONS} del
   * preflight no se declara: lo responde el propio soporte de CORS de Spring
   * antes de llegar a un controller.
   *
   * <p>
   * Sin {@code allowCredentials}: no hay login ni cookies de sesion, asi que
   * habilitarlas seria abrir algo que la aplicacion no usa.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigin)
        // Content-Type alcanza: es la unica cabecera que el frontend agrega, al
        // mandar el cuerpo JSON de responder, resolver y crear pregunta.
        .allowedHeaders("Content-Type")
        .allowedMethods("GET", "POST", "PATCH");
  }
}
