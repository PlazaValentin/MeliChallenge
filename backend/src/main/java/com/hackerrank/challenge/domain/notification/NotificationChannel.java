package com.hackerrank.challenge.domain.notification;

/**
 * Medio por el cual se avisa al vendedor. El contrato vive en el dominio y las
 * implementaciones en infraestructura, que es donde esta el detalle de como se
 * entrega cada mensaje.
 *
 * <p>
 * Agregar un canal es implementar esta interfaz: ni los canales existentes ni
 * quien los invoca necesitan cambiar.
 */
public interface NotificationChannel {

  /** Identifica al canal en los logs. */
  String name();

  /**
   * Entrega la notificacion. Puede fallar: quien invoca aisla el error para que
   * un canal caido no impida el envio por los demas.
   */
  void send(QuestionNotification notification);
}
