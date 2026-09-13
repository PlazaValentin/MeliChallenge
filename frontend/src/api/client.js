// Una funcion por endpoint del contrato (ver DECISIONS.md, "Contrato de la API").
// Es la unica parte del frontend que sabe que la API vive en otro origen y que
// conoce la forma del cuerpo de error; el resto de la app trabaja con datos ya
// parseados o con una excepcion.

// URL absoluta y no una ruta relativa con proxy de Vite: el backend habilita
// CORS para este origen a proposito (app.cors.allowed-origin), y un proxy
// enmascararia justamente lo que el CORS resuelve.
//
// Sale de una variable de entorno y no de una constante, por el mismo motivo
// por el que el origen permitido no esta hardcodeado del lado del backend: son
// las dos mitades del mismo acuerdo, y si una se puede mover sin recompilar, la
// otra tambien. Se declara en frontend/.env.
const BASE_URL = import.meta.env.VITE_API_BASE_URL

// Sin valor por defecto, con el mismo criterio que app.seed.enabled y
// app.cors.allowed-origin: la variable tiene que estar declarada para que
// contra donde apunta el frontend se lea en el .env y no en una linea de
// codigo. Falla al cargar el modulo y no en la primera llamada, para que una
// configuracion incompleta se note al arrancar y no recien cuando alguien
// aprieta un boton.
if (!BASE_URL) {
  throw new Error(
    'Falta la variable VITE_API_BASE_URL. Declarala en frontend/.env con la URL del backend.',
  )
}

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
