package com.hackerrank.challenge.domain;

import java.time.ZoneId;

/**
 * Zona horaria del negocio, usada para cortar el día en los filtros de fecha
 * (ver DECISIONS.md, sección "Fechas"). Vive en la raíz de {@code domain} por
 * ser
 * un dato compartido entre capas (repositorio hoy, scoring más adelante), no
 * una
 * entidad ni una regla de una sola clase.
 */
public final class BusinessCalendar {

  public static final ZoneId ZONE_ID = ZoneId.of("America/Argentina/Buenos_Aires");

  private BusinessCalendar() {
  }
}
