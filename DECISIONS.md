# DECISIONS.md

Este documento registra las decisiones de diseño tomadas para resolver la
ambigüedad declarada en el enunciado (estados y transiciones, fórmula de
importancia, y extensibilidad de notificaciones), junto con el trade-off
aceptado en cada una.

## Modelo

- **Buyer no es entidad.** Sus datos (nombre, email) viajan embebidos en
  `Order`. Trade-off aceptado: no hay identidad de comprador entre pedidos
  distintos; se compensa con un único campo de búsqueda que matchea contra
  nombre y email, de modo que el email permita distinguir compradores
  homónimos sin necesidad de modelar la entidad.
- **El total del pedido no se persiste**, se calcula desde las líneas.
  Evita inconsistencia entre el total guardado y el detalle de líneas.
- **Question referencia como máximo un Product, y ese producto debe
  pertenecer al Order** al que la pregunta está asociada. Es una
  validación de negocio explícita en la capa de dominio, no solo de
  formato.
- **Dinero:** representación decimal con 2 posiciones. Viaja así en los
  DTOs (no en unidades enteras tipo centavos). No se aplica redondeo:
  cantidad por precio unitario no genera decimales nuevos, y la suma de
  subtotales tampoco.
- **Una pregunta admite una sola respuesta.** Si el comprador necesita
  repreguntar, crea una pregunta nueva sobre el mismo pedido, con su
  propia fecha de creación. Cada pregunta es un turno, y la conversación
  emerge de la secuencia de preguntas del pedido ordenadas por fecha, sin
  necesidad de modelar una entidad `Message`. El frontend renderiza esa
  secuencia como chat. Trade-off aceptado: el vendedor no puede mandar dos
  mensajes seguidos sin una pregunta intermedia; un hilo real requeriría la
  entidad `Message` con autor y fecha, que queda fuera de alcance.
- **No existen `answeredAt` ni `resolvedAt`.** Como cada pregunta es un
  turno único, el tiempo que se pondera es siempre el `createdAt` de cada
  pregunta: si ya fue respondida, esa duda se considera atendida.
  Consecuencia asumida: un pedido puede tener varias preguntas sin
  resolver a la vez, y el flag del listado debe reflejar eso.
- **Se pueden crear preguntas sobre pedidos en estado terminal**
  (`DELIVERED` / `CANCELLED`). Que el pedido esté finalizado no significa
  que el comprador no necesite servicio post-venta; el sistema no tiene
  forma de saber cuándo termina el post-venta, así que cierra el pedido
  antes pero deja abierta la posibilidad de preguntar.

## Estados y transiciones

- **Pedido:** `PENDING → CONFIRMED → SHIPPED → DELIVERED`. Desde
  `PENDING` y `CONFIRMED` se puede pasar a `CANCELLED`. `CANCELLED` y
  `DELIVERED` son terminales. No hay devoluciones ni checkpoints de
  envío.
- **Pregunta:** `OPEN → ANSWERED → RESOLVED`, estrictamente secuencial:
  no se permite saltear de `OPEN` a `RESOLVED` directamente. Responder y
  resolver son dos acciones distintas del vendedor.
- **Validación de transición en el dominio:** una transición inválida es
  rechazada, no silenciada.
- **Pedir el estado que el recurso ya tiene es una transición inválida**
  (`PENDING → PENDING`) y devuelve `409`, coherente con el resto de las
  excepciones de negocio. No se trata como no-op idempotente: si el estado
  actual no admite ir hacia sí mismo, la solicitud es incorrecta y hay que
  informarlo.
- **Operaciones considera "sin resolver"** a las preguntas en `OPEN` y
  `ANSWERED`. Solo `RESOLVED` sale de su radar.
- **Por qué `ANSWERED` sigue siendo visible para Operaciones.** El flag
  del vendedor se apaga al responder, pero la pregunta permanece en la
  cola de Operaciones hasta que se resuelva. Es deliberado: Operaciones
  también gestiona compradores, y que el vendedor haya atendido todas las
  preguntas no significa que el comprador haya quedado conforme. Ese hueco
  entre "respondido" y "conforme" es justamente donde Operaciones puede
  necesitar mediar.
- **Observación sobre `CANCELLED`:** que `CANCELLED` pondere alto en el
  scoring de preguntas hizo dudar si no hace falta al menos un estado más
  que refleje que algo ocurrido dentro de las preguntas modificó el estado
  del envío. Se descarta por alcance: implicaría que las preguntas tengan
  efectos sobre el pedido, cosa que el enunciado no pide.

## Listado de pedidos

- Sin ventana temporal: se devuelven todos los pedidos del vendedor,
  ordenados por fecha descendente. Trade-off aceptado y declarado: no es
  el ideal a escala (ver "Detectado y no resuelto").
- Filtros combinables con AND: estado (acepta múltiples valores),
  rango de fechas, y búsqueda de comprador.
- **Búsqueda de comprador:** substring, case-insensitive, sin acentos,
  sobre nombre y email. Es un filtro por texto libre, no una búsqueda por
  identidad (coherente con que Buyer no es entidad).
  Se evaluó como alternativa devolver el listado de compradores actuales
  (nombre y email) para filtrar desde el frontend; se descartó porque, al
  no ser Buyer una entidad, esa lista habría que derivarla recorriendo
  todos los pedidos y deduplicando.
- **Filtro inválido de estado:** devuelve `400` cuando se consulta por un
  estado que el sistema no maneja (ej. `"en aduana"`): no forma parte del
  ciclo de vida definido y por lo tanto es un error de la consulta. En
  cambio, un estado válido sin resultados (ej. `CANCELLED` para un
  vendedor sin pedidos cancelados) devuelve `200` con lista vacía.
- **`from` posterior a `to`:** devuelve `400`, porque no pasa la
  validación de entrada.
- **El listado trae agregados por pedido**: costo total y categoría de
  prioridad derivada del score (no el score numérico), más un flag de
  preguntas pendientes. Los mismos agregados se exponen también en el
  detalle del pedido. Los umbrales de la categorización quedan a definir
  (ver sección de ambigüedades).
- **El flag de preguntas no es un contador.** No se expone la cantidad de
  preguntas: solo un flag que le indica al vendedor que debe abrir el
  modal para responder. No es un "leído/no leído", es un
  "respondiste / no respondiste", y no desaparece hasta que se responda.
- **La prioridad del pedido es el MÁXIMO de las clasificaciones de sus
  preguntas sin resolver, nunca la suma.** Cada pregunta tiene su propio
  score y se evalúa por separado; sumarlas haría que varias preguntas
  triviales hicieran parecer más crítico un pedido que una única pregunta
  grave. En la cola de Operaciones no hay agregación: cada pregunta es una
  fila con su score individual.
- **Los subtotales de línea los calcula y expone el backend**, no el
  frontend. Repartir lógica de negocio entre front y back complica el
  diagnóstico cuando aparece un error de funcionalidad.

## Fechas

- Rango de fechas **inclusivo en ambos extremos**.
- Internamente, el límite superior se expande al inicio del día
  siguiente y se compara con estrictamente-menor-que, en lugar de truncar
  la hora del campo filtrado. Se evita aplicar funciones sobre el campo
  indexado/filtrado.
- El corte de día se hace en la zona horaria del negocio
  (`America/Argentina/Buenos_Aires`), no en UTC. La evaluación del rango se
  hace en el horario del que consulta.
- El seed usa `Date.now()` más/menos offsets en días, para que el dataset
  no envejezca con el correr del tiempo real.

## Scoring de importancia (preguntas)

- **Factores que afectan el score, en orden de peso**: tiempo de espera,
  palabras clave de urgencia en el texto de la pregunta, monto total del
  pedido, estado del pedido, y estado de la pregunta (agregado como caso
  borde: el estado de la pregunta también pondera). El peso relativo de
  cada factor se refleja en su techo de puntos posible, no en un
  multiplicador aparte: a mayor peso, mayor techo.
- **Normalización por brechas (buckets), no valores absolutos**, tanto
  para tiempo como para monto, para evitar que una unidad continua (una
  hora, un peso) domine la suma solo por su magnitud. Valores definidos:
  - **Tiempo de espera** (techo 60): 0–24h → 0, 24–72h → 20, 72h–7d → 40,
    más de 7d → 60.
  - **Monto del pedido** (techo 40): menor a 50.000 → 0, 50.000–149.999,99
    → 15, 150.000–499.999,99 → 25, 500.000 o más → 40.
  - **Estado del pedido** (techo 30): `DELIVERED` → 0, `SHIPPED` → 5,
    `PENDING` → 10, `CONFIRMED` → 10, `CANCELLED` → 30.
  - **Estado de la pregunta** (techo 15): `RESOLVED` → 0, `ANSWERED` → 5,
    `OPEN` → 15.
- **`CANCELLED` pondera más que los estados en curso** (incluso más que
  `PENDING`/`CONFIRMED`), porque una pregunta sobre un pedido cancelado
  suele implicar devolución de dinero, un caso operacionalmente más
  sensible que un pedido que todavía se puede corregir.
- **Palabras clave** (techo 50 con el diccionario por defecto): se buscan
  solo sobre el texto de la pregunta, nunca sobre la respuesta del
  vendedor (para que el vendedor no pueda alterar el score de su propia
  pregunta al responder). 10 puntos por cada palabra **distinta**
  encontrada; una palabra repetida varias veces en el mismo texto no vuelve
  a sumar. Diccionario de ejemplo (configurable, pensado para ilustrar la
  idea, no exhaustivo): "urgente", "roto", "incompleto", "enojado",
  "estafa", "defectuoso", "pesimo", "indignado", "reclamo", "devolucion".
  Queda declarada como idea futura la posibilidad de agrupar palabras clave
  por peso distinto (ej. "estafa" más grave que "incompleto"), sin
  implementar ahora por no tener criterio de negocio validado.
- **Score máximo teórico: 195** (60+50+40+30+15). **Score máximo al crear
  la pregunta: 135** (sin el factor tiempo, que en ese momento aporta cero).
- **Umbrales de clasificación**, sobre el score total: `LOW` si el score es
  menor a 50; `MEDIUM` de 50 a 89; `HIGH` de 90 a 129; `CRITICAL` desde 130.
  Verificado que `HIGH` y `CRITICAL` son alcanzables al crear la pregunta
  (techo 135), sin depender del factor tiempo: por ejemplo, 5 palabras
  clave distintas (50) + monto alto (40) + pedido `CANCELLED` (30) +
  pregunta `OPEN` (15) = 135, `CRITICAL`.
- **Pesos y umbrales van en configuración** (`app.scoring.*` en
  `application.properties`, con binding tipado vía
  `@ConfigurationProperties`), no hardcodeados en el dominio. La clase que
  hace el binding vive en `infrastructure/config`, para que el dominio
  (`domain/rules/scoring`) no dependa de anotaciones de Spring.
- **Uso en dos momentos distintos:**
  - Al crear la pregunta: decide si se notifica. El factor tiempo aporta
    cero en este momento (recién creada, no hay espera que ponderar);
    pesan keyword, monto y estado del pedido (y estado de la pregunta).
  - Al consultar Operaciones: se recalcula al vuelo para ordenar, ahí sí
    con el factor tiempo activo.
- **Lo que se persiste es la pregunta con sus atributos, no el score ni
  la clasificación.** El score y la clasificación siempre se derivan al
  consultar. Esto es consciente: si el estado de la pregunta cambia
  (por ejemplo, se responde), el score cambia en la siguiente consulta
  porque el estado de la pregunta es uno de los factores ponderados.
- **Consecuencia asumida del tiempo medido desde `createdAt`:** como el
  reloj no se reinicia al responder, una pregunta respondida rápido sigue
  acumulando puntos de espera mientras no se resuelva. El estado
  `ANSWERED` baja su score por el factor de estado, pero el factor tiempo
  crece igual. Es deliberado: a Operaciones le importa que la pregunta
  siga sin resolverse, no que ya haya sido contestada.
- **Desempate:** ante score igual, prevalece la pregunta más antigua. Se
  reconoce que, al ponderar tiempo por brechas y no por valor absoluto,
  dos preguntas pueden caer en la misma brecha y por lo tanto empatar en
  score real, no solo en teoría.

## Notificaciones

- Al crear la pregunta se calcula su clasificación (ver scoring, "al
  crear") y se publica un evento de aplicación; el request HTTP responde
  sin esperar el resultado de la notificación.
- Un listener asincrónico evalúa si la clasificación amerita notificar y
  envía por todos los canales activos por configuración.
- Canales implementados: email, Slack, SMS. Solo email está activo.
  Agregar un canal nuevo no debe requerir modificar los canales
  existentes.
- El fallo de un canal no afecta a los demás; se registra en log.
- **Testing:** la lógica del listener (decide-si-notifica-y-por-qué-canal)
  y la publicación del evento se testean por separado, sin depender de la
  asincronía real, para evitar tests flaky.
- **Evolutivo, no implementado ahora:** un proceso periódico que
  reevalúe preguntas abiertas/respondidas, detecte cambios de
  clasificación (por paso del tiempo o cambio de estado) y notifique si
  corresponde, verificando que no se haya notificado antes por el mismo
  motivo. Se deja asentado como intención futura, no se implementa en
  esta entrega.
- **Endpoint de creación de preguntas:** existe en el backend
  específicamente para poder demostrar el disparo de notificaciones de
  punta a punta. Su exposición en el frontend queda abierta (ver
  ambigüedades).

## Contrato de la API

- **Vendedor como parte de la ruta** en los recursos de pedidos, por ser
  una entidad y no un filtro incidental.
- **Rutas definitivas**, agrupadas por controller:
  - `OrderController`
    - `GET   /api/sellers/{sellerId}/orders`
    - `GET   /api/sellers/{sellerId}/orders/{orderId}`
    - `PATCH /api/sellers/{sellerId}/orders/{orderId}/status`
  - `QuestionController`
    - `POST  /api/orders/{orderId}/questions`
    - `POST  /api/questions/{questionId}/answer`
    - `PATCH /api/questions/{questionId}/resolve`
  - `OpsController`
    - `GET   /api/ops/questions/unresolved`
- **Idioma:** el código y los valores de enum viajan en inglés; el
  frontend mapea esos valores a etiquetas en español para el usuario.
- **Ids: UUID**, por escalabilidad y por permitir truncarlos para
  mostrarlos en demos/UI sin exponer un id secuencial.
- **CORS** habilitado para el origen del frontend.
- **Las preguntas tienen su propio controller**, por segmentación de
  responsabilidades. No quedan anidadas bajo el vendedor: el listado de
  preguntas ya trae los datos del vendedor y el id del pedido por si se
  quiere ver el detalle, y del lado del vendedor las preguntas ya vienen
  embebidas en el `GET` del pedido. La creación sí cuelga del pedido
  (`POST /api/orders/{orderId}/questions`) porque una pregunta no existe
  sin un pedido al cual pertenecer; las acciones sobre una pregunta ya
  creada operan sobre su propio id.
- **Operaciones tiene su propio controller**, separado del de preguntas:
  es otra audiencia, con otro propósito y otra pantalla.
- **Verbos según el efecto real de la acción:** `POST` para responder,
  porque crea la respuesta; `PATCH` para resolver, porque solo cambia un
  estado.
- **Listados con objeto contenedor**, no array plano, porque agregar
  paginación está entre las prioridades y el envelope permite hacerlo sin
  romper el contrato.
- **Manejo de errores centralizado** en una única clase que captura todas
  las excepciones lanzadas por la aplicación y las mapea a su respuesta
  HTTP: validación de entrada → `400`, no encontrado → `404`, excepciones
  de negocio → `409`, no contempladas → `500`. Al cliente se le da el
  detalle mínimo necesario para que el frontend lo muestre en un pop-up,
  sin exponer stack trace. Los logs tampoco registran información PII; el
  detalle técnico queda únicamente en el log.
- **UUID mal formado → `400`; UUID bien formado pero inexistente →
  `404`.** Aplica uniformemente a vendedor, pedido y pregunta.
- **`productId` que no pertenece al pedido → `400`**, no `404` ni `409`:
  responder "no existe" o "no corresponde" expondría qué productos tiene
  el catálogo del vendedor, información que aunque pueda ser pública no
  corresponde revelar en un contexto que no la pide.
- **Cuerpo único de error**, devuelto por el handler centralizado: código
  de estado HTTP, descripción breve apta para mostrar en un pop-up, y un
  array de errores que permite listar múltiples fallos de validación de
  entrada en una sola respuesta. **El array existe siempre**, aunque venga
  vacío, para que el frontend no tenga que contemplar dos formas distintas
  de error.
- **Las acciones de cambio de estado devuelven solo el id del recurso
  afectado**, tanto para el pedido como para la pregunta. Quien disparó la
  acción ya sabe qué estado pidió; si necesita el recurso actualizado, lo
  consulta.
- **La creación de una pregunta no devuelve clasificación ni score.**
  Quien usa ese endpoint es el comprador (aunque no exista como entidad en
  esta demo) y no tiene sentido que sepa qué nivel de criticidad se le
  asignó a su propia pregunta. Además el score no se persiste: devolverlo
  ahí sería exponer un cálculo puntual como si fuera un atributo de la
  entidad.
- **La creación de pregunta responde `201` sin header `Location`**, porque
  no existe ni se planea un endpoint de detalle de pregunta al cual
  apuntar: las preguntas siempre se consultan en el contexto de su pedido
  o de la cola de Operaciones.
- **Clasificación única compartida** entre la vista de Operaciones y la
  del vendedor: mantener un solo set de valores facilita la comunicación y
  el entendimiento entre ambos equipos.
- **El ítem de la cola de Operaciones trae `sellerId` y `orderId`**, lo
  mínimo para poder ir a buscar el detalle del pedido desde el botón "ver
  detalle". El detalle del pedido y el chat vendedor-comprador son
  componentes aparte, reutilizados entre ambas vistas; la diferencia es que
  Operaciones no puede escribir en el chat: solo el vendedor responde las
  dudas del comprador. Un evolutivo posible sería que Operaciones también
  pueda hablar con el comprador, pero no en el mismo chat que usa el
  vendedor.
- **Sin restricción de acceso entre vendedores.** Para impedir que un
  vendedor consulte pedidos de otro haría falta login, que está fuera de
  alcance; queda explícitamente asumido que en esta demo esa barrera no
  existe.
- **Endpoint de Operaciones:** es global (todas las preguntas sin
  resolver, de todos los vendedores), con posibilidad de filtrar por
  vendedor. Es una pantalla distinta de la del vendedor, con audiencia y
  propósito distintos.
- **Desglose del score en la respuesta de Operaciones:** se devuelve el
  score total y su desglose por factor, para que Operaciones pueda
  interpretar y eventualmente ayudar a ajustar la fórmula.
- **En el listado de pedidos del vendedor no se devuelve el score
  numérico**, sino una categoría derivada del score (ej. score > 100 →
  "Crítica"), pensada para lectura rápida por el vendedor. Los umbrales
  concretos quedan a definir.
- **Preguntas embebidas en el detalle del pedido:** el pedido y sus
  preguntas se consultan juntos porque se usan juntos; separarlos
  obligaría al frontend a hacer dos llamadas por cada pedido consultado.
  Para Operaciones, en cambio, se listan directamente las preguntas sin
  resolver (no pedidos con preguntas embebidas), para no complejizar ni
  agregar latencia a un endpoint que tiene un propósito distinto.
- **Dinero en los DTOs:** viaja como decimal con 2 posiciones.

## Estructura del proyecto (backend)

- **`domain` contiene entidades, enums, reglas de negocio y excepciones**, y se
  organiza en subpaquetes por tipo de pieza:
  - `domain/entity` — entidades y objetos de valor del modelo (`Order`,
    `OrderLine`, `Question`, `Product`, `Seller`, `Buyer`).
  - `domain/enums` — enums del dominio (`OrderStatus`, `QuestionStatus`).
  - `domain/exception` — excepciones propias del dominio
    (`DomainValidationException`, `BusinessRuleException`).
- **Las reglas de transición viven dentro del propio enum de estado**, no en un
  paquete aparte: el ciclo de vida es parte de la definición del estado, y
  separarlo dejaría un enum anémico y la regla huérfana de su contexto. El lugar
  para reglas de negocio que no pertenezcan a una única entidad (por ejemplo el
  scoring) es un paquete propio, no el enum.
- **Las entidades protegen sus invariantes**: no hay setters de estado y la única
  vía de cambio son métodos de negocio que validan la transición, de modo que no
  sea posible construir ni dejar un recurso en un estado inválido desde afuera.
- Esta estructura se respeta de acá en adelante para las capas que se agreguen.
- **Reglas que no pertenecen a una única entidad viven en `domain/rules`**,
  organizadas por subpaquete temático (ej. `domain/rules/scoring` para el
  cálculo de importancia de preguntas). Es el lugar reservado desde la
  decisión anterior sobre dónde no poner las reglas de transición de
  estado.
- **Patrón repository:** el contrato (`OrderRepository`, `QuestionRepository`,
  `ProductRepository`, `SellerRepository`) vive en `domain/repository`; la
  implementación en memoria vive en `infrastructure/persistence`. Un repositorio
  por agregado (`Order` y `Question`); `Product` y `Seller` tienen el suyo solo
  para poder cargar el seed, sin más operaciones que `save` / `findById` /
  `findAll` porque el contrato de la API no expone catálogo ni alta de
  vendedores.
- **Los filtros del listado de pedidos viven en el repositorio**
  (`OrderSearchCriteria`, resuelto por `findBySeller`), no en el service: es lo
  que haría una base de datos real, y contiene el cambio si el día de mañana se
  migra a una consulta SQL.
- **`QuestionRepository` no filtra por vendedor.** `Question` solo conoce su
  `orderId`, no el vendedor del pedido. El filtro `sellerId` de la cola de
  Operaciones se resuelve orquestando en el service: se listan las preguntas sin
  resolver y se cruzan con `OrderRepository.findBySeller` cuando corresponda. Se
  prefirió esto a que un repositorio dependa de otro, para no acoplar la
  persistencia de preguntas a la de pedidos.
- **Persistencia in-memory con `ConcurrentHashMap`.** Alcanza para
  thread-safety en `save`/`findById`; los métodos de listado son lecturas sobre
  una vista de los valores en un momento dado, sin necesidad de bloqueo
  adicional para esta escala.
- **`application` contiene los casos de uso**, un servicio por agregado
  (`OrderService`, `QuestionService`), con `application/input` y
  `application/output` para lo que cruza el borde de la capa. Los servicios
  orquestan y no calculan: el filtrado lo resuelve el repositorio, la validación
  de transiciones vive en el enum de estado y el score lo provee
  `domain/rules/scoring`.
- **`api` contiene la capa web**, separada en `api/controller`, `api/dto` y
  `api/error`. Los controllers no exponen entidades de dominio ni los objetos de
  `application/output`: siempre mapean a un DTO propio, para que un cambio en el
  modelo no rompa el contrato publicado sin que nadie lo note.
- **Los listados usan un envoltorio genérico con `items`**, no el nombre del
  recurso: permite un único tipo reutilizable y que agregar metadata de
  paginación sea un cambio en un solo lugar, en línea con que la paginación está
  declarada como próxima prioridad.

## Frontend

- **Dos vistas separadas, con ejes de lectura distintos:**
  - **Vendedor: vertical.** Se centra en un pedido y baja al detalle, con
    sus líneas y todas sus preguntas.
  - **Operaciones: horizontal.** Cruza pedidos: todas las preguntas sin
    resolver de todos los vendedores, ordenadas por importancia.
- **El detalle del pedido y el chat vendedor-comprador son componentes
  reutilizados entre ambas vistas.** La diferencia no está en el
  componente sino en el permiso: Operaciones lo ve en modo lectura y no
  puede escribir. Solo el vendedor responde.
- **Sin login.** La distinción entre vistas se resuelve con un selector de
  rol. Queda declarado que simula los roles sin implementar autenticación
  ni control de acceso real.
- **Mapeo de labels en el frontend:** los valores de enum llegan en inglés
  desde la API y el frontend los traduce a etiquetas en español.

## Alcance excluido (declarado)

- Autenticación, multi-tenancy, paginación, devoluciones, hilo de
  mensajes dentro de una pregunta, reevaluación periódica del score
  (ver excepción evolutiva en Notificaciones).
- Creación de pedidos por endpoint: no se contempla: los pedidos nacen
  únicamente del seed.
- Categorización de vendedores por tiempo de respuesta u otras métricas
  operacionales: identificado como relevante para un análisis de escala,
  no se implementa.
- Eviction del store en memoria: no se implementa porque el store es el
  almacenamiento principal, no un cache. Un cache puede desalojar porque
  el dato original vive en otro lado; acá desalojar sería perder el dato.
  El primer límite real es la paginación, no la falta de eviction.
- Resiliencia de notificaciones (batching, retries con backoff y
  jitter, backpressure, DLQ): no se implementa, por ser infraestructura
  desproporcionada para una demo.
- Entidad `Message` con autor y fecha para modelar un hilo real de
  conversación: fuera de alcance (ver Modelo, "una pregunta, una
  respuesta").
- Autenticación y, por lo tanto, aislamiento de datos entre vendedores.

## Detectado y no resuelto (fuera de alcance, con propuesta a futuro)

- **Listado de pedidos sin paginación ni ventana temporal.** A escala,
  es el primer punto de quiebre (payload y render). En producción: 
  paginación resuelta contra la base de datos, con filtro de ventana
  temporal (últimas 24h, semana, mes) en vez de "traer todo".
- **Persistencia in-memory sin índices.** Los filtros (estado, fecha,
  comprador) hoy son de recorrido lineal. En producción: base de datos
  relacional con índices sobre los campos de filtro, y la búsqueda de
  comprador dejaría de ser un substring en memoria.
- **Búsqueda de comprador por substring sin entidad Buyer.** Funciona
  para la demo (nombre + email), pero no identifica compradores entre
  pedidos. En producción ameritaría modelar la entidad si el negocio
  necesita esa trazabilidad.
- **Clasificación automática de la conversación por IA.** Idealmente, un
  agente analizaría la conversación completa vendedor-comprador con
  criterios dados, clasificando tono, satisfacción y trato, además del
  texto de la pregunta aislada. No se implementa ahora.
- **Notificaciones sin reintentos ni cola.** Si el canal falla, se loguea
  y no se reintenta. En producción: colas con reintento, backpressure y
  dead-letter queue.
- **Reevaluación periódica de preguntas abiertas.** Ver sección de
  Notificaciones: es evolutivo, no implementado.
- **Hilo real de conversación.** Hoy la conversación emerge de la
  secuencia de preguntas del pedido, cada una con una única respuesta. En
  producción, si el negocio necesita que el vendedor escriba dos veces
  seguidas o que exista un hilo propiamente dicho, haría falta una entidad
  `Message` con autor y fecha.
- **Aislamiento entre vendedores.** Sin login, cualquiera que conozca un
  `sellerId` puede consultar sus pedidos. En producción: autenticación y
  autorización por vendedor sobre cada recurso.
- **Operaciones no participa del chat.** Hoy solo el vendedor responde.
  Un evolutivo sería habilitar un canal de Operaciones con el comprador,
  separado del chat del vendedor.

## Ambigüedades detectadas, a definir por el autor del proyecto

Estos puntos surgieron durante la conversación y quedaron mencionados
como "a definir" o "supongamos" sin un valor cerrado. Se listan aparte
para no fijarlos por cuenta propia:

- **Umbrales exactos de la categorización de prioridad** que se muestra
  en el listado de pedidos del vendedor (se mencionó "score > 100 =
  Crítica" solo como ejemplo, no como valor definitivo).
- **Valores numéricos concretos de las brechas de tiempo, monto y estado**
  usados en el scoring (los ejemplos dados —10/20/30/50, o 5/30 para
  estados— son ilustrativos de la idea, no la configuración final).
- **Diccionario final de palabras clave y si se agruparán por peso
  distinto** (se mencionó la idea de grupos con más peso, ej. "ofendido"
  vs. "falta", pero sin decisión tomada de implementarlo).
- **Exposición del endpoint de creación de preguntas en el frontend**: se
  definió que el endpoint existe en el backend para demostrar
  notificaciones, pero no si tendrá una pantalla/formulario en el
  frontend o quedará solo para pruebas directas contra la API.
- **Reglas concretas de validación de entrada** (longitudes mínimas y
  máximas de textos, campos obligatorios): se definen al implementar cada
  flujo, no por anticipado.
