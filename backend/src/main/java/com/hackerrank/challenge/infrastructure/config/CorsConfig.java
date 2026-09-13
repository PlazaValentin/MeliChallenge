package com.hackerrank.challenge.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS para el origen del frontend (ver DECISIONS.md, "Contrato de la API").
 *
 * <p>
 * Los origenes viajan por configuracion y no como constantes: el frontend puede
 * servirse desde otro puerto, otra maquina o detras de un proxy sin recompilar
 * el backend. La propiedad no declara valor por defecto, con el mismo criterio
 * que {@code app.seed.enabled}: tiene que estar en
 * {@code application.properties} para que el comportamiento sea visible ahi y
 * no haya que leer una anotacion para saber quien puede llamar a la API.
 *
 * <p>
 * Vive en {@code infrastructure/config} junto al resto de la configuracion de
 * Spring, para no meter anotaciones del framework en el dominio.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final List<String> allowedOriginPatterns;

  public CorsConfig(
      @Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  /**
   * Se habilitan solo los metodos que el contrato usa. El {@code OPTIONS} del
   * preflight no se declara: lo responde el propio soporte de CORS de Spring
   * antes de llegar a un controller.
   *
   * <p>
   * Se declaran patrones y no origenes literales porque el entorno de evaluacion
   * sirve el frontend detras de un proxy cuyo host se genera por sesion y no se
   * puede anticipar en un archivo de configuracion. Un patron acotado cubre ese
   * caso sin abrir la API a cualquier origen, que es lo que pasaria con un
   * comodin suelto.
   *
   * <p>
   * Sin {@code allowCredentials}: no hay login ni cookies de sesion, asi que
   * habilitarlas seria abrir algo que la aplicacion no usa.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
        // Content-Type alcanza: es la unica cabecera que el frontend agrega, al
        // mandar el cuerpo JSON de responder, resolver y crear pregunta.
        .allowedHeaders("Content-Type")
        .allowedMethods("GET", "POST", "PATCH");
  }
}
