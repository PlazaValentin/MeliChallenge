// Traduccion de los valores de enum y formato de montos y fechas.
//
// Los enums viajan en ingles desde la API y el frontend los mapea a etiquetas
// en espanol para el usuario (ver DECISIONS.md, "Contrato de la API"). El mapeo
// vive en un solo lugar para que una etiqueta no diga dos cosas distintas en
// dos pantallas.

export const ORDER_STATUS_LABELS = {
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmado',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
}

export const QUESTION_STATUS_LABELS = {
  OPEN: 'Sin responder',
  ANSWERED: 'Respondida',
  RESOLVED: 'Resuelta',
}

export const PRIORITY_LABELS = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  CRITICAL: 'Critica',
}

// Nombre de cada factor del desglose del score, para que Operaciones pueda leer
// por que una pregunta quedo arriba de otra sin consultar el DECISIONS.md.
export const SCORE_FACTOR_LABELS = {
  waitingTimePoints: 'Tiempo de espera',
  keywordPoints: 'Palabras clave',
  orderAmountPoints: 'Monto del pedido',
  orderStatusPoints: 'Estado del pedido',
  questionStatusPoints: 'Estado de la pregunta',
}

// Si llega un valor que el frontend no conoce se muestra crudo en vez de vacio:
// un enum nuevo en el backend tiene que notarse en pantalla, no desaparecer.
function translate(dictionary, value) {
  return dictionary[value] ?? value
}

export const orderStatusLabel = (value) => translate(ORDER_STATUS_LABELS, value)
export const questionStatusLabel = (value) => translate(QUESTION_STATUS_LABELS, value)
export const priorityLabel = (value) => translate(PRIORITY_LABELS, value)

// Mismo locale y zona horaria del negocio que usa el backend para cortar los
// dias: con la zona del navegador, un pedido podria mostrarse un dia distinto
// del que el filtro de fechas considera.
const LOCALE = 'es-AR'
const TIME_ZONE = 'America/Argentina/Buenos_Aires'

const moneyFormat = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: TIME_ZONE,
})

const dateFormat = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: 'short',
  timeZone: TIME_ZONE,
})

export function formatMoney(amount) {
  return moneyFormat.format(amount)
}

/** Fecha y hora, para las preguntas del chat, donde el momento importa. */
export function formatDateTime(isoInstant) {
  return dateTimeFormat.format(new Date(isoInstant))
}

/** Solo la fecha, para el listado de pedidos, donde la hora es ruido. */
export function formatDate(isoInstant) {
  return dateFormat.format(new Date(isoInstant))
}
