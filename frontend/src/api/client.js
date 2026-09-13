// Una funcion por endpoint del contrato (ver DECISIONS.md, "Contrato de la API").
// Es la unica parte del frontend que sabe que la API vive en otro origen y que
// conoce la forma del cuerpo de error; el resto de la app trabaja con datos ya
// parseados o con una excepcion.

/** Puerto del frontend, el que declara vite.config.js. */
const FRONTEND_PORT = '3000'

/** Puerto del backend, el que declara server.port en application.properties. */
const BACKEND_PORT = '8080'

/**
 * URL del backend.
 *
 * Se deriva del host desde el que se abrio la pagina, reemplazando el puerto del
 * frontend por el del backend. No puede ser un valor fijo: el entorno de
 * evaluacion sirve el frontend detras de un proxy con un host generado por
 * sesion (vm-xxx-3000.hrcdn.net), donde `localhost` es la maquina del navegador
 * y no la que corre la API.
 *
 * VITE_API_BASE_URL funciona como override, para poder apuntar a un backend que
 * no este en el mismo host que el frontend.
 *
 * Se mantiene una URL absoluta en lugar de un proxy de Vite sobre rutas
 * relativas: el proxy haria que las llamadas salgan del mismo origen y el CORS
 * dejaria de intervenir, cuando es justamente parte del contrato declarado.
 */
function resolveBaseUrl() {
  const override = import.meta.env.VITE_API_BASE_URL
  if (override) return override

  const { protocol, hostname, host } = window.location

  // Desarrollo local: el host trae el puerto y alcanza con cambiarlo.
  if (host.includes(`:${FRONTEND_PORT}`)) {
    return `${protocol}//${host.replace(`:${FRONTEND_PORT}`, `:${BACKEND_PORT}`)}`
  }

  // Detras del proxy el puerto viaja en el nombre del host y no como puerto
  // real, asi que el reemplazo se hace sobre el hostname.
  return `${protocol}//${hostname.replace(FRONTEND_PORT, BACKEND_PORT)}`
}

const BASE_URL = resolveBaseUrl()

/**
 * Error de la API con el cuerpo unico que devuelve el handler centralizado.
 * Lleva `errors` siempre, aunque venga vacio, para que quien lo muestre no
 * tenga que contemplar dos formas distintas de error.
 */
export class ApiError extends Error {
  constructor(description, errors, statusCode) {
    super(description)
    this.description = description
    this.errors = errors
    this.statusCode = statusCode
  }
}

async function request(path, options) {
  let response
  try {
    response = await fetch(BASE_URL + path, options)
  } catch {
    // fetch solo rechaza si la request no llego a destino: backend caido, CORS
    // rechazado o red. No hay cuerpo de error que leer, asi que se arma uno.
    throw new ApiError(
      'No se pudo contactar al servidor. Verifica que el backend este levantado.',
      [],
      0,
    )
  }

  // No todo lo que responde en esta URL es la API: una pagina de error del
  // contenedor, un proxy en el medio o una ruta equivocada devuelven HTML o
  // cuerpo vacio. Si se asume JSON, el parseo explota con un error de sintaxis
  // que no dice nada del problema real y tapa el codigo de estado, que es el
  // unico dato util que quedo.
  const body = await parseJsonBody(response)

  if (!response.ok) {
    throw new ApiError(
      body?.description ?? describeStatus(response.status),
      body?.errors ?? [],
      response.status,
    )
  }
  return body
}

/**
 * Devuelve el cuerpo parseado, o null si viene vacio (204) o no es JSON.
 *
 * Se lee como texto y se parsea aparte: `response.json()` no distingue entre
 * "no hay cuerpo" y "el cuerpo no es JSON", y ambos casos terminan en la misma
 * excepcion de sintaxis.
 */
async function parseJsonBody(response) {
  let text
  try {
    text = await response.text()
  } catch {
    // La conexion se corto mientras llegaba el cuerpo. El codigo de estado ya
    // se conoce, asi que se sigue con el: vale mas que perderlo en un error de
    // lectura.
    return null
  }
  if (!text) return null

  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/**
 * Texto para mostrar cuando la respuesta fallo pero no trajo el cuerpo de error
 * de la API. Es generico a proposito: lo que sea que haya respondido no es la
 * aplicacion, asi que no se puede afirmar nada mas que el codigo de estado.
 */
function describeStatus(statusCode) {
  return `El servidor respondio ${statusCode} sin un cuerpo de error reconocible.`
}

function jsonBody(method, payload) {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

/**
 * Arma el query string del listado de pedidos. Los filtros vacios no viajan:
 * un parametro presente y vacio no es lo mismo que ausente, y el backend
 * distingue "sin filtro" de "filtro que no matchea nada".
 */
function orderFilterQuery(filters = {}) {
  const params = new URLSearchParams()
  // El estado se repite en el query string para pasar varios valores.
  for (const status of filters.statuses ?? []) {
    params.append('status', status)
  }
  if (filters.from) params.append('from', filters.from)
  if (filters.to) params.append('to', filters.to)
  if (filters.buyer?.trim()) params.append('buyer', filters.buyer.trim())

  const query = params.toString()
  return query ? `?${query}` : ''
}

export function listOrders(sellerId, filters) {
  return request(`/api/sellers/${sellerId}/orders${orderFilterQuery(filters)}`)
}

export function getOrderDetail(sellerId, orderId) {
  return request(`/api/sellers/${sellerId}/orders/${orderId}`)
}

export function changeOrderStatus(sellerId, orderId, status) {
  return request(
    `/api/sellers/${sellerId}/orders/${orderId}/status`,
    jsonBody('PATCH', { status }),
  )
}

export function createQuestion(orderId, questionText, productId) {
  return request(
    `/api/orders/${orderId}/questions`,
    // productId es opcional: null significa que la pregunta es sobre el pedido
    // en general y no sobre un item puntual.
    jsonBody('POST', { questionText, productId: productId ?? null }),
  )
}

export function answerQuestion(questionId, answerText) {
  return request(`/api/questions/${questionId}/answer`, jsonBody('POST', { answerText }))
}

export function resolveQuestion(questionId) {
  // Va sin cuerpo, y por eso tampoco lleva Content-Type: resolver no necesita
  // ningun dato mas alla de la pregunta sobre la que opera.
  return request(`/api/questions/${questionId}/resolve`, { method: 'PATCH' })
}

export function listUnresolvedQuestions(sellerId) {
  const query = sellerId ? `?sellerId=${sellerId}` : ''
  return request(`/api/ops/questions/unresolved${query}`)
}
