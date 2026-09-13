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
  a sumar. Diccionario definido e implementado en
  `app.scoring.keyword-points` (configurable, no exhaustivo): "urgente",
  "roto", "incompleto", "enojado", "estafa", "defectuoso", "pesimo",
  "indignado", "reclamo", "devolucion", 10 puntos cada una. Como los puntos
  se declaran por palabra y no como un valor único, asignarle más peso a una
  palabra ya es posible sin tocar código: hoy todas valen igual porque no
  hay criterio de negocio validado para diferenciarlas.
- **El techo del factor es explícito, no emergente del diccionario**
  (`app.scoring.keyword-max-points`, hoy 50): por más palabras distintas que
  traiga el texto, el factor no supera ese valor. Sin tope, las diez palabras
  por defecto sumarían 100 y las keywords pasarían a pesar más que el tiempo
  de espera (60), contradiciendo el orden de peso declarado más arriba. Se
  prefirió una propiedad a derivar el techo de la cantidad de palabras, para
  que ampliar el diccionario no corra el techo en silencio, y a una constante
  en el código, para no dejar el único techo del scoring fuera de
  configuración. La configuración se rechaza al arrancar si el techo no es
  positivo o si queda por debajo del puntaje de alguna palabra, caso en que el
  diccionario diría una cosa y el factor puntuaría otra ya con una sola
  coincidencia.
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
- **Los dos momentos usan el mismo reloj real.** El cero del factor tiempo al
  crear no se fuerza: sale de que la diferencia entre crear y puntuar es de
  microsegundos y cae en el primer tramo. Se evaluó fijar el reloj en el
  `createdAt` de la pregunta para que el cero fuera una identidad, y se
  descartó: convertiría el momento de creación en un caso especial del
  scoring, dejaría de reflejar un cambio de configuración en el primer tramo,
  y obligaría a explicar por qué ahí se usa un reloj distinto. El caso
  teórico de una espera negativa ya está contemplado en el propio cálculo.
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
- **El orden de declaración de las categorías de prioridad es significativo.**
  Van de menos a más grave y la prioridad del pedido se deriva tomando el máximo
  según ese orden natural, así que reordenarlas cambiaría el cálculo en silencio,
  sin romper la compilación ni fallar en ningún lado. Queda advertido en el
  propio enum.
- **Un pedido sin preguntas sin resolver no tiene prioridad**, y eso es distinto
  de tener prioridad baja: "sin preguntas" y "preguntas triviales" no son lo
  mismo, y colapsarlos le mentiría al vendedor. El campo viaja vacío.
- **El flag de preguntas pendientes y la prioridad no miran el mismo conjunto.**
  El flag es "respondiste / no respondiste" y solo mira las `OPEN`; la prioridad
  se calcula sobre las sin resolver (`OPEN` y `ANSWERED`), que son las que siguen
  en el radar de Operaciones. Un pedido con todas sus preguntas respondidas pero
  ninguna resuelta tiene el flag apagado y, aun así, prioridad presente.

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
- **Se notifica desde `HIGH` hacia arriba.** El umbral no es una decisión
  propia: el enunciado pide notificar cuando la pregunta se clasifica como
  "High Priority" o "Critical". Vive en configuración
  (`app.notifications.minimum-priority`) y no como constante, y la
  comparación se apoya en el orden natural de la clasificación, que ya está
  declarado como significativo.
- **Consecuencia de que el umbral sea `HIGH` y no `CRITICAL`:** al crear, el
  factor tiempo aporta cero, así que el techo real depende del estado del
  pedido. Con monto máximo y cinco palabras clave se llega a 115 sobre un
  pedido en curso y a 135 sobre uno `CANCELLED`: `CRITICAL` (130) solo es
  alcanzable al crear si el pedido está cancelado. Un umbral en `CRITICAL`
  habría dejado sin notificar toda pregunta grave sobre un pedido vigente,
  que es el caso más común.
- **El evento lleva la clasificación ya calculada, no el id solo.** El score
  no se persiste: si el listener lo recalculara obtendría otro valor, porque
  entre publicar y consumir pasó tiempo y el factor tiempo es sensible a eso.
  Se transporta la decisión tomada en el instante de creación, que es la que
  corresponde a ese momento.
- **Un flag por canal, y el canal existe solo si está encendido.** Cada canal
  declara su propia condición sobre `app.notifications.channels.<canal>.enabled`
  y se registra como bean únicamente si está activo; quien despacha recibe la
  lista de canales ya filtrada y la recorre sin preguntar por ninguno en
  particular. Así sumar Slack o SMS es publicar una clase nueva y una línea de
  configuración, sin tocar los canales existentes ni el código que los invoca.
  Se preferió esto a una lista única de canales activos (`active-channels=email`)
  porque esa alternativa obliga a quien despacha a filtrar por nombre, es decir
  a conocer la identidad de los canales.
- **La decisión de notificar y el despacho viven separados del listener.** El
  listener solo recibe el evento y delega; decidir y despachar es una pieza
  aparte, sin anotaciones de asincronía, que puede ejercitarse de forma
  directa. Es lo que hace testeable el punto siguiente.
- **Los avisos no llevan el texto de la pregunta ni datos del comprador.** Los
  canales loguean lo que envían, y los logs no registran información PII
  (coherente con lo definido para el manejo de errores). Viajan los ids, la
  clasificación y el score, que alcanzan para actuar.
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
- **Ids: UUID**, por escalabilidad y por no exponer un id secuencial, que
  filtra el volumen de pedidos del sistema.
- **CORS** habilitado para el origen del frontend. Los orígenes viajan en
  configuración (`app.cors.allowed-origin-patterns`) y no como constante en el
  código, con el mismo criterio que el resto de `app.*`: el frontend puede
  servirse desde otro puerto o máquina sin recompilar el backend. Se habilitan
  solo los métodos del contrato (`GET`, `POST`, `PATCH`); `OPTIONS` no se
  declara porque el preflight lo responde el propio soporte de CORS antes de
  llegar a un controller, y listarlo sugeriría que hay un endpoint que lo
  atiende. Tampoco se habilitan credenciales: no hay login ni cookies de sesión,
  y permitirlas sería abrir algo que la aplicación no usa.
- **La propiedad admite varios orígenes y acepta patrones, no valores
  literales.** El entorno de evaluación sirve el frontend detrás de un proxy
  cuyo host se genera por sesión (`vm-xxx-3000.hrcdn.net`), así que no hay un
  origen que se pueda escribir de antemano. Se declaran un patrón acotado al
  dominio del proxy y el `localhost` del desarrollo local; un comodín suelto
  habría resuelto lo mismo abriendo la API a cualquier origen, que es
  exactamente lo que CORS existe para impedir.
- **Las preguntas tienen su propio controller**, por segmentación de
  responsabilidades. No quedan anidadas bajo el vendedor: el listado de
  preguntas ya trae los datos del vendedor y el id del pedido por si se
  quiere ver el detalle, y del lado del vendedor las preguntas ya vienen
  embebidas en el `GET` del pedido. La creación sí cuelga del pedido
  (`POST /api/orders/{orderId}/questions`) porque una pregunta no existe
  sin un pedido al cual pertenecer; las acciones sobre una pregunta ya
  creada operan sobre su propio id.
- **`QuestionController` no declara `@RequestMapping` a nivel de clase.** Sus
  rutas cuelgan de dos bases distintas a propósito (una del pedido, las otras de
  la pregunta), así que cada método declara su ruta completa. Forzar un prefijo
  común obligaría a partir el controller en dos o a deformar alguna de las rutas
  para que encaje.
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
  detalle mínimo necesario para que el frontend lo muestre en un banner,
  sin exponer stack trace. Los logs tampoco registran información PII; el
  detalle técnico queda únicamente en el log.
- **UUID mal formado → `400`; UUID bien formado pero inexistente →
  `404`.** Aplica uniformemente a vendedor, pedido y pregunta.
- **`productId` que no pertenece al pedido → `400`**, no `404` ni `409`:
  responder "no existe" o "no corresponde" expondría qué productos tiene
  el catálogo del vendedor, información que aunque pueda ser pública no
  corresponde revelar en un contexto que no la pide.
- **Cuerpo único de error**, devuelto por el handler centralizado: código
  de estado HTTP, descripción breve apta para mostrar en un banner, y un
  array de errores que permite listar múltiples fallos de validación de
  entrada en una sola respuesta. **El array existe siempre**, aunque venga
  vacío, para que el frontend no tenga que contemplar dos formas distintas
  de error.
- **Las acciones de cambio de estado devuelven solo el id del recurso
  afectado**, tanto para el pedido como para la pregunta. Quien disparó la
  acción ya sabe qué estado pidió; si necesita el recurso actualizado, lo
  consulta.
- **El campo del id se llama `orderId` o `questionId`, no `id`.** Son dos
  respuestas distintas y no un tipo genérico reutilizado: el nombre explícito
  dice de qué recurso se trata sin depender del contexto de la llamada, y hace
  legible el log del frontend, donde un `id` suelto no se puede atribuir a nada.
  El costo es un record más, que es barato.
- **`PATCH /api/questions/{questionId}/resolve` va sin cuerpo.** Resolver no
  necesita ningún dato más allá de la pregunta sobre la que opera; un body vacío
  obligatorio sería ceremonia sin información.
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
- **El detalle del pedido se busca dentro del vendedor de la ruta.** Un pedido
  que existe pero pertenece a otro vendedor responde `404`, con el mismo mensaje
  que uno inexistente: distinguirlos confirmaría la existencia de un pedido
  ajeno. No es control de acceso (sigue sin haber login) sino coherencia del
  recurso: si el `sellerId` de la URL no se usara para resolver el pedido, la
  jerarquía de la ruta sería decorativa.
- **La validación de pertenencia aplica a TODOS los endpoints que cuelgan de la
  ruta del vendedor, no solo a los de lectura.** Si el `GET` de detalle la valida
  y el `PATCH` de estado no, quedan dos endpoints bajo la misma ruta con
  comportamientos distintos. En una escritura el problema es peor: no se trata de
  mostrar algo ajeno sino de modificarlo. Por eso `changeStatus` recibe el
  `sellerId` y resuelve el pedido dentro de ese vendedor, devolviendo el mismo
  `404` con el mismo mensaje que un pedido inexistente.
- **El ítem de la cola de Operaciones trae `sellerId` y `orderId`**, lo
  mínimo para poder ir a buscar el detalle del pedido desde el botón "ver
  detalle". Suma además el estado y el monto de ese pedido, que son dos de los
  factores del score, para no tener que abrir el detalle solo para entender por
  qué una pregunta puntuó como puntuó. El detalle del pedido y el chat vendedor-comprador son
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
- **La pregunta del detalle viaja con el nombre del producto ya resuelto**, no
  solo con su id. El frontend podría cruzarlo contra las líneas del pedido, pero
  eso sería que el cliente reconstruya una relación que el backend ya conoce, y
  dejaría de funcionar para cualquier otro consumidor de la API. El nombre sale
  de la línea del pedido y no del catálogo: la línea es el registro histórico de
  lo que se compró, así que si el producto se renombró después, la pregunta sigue
  hablando del nombre que el comprador vio.
- **Un producto referenciado que no está entre las líneas del pedido es un `500`.**
  La invariante del dominio ya garantiza que no puede pasar, así que si pasa es un
  defecto del sistema, no de quien consulta: mismo criterio que una pregunta que
  referencia un pedido inexistente. Devolver el nombre vacío escondería el dato
  corrupto para siempre. El mensaje del log lleva los ids de pregunta, producto y
  pedido para poder ubicar el caso, sin PII.
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
- **Tres tipos de excepción, uno por causa**: `DomainValidationException` (el
  dato de entrada está mal formado o viola una invariante) → `400`,
  `ResourceNotFoundException` (el id es válido pero no existe) → `404`, y
  `BusinessRuleException` (el recurso existe pero la operación no es admisible
  en su estado actual) → `409`. Las tres viven en `domain/exception`: son
  conceptos del dominio, y el mapeo a HTTP lo hace la capa web, no ellas.
- **Una violación de integridad no es un `404`.** Si una pregunta referencia un
  pedido inexistente, el problema es del sistema y no de quien consulta:
  responder `404` le atribuiría al cliente un error que no cometió. Se corta con
  un error no contemplado (`500`) en lugar de filtrar la fila en silencio, que
  dejaría a Operaciones viendo menos trabajo del que hay sin que nadie se entere.
- **Dinero serializado con dos decimales en un solo lugar.** `BigDecimal`
  conserva la escala con la que fue construido, así que un importe redondo
  saldría con un decimal según de dónde venga el valor. Se resuelve con un
  serializador global y no campo por campo, para que ningún importe nuevo pueda
  olvidarse de aplicarlo.

## Datos de arranque

- **El dataset vive en un JSON en `resources` y se carga al iniciar la app**,
  construyendo cada entidad a través de los constructores y factories del
  dominio. Las mismas invariantes que protegen a la API validan también el seed:
  un dataset que no podría existir vía API tampoco puede existir precargado.
- **La carga se activa por configuración** (`app.seed.enabled`), sin valor por
  defecto en el código: la propiedad tiene que estar declarada para que el seed
  corra. Se prefirió esto a un default implícito para que el comportamiento sea
  visible en `application.properties` y no haya que leer una anotación para
  saber si hay datos precargados.
- **Reconstruir una entidad existente es un camino distinto de crearla.** Las
  preguntas del seed tienen ids fijos y legibles, así que además de la factory
  de creación (que genera el id) hay una de reconstitución (que lo recibe),
  con las mismas validaciones. Se prefirió eso a forzar el id por reflexión, que
  saltea el control de la propia entidad, y a generar ids aleatorios, que haría
  imposible referenciar un caso puntual del dataset.
- **El dataset se diseña por cobertura, no por volumen.** Cada pregunta existe
  para aislar un factor distinto del scoring; si dos puntúan parecido por las
  mismas razones, una sobra. Cubre los cuatro tramos de espera y de monto, los
  cinco estados de pedido, los tres de pregunta y las cuatro categorías de
  prioridad, e incluye tanto un caso que supera el umbral de notificación como
  un pedido con varias preguntas sin resolver de scores contrastantes, para que
  se vea que la prioridad del pedido es el máximo y no la suma.
- **El dataset no ejercita el techo del factor de palabras clave, y se decidió
  dejarlo así.** Ninguna pregunta del seed supera las cinco palabras distintas
  (el máximo es justo 50, el propio techo), así que el tope nunca recorta en la
  demo y ningún score cambió al introducirlo. Forzar una sexta palabra
  distinta habría requerido una pregunta que acumule seis insultos sin
  repetirse, un texto que no se parece a una consulta real: el criterio del
  dataset es cobertura de casos de negocio, y un límite de configuración se
  verifica en los tests, que es donde no cuesta nada construir el caso
  extremo.
- **Tres compradores del dataset llevan acento** (`Lucía Fernández`,
  `Marcos Díaz`, `Carla Gómez`). La búsqueda de comprador normaliza acentos, y
  con todos los nombres escritos sin ellos esa regla no se podía mostrar: buscar
  "lucia" y que aparezca "Lucía" es lo que hace visible el comportamiento. A
  diferencia del techo de palabras clave, acá el caso no es artificial: un nombre
  acentuado es lo que cabe esperar del dominio, y era el dataset el que no lo
  reflejaba.

## Validación de la entrada

- **`spring-boot-starter-validation` es la única dependencia agregada al
  scaffold.** Se suma para poder declarar las restricciones de formato como
  anotaciones sobre los parámetros y los DTOs de entrada, en vez de repartir
  `if`s de validación por los controllers. Es la implementación estándar de
  Bean Validation y no arrastra nada propio del proyecto.
- **La validación de formato vive en la frontera y devuelve `400`.** Es lo
  primero que se evalúa, antes de llegar al service: lo que no tiene forma
  válida no entra al sistema. El handler centralizado traduce las violaciones
  al mismo cuerpo de error que el resto, con un elemento en `errors` por cada
  restricción incumplida, para que el frontend pueda marcar todos los campos
  que fallaron en una sola pasada en lugar de descubrirlos de a uno.
- **La conversión de tipos de Spring es la primera barrera de validación.**
  Un `UUID`, un `LocalDate` o un valor de enum mal formado falla al convertirse
  y nunca llega al cuerpo del método: esa conversión ya cubre buena parte de la
  validación de formato sin necesidad de anotación alguna. Por eso un estado
  inexistente en el filtro (`?status=EN_ADUANA`) es `400` y no `404`, y por eso
  los parámetros que solo necesitan tener el tipo correcto no llevan
  anotaciones: agregarlas sería redundante.
- **Las reglas que involucran más de un campo se validan explícitamente en el
  controller.** Bean Validation expresa restricciones sobre un valor, no
  relaciones entre valores: que `from` no sea posterior a `to` no es una
  propiedad de ninguno de los dos por separado, así que se verifica a mano y se
  lanza la excepción de validación del dominio. La alternativa (una anotación
  a nivel de clase con su propio validador) se descartó por desproporcionada
  para una única regla.
- **Cada anotación de validación declara su `message` en español.** Los
  mensajes por defecto de Hibernate Validator salen en inglés y dependen del
  `Locale` de la request, que el cliente controla: dejarlos implícitos haría que
  el idioma de la respuesta varíe según quién llame. Se prefirió el `message`
  explícito sobre un `ValidationMessages.properties` para que el texto quede a
  la vista junto a la restricción que lo produce.
- **Los topes de longitud de texto son defensivos, no reglas de negocio.**
  2000 caracteres para el texto de la pregunta y el de la respuesta, con el mismo
  criterio que los 120 del buscador de comprador: nadie escribe una consulta de
  esa extensión, pero el límite evita aceptar un payload absurdo. Cada DTO
  declara su propio tope junto a la restricción que lo aplica: que hoy coincidan
  no los vuelve el mismo límite, y compartir una constante habría acoplado dos
  validaciones que pueden evolucionar por separado.
- **Un cuerpo ilegible también es `400`.** Un body ausente, mal formado o con un
  valor que no se puede convertir al tipo esperado (por ejemplo un estado fuera
  del ciclo de vida) es el equivalente, para el cuerpo, de lo que la conversión
  de tipos es para los parámetros. Sin mapearlo explícitamente caería en la red
  de contención y saldría como `500`, atribuyéndole al sistema un error que es de
  la solicitud. Cuando se puede identificar el campo culpable viaja en `errors`;
  si el cuerpo directamente no parsea, el array queda vacío.

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
  desde la API y el frontend los traduce a etiquetas en español. Si llega un
  valor que el frontend no conoce, se muestra crudo en lugar de vacío: un enum
  nuevo en el backend tiene que notarse en pantalla, no desaparecer.
- **Sin router.** Son dos vistas con navegación lineal y el estado compartido
  son tres valores (vista actual, vendedor y pedido seleccionado), que viven en
  el componente raíz. Agregar `react-router` daría URLs compartibles y botón de
  atrás, que esta demo no necesita, a cambio de una dependencia y su API. Se
  asume el costo: no se puede compartir el link de un pedido.
- **Ninguna dependencia agregada al scaffold del frontend.** `fetch`, `useState`
  y `useEffect` cubren todo el alcance. Descartadas y por qué: `axios` (`fetch`
  alcanza y el manejo de errores se centraliza igual en un solo módulo), React
  Query o SWR (un polling con `setInterval` no justifica una capa de caché),
  Redux o Zustand (el estado compartido son tres valores), MUI, Bootstrap o
  Tailwind (CSS plano alcanza para tablas, chat y badges), y una librería de
  toasts (el banner de error es un `div` condicional).
- **El `sellerId` sale de una constante del frontend**, con los dos vendedores
  del seed. Es consecuencia directa de no tener autenticación: en un sistema
  real saldría del token de sesión. Por eso no se agregó un endpoint de
  vendedores, que sería resolver por otra vía algo que en realidad resuelve el
  login.
- **Cambiar de vista o de vendedor cierra el pedido abierto.** El detalle se
  resuelve dentro del vendedor de la ruta, así que un pedido de otro vendedor
  respondería `404`; conservarlo dejaría la pantalla apuntando a algo que no
  puede existir.
- **La URL del backend se deriva del host del navegador**, reemplazando el
  puerto del frontend por el del backend sobre `window.location`. No puede ser
  un valor fijo: el entorno de evaluación sirve el frontend detrás de un proxy
  con un host generado por sesión, donde `localhost` es la máquina del
  navegador y no la que corre la API. Una URL apuntando a `localhost:8080`
  funciona al probar con `curl` desde dentro del contenedor y falla en el
  navegador, que es exactamente el síntoma que se observó. Derivarla del host
  actual funciona en los dos contextos sin configuración por ambiente.
- **`VITE_API_BASE_URL` queda como override, no como valor obligatorio.** Sirve
  para el caso en que el backend no esté en el mismo host que el frontend, que
  no es el de esta entrega. Se prefirió esto a exigirla siempre, porque un valor
  obligatorio volvería a atar el frontend a un host conocido de antemano, que es
  justamente lo que no se puede asumir.
- **No se usa el proxy de Vite para evitar el problema.** Habría resuelto la
  conexión haciendo que las llamadas salgan del mismo origen, pero con eso el
  CORS dejaría de intervenir, y el CORS es parte del contrato declarado. La
  solución no puede consistir en desactivar lo que se está entregando.
- **Toda respuesta se parsea de forma tolerante.** El cuerpo se lee como texto y
  se intenta parsear aparte, porque `response.json()` no distingue "no hay
  cuerpo" de "el cuerpo no es JSON" y ambos casos terminan en la misma excepción
  de sintaxis. No todo lo que responde en esa URL es la aplicación: una página
  de error del contenedor o un proxy en el medio devuelven HTML. Cuando el
  cuerpo no es el de la API, el error igual se construye con el código de
  estado, que es el único dato confiable que queda.
- **Un fallo de red no es un error de la API.** Si la request no llega a destino
  (backend caído, red, o el navegador bloqueando por CORS), no hay cuerpo que
  leer: se construye un error con el mismo formato que el resto, para que la
  capa que lo muestra no tenga que distinguir de dónde vino.
- **Las fechas se formatean en la zona horaria del negocio**
  (`America/Argentina/Buenos_Aires`), la misma en la que el backend corta los
  días. Con la zona del navegador, un pedido podría mostrarse en un día distinto
  del que el filtro de fechas considera.
- **El error de la API se muestra en un banner dentro de la vista**, no en un
  pop-up modal. Un modal bloqueante obliga a cerrarlo antes de poder corregir el
  campo, mientras que el banner deja ver el error y el formulario al mismo
  tiempo. El banner se limpia al disparar el reintento: si no, el error viejo
  queda en pantalla mientras la nueva request está en vuelo.
- **Los tres estados vacíos son distintos y se declaran por separado:** un
  listado de pedidos sin resultados (los filtros no matchean), una cola de
  Operaciones sin filas (no hay preguntas sin resolver, que es una buena
  noticia) y un pedido sin preguntas. Este último es lo normal y no una
  anomalía: la sección del chat no se oculta, porque un bloque ausente no se
  distingue de uno que todavía carga o que falló.
- **El detalle del pedido hace su propia consulta**, en vez de recibir el pedido
  ya cargado desde la vista que lo abre. El backend devuelve líneas y preguntas
  juntas en un solo llamado, y que el componente se traiga lo que necesita evita
  que cada una de las dos vistas tenga que saber cómo se arma el detalle. Es lo
  que lo hace reutilizable entre vendedor y Operaciones sin condicionales.
- **Después de una acción se recarga el detalle desde el backend**, en lugar de
  actualizar el estado local con la respuesta. El score, la prioridad y el flag
  de preguntas pendientes se derivan al consultar y no se persisten, así que
  recalcularlos en el cliente sería duplicar la regla de negocio en la punta
  equivocada. Las acciones devuelven solo el id, justamente porque no son la
  fuente de verdad del recurso actualizado.
- **Volver del detalle recarga el listado.** La prioridad y el flag de preguntas
  pendientes se derivan al consultar, así que responder o resolver desde el
  detalle los deja viejos en el listado que quedó en memoria. Se recarga siempre
  al volver, y no solo cuando hubo una acción, para no tener que llevar la cuenta
  de si el detalle modificó algo: el costo es un llamado de más cuando el
  vendedor solo miró.
- **Las acciones del vendedor las dispara el detalle, no el chat.** `OrderDetail`
  hace la llamada, muestra el error en su banner y recarga; el chat recibe dos
  callbacks y queda de presentación. Es lo que lo mantiene reutilizable en
  Operaciones: si el chat importara el cliente de API, sabría de un endpoint que
  en esa vista nunca puede invocar, y el error tendría que subir igual al banner
  del detalle.
- **El texto de la respuesta y el "en vuelo" viven en cada turno**, no en un mapa
  por pregunta en el chat: son datos que no se usan fuera del turno al que
  pertenecen. Mientras la acción está en vuelo el botón se deshabilita, porque un
  doble click sobre "Responder" sería un `409` (una pregunta admite una sola
  respuesta) y le mostraría al vendedor un error que no cometió.
- **Las acciones se ofrecen según el estado de la pregunta y nunca las dos a la
  vez.** Responder y resolver son secuenciales (`OPEN → ANSWERED → RESOLVED`), así
  que ofrecer "resolver" sobre una pregunta abierta sería ofrecer una transición
  que el backend rechaza. Una pregunta ya resuelta no muestra ninguna.
- **El componente de la vista del vendedor se remonta al cambiar de vendedor.**
  Sin eso, quedaría mostrando los pedidos del vendedor anterior mientras llegan
  los nuevos, que es peor que no mostrar nada: el usuario no tiene forma de
  saber que lo que está leyendo ya no corresponde a lo que seleccionó.
- **La cola de Operaciones no ordena en el frontend.** El orden (score
  descendente y, ante empate, la más antigua primero) es una regla de negocio y
  se muestra tal como llega. Reordenarlo en el cliente duplicaría el criterio en
  la punta que no calcula el score.
- **El desglose del score es colapsable por fila y arranca cerrado.** La cola
  existe para priorizar, así que lo que se lee de un vistazo es el score y la
  prioridad; los cinco factores quedan a un click para cuando haga falta entender
  por qué una pregunta quedó arriba de otra. Se despliega en una fila propia a lo
  ancho y no como cinco columnas fijas, que junto con la pregunta y el pedido
  volverían ilegible la tabla.
- **El filtro por vendedor vive en la vista de Operaciones, no en el header.** El
  selector del header elige el vendedor cuyo trabajo se está mirando; el de
  Operaciones es un filtro de la cola, con la opción "Todos" que en la otra vista
  no existe. Unificarlos obligaría al header a saber en qué vista está para
  decidir qué opciones ofrecer, que es lógica de una vista viviendo afuera.
- **La lista de vendedores vive en `api/sellers.js`, no en la raíz.** Es un dato,
  del mismo tipo que las etiquetas, y las dos vistas lo necesitan: dejarlo en
  `App` obligaría a las vistas a importar de su propio padre, invirtiendo la
  dirección de la dependencia.
- **El pedido que abre Operaciones vive en su vista y no en la raíz.** No es el
  mismo dato que el del vendedor: allá el vendedor viene del header y solo se
  elige el pedido, acá el par (vendedor, pedido) sale de la fila. Compartir el
  estado haría que la raíz sostenga un valor con semántica distinta según la
  vista, y el vendedor del header entraría en conflicto con el de la fila.
- **Operaciones abre el detalle en modo lectura.** Es el mismo `OrderDetail` con
  el mismo chat: la diferencia no está en el componente sino en el permiso, porque
  solo el vendedor responde las dudas del comprador.
- **Volver del detalle también recarga la cola de Operaciones.** Ahí no hay acción
  posible que la invalide, pero la cola es global y puede haber otro operador —o
  el propio vendedor— trabajando sobre las mismas preguntas mientras se mira el
  detalle.
- **Los filtros del listado se aplican solos, sin botón de "Filtrar".** Estado y
  fechas son eventos discretos y disparan la consulta al cambiar. El texto del
  comprador va con un retardo de 300 ms: sin él, cada tecla dispara una request y
  las respuestas pueden llegar desordenadas, dejando en pantalla el resultado de
  una búsqueda vieja sobre lo que se escribió después. Sí hay botón de limpiar,
  que se deshabilita cuando no hay nada que limpiar.
- **El texto que se tipea y el que se busca son dos estados distintos.** El input
  refleja el primero sin esperar nada, y la consulta usa el segundo. Con un solo
  valor, el retardo se sentiría como una demora al escribir.
- **Los cinco estados son checkboxes, no un `select multiple`.** La multiselección
  nativa exige ctrl/cmd+click y no se descubre sola. Ninguno tildado significa
  "todos": es la ausencia del filtro, no un filtro que no matchea nada.
- **Las fechas viajan tal como las devuelve el input**, que ya usa `yyyy-MM-dd`,
  el formato que el backend espera. Convertirlas a `Date` en el medio solo
  agregaría un corrimiento de zona horaria sobre el día que el usuario eligió.
- **El rango invertido no se previene en el frontend.** El backend lo valida y
  devuelve `400`; el banner lo muestra como cualquier otro error. Adelantar la
  validación significaría mantener la misma regla en las dos puntas, y la
  respuesta del servidor sigue siendo la que manda.
- **Un filtro sin resultados dice que no hubo coincidencias, no que no hay
  pedidos.** Son dos vacíos distintos: sin filtros, el vendedor no tiene pedidos;
  con filtros, los tiene pero ninguno matchea. Un solo texto para ambos haría
  pensar que el listado está vacío cuando en realidad está acotado.
- **Los filtros son un componente aparte de la tabla.** Uno toma la entrada del
  usuario y la otra muestra el resultado; juntos darían un archivo donde el markup
  del formulario tapa el del listado.
- **El alta de pregunta se separa visualmente del chat con un borde.** No es un
  turno más de la conversación sino una simulación del comprador, y eso tiene que
  verse sin leer el rótulo: si se leyera como parte del hilo, parecería que el
  vendedor puede escribir en nombre del comprador.
- **El selector de producto solo ofrece las líneas del pedido.** Preguntar por un
  producto que no pertenece al pedido es un `400` del backend, así que no se
  ofrece: la UI no propone opciones que el sistema va a rechazar. La opción por
  defecto es "sobre el pedido en general", que es el caso en que no viaja
  `productId`.
- **El formulario se limpia aunque la creación falle.** El error queda en el
  banner; conservar el texto de un intento fallido invita a reenviar exactamente
  lo mismo, que va a fallar igual.
- **La cola de Operaciones se refresca sola cada 30 segundos, en silencio.** Es
  la única vista donde el dato se desactualiza sin que el usuario haga nada: la
  cola es global y otro operador —o el propio vendedor— puede estar resolviendo
  las mismas preguntas. El refresco automático no toca el estado de carga, porque
  parpadear un indicador cada 30 segundos sobre datos que siguen en pantalla
  anunciaría una actividad que nadie disparó. La vista del vendedor no lo lleva:
  ahí el usuario es el único que modifica lo que mira, y ya se recarga al volver
  del detalle.
- **Silencioso es el refresco automático, no toda recarga.** El corte no es
  "primera carga contra el resto" sino acción del usuario contra refresco de
  fondo: cambiar el filtro de vendedor y volver del detalle sí muestran el estado
  de carga y ocultan la tabla, porque dejar las filas del vendedor anterior junto
  al indicador no permite distinguir si el filtro no matcheó o la consulta sigue
  en vuelo. Es el mismo criterio por el que la vista del vendedor se remonta al
  cambiar de vendedor.
- **Un refresco que falla muestra el error pero no vacía la lista.** Lo que está
  en pantalla se consultó bien y sigue siendo un estado válido del sistema;
  borrarlo por un fallo de red dejaría a Operaciones sin la cola que ya tenía, que
  es peor que tenerla algo vieja. El banner avisa, y la marca de actualización
  queda clavada en el último refresco exitoso, que es justamente el dato que dice
  cuán vieja está.
- **La marca de actualización es la hora absoluta, no "hace X".** Un relativo
  obliga a un segundo intervalo que reescriba el texto entre refresco y refresco,
  porque si no dice "hace unos segundos" durante medio minuto. Se prefirió no
  sostener un timer más para algo que se lee igual de rápido: lo que importa es si
  el dato es de recién o quedó viejo por un fallo.
- **El polling se pausa mientras el detalle está abierto.** La cola no se ve, y
  al volver ya se recarga; mantenerlo activo serían requests cuyo resultado nadie
  mira. El intervalo se limpia tanto al desmontar como al abrir el detalle.
- **Los listados no muestran el id del pedido, y el detalle lo muestra completo.**
  Se probó truncar el UUID a ocho caracteres, y con el prefijo común del seed
  todas las filas se veían idénticas: una columna que no identifica nada ocupa
  lugar y ensucia la lectura. En el listado el pedido se reconoce por comprador y
  fecha, y en la cola de Operaciones por vendedor, estado y monto. El UUID vive en
  el detalle, sin truncar, que es donde sirve para copiarlo o usarlo en un `curl`.

## Testing

- **Ningún test levanta el contexto de Spring** (salvo el `contextLoads` que
  venía con el scaffold). Las reglas que importan —scoring, transiciones,
  invariantes, derivación de agregados, despacho de notificaciones— son lógica
  propia y se ejercitan instanciando las clases. Levantar el contexto para
  verificarlas agregaría segundos de arranque por clase sin verificar nada
  adicional.
- **Los tests arman sus propios datos, no usan el seed.** El seed existe para
  la demo; que un test dependa de él haría que cambiar el dataset rompa tests
  sin que haya cambiado ninguna regla. Se usan builders
  (`TestData.anOrder()`, `TestData.aQuestion(order)`) con valores por defecto
  válidos, donde cada test sobreescribe solo el campo que le importa.
- **El builder de pedidos llega a un estado recorriendo el ciclo de vida**, no
  forzando el campo. Que no se pueda construir un `Order` directamente en
  `SHIPPED` es una invariante, y saltearla desde el test la dejaría sin
  proteger justo donde se la verifica.
- **Reloj fijo donde el tiempo interviene.** El factor tiempo del scoring es
  sensible al instante de evaluación: con el reloj real, los tests de brechas
  serían flaky. Se usa un `Clock` fijo y se envejece la pregunta moviendo el
  reloj, no esperando.
- **Dobles con Mockito**, que ya viene en `spring-boot-starter-test`. Se
  prefirió a escribir fakes a mano: el objetivo de esos tests es aislar la
  pieza bajo prueba, no ejercitar una implementación alternativa del
  repositorio.
- **La configuración de scoring de los tests se declara, no se lee de
  `application.properties`.** Leerla ataría los tests al arranque de Spring y
  volvería ambiguo un fallo (¿cambió la regla o cambió la config?). La
  contrapartida asumida: si los valores de configuración cambian, hay que
  actualizar los tests que afirman puntajes concretos. Es deliberado, son la
  traducción ejecutable de lo acordado acá.
- **Los bordes de la clasificación se verifican con una configuración de un
  solo factor.** Con los puntajes reales, todos múltiplos de cinco, los valores
  de borde (49, 89, 129) no son alcanzables sumando factores; una config donde
  una única palabra clave vale lo que se le indique permite fijar un total
  exacto y verificar el umbral, que es lo que se quiere probar.
- **Los nombres de los tests describen el comportamiento esperado**, no el
  método que ejercitan: `unaPreguntaAbiertaNoPuedeResolverseSinSerRespondida`
  dice qué regla se rompe si falla; `testResolve` no.
- **La validación del rango de fechas se prueba instanciando el controller.**
  Es lógica propia (involucra dos campos, y ninguna anotación puede expresar
  que un valor sea coherente con otro), así que se ejercita directo con un
  doble del service, sin `MockMvc`. No se extrajo la regla a una clase aparte:
  es una sola condición y moverla solo para hacerla testeable habría cambiado
  código productivo sin necesidad.
- **Lo que deliberadamente no se testea:** las anotaciones de Bean Validation
  (es testear a Spring), el repositorio en memoria (es un `ConcurrentHashMap`
  con getters), los getters y constructores triviales, y las invariantes de
  `Product` y `Seller`, que son campos obligatorios equivalentes a los de
  `Buyer` y no agregan cobertura real sobre lo ya verificado.
- **Se verificó que los tests fallan cuando la regla cambia.** Para las dos
  afirmaciones centrales —que la prioridad del pedido es el máximo y no la
  suma, y que el desempate es por antigüedad— se mutó a propósito el código
  productivo (`max` por `min`, y quitando el criterio de desempate) y se
  confirmó que la suite los detecta. Sin esa comprobación, un test que pasa no
  distingue entre verificar y acompañar.
- **La salida de tests se configura en el build** (`testLogging` con eventos y
  un resumen final). Gradle por defecto solo informa si el build pasó o falló,
  y el detalle quedaba únicamente en el reporte HTML. Se prefirió configurarlo
  en `build.gradle` antes que dejar un script de utilidad suelto en el repo.

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
- ~~**Exposición del endpoint de creación de preguntas en el frontend**~~:
  **resuelto**. Se expone como un formulario al pie del chat, en el detalle del
  pedido, rotulado explícitamente como simulación del comprador. Es lo único
  que permite demostrar el disparo de notificaciones desde la interfaz; sin eso
  el objetivo solo se puede mostrar con `curl`. Queda en la vista del vendedor
  y no como un tercer rol en el selector, porque el comprador no es una entidad
  del modelo y darle una vista propia sugeriría lo contrario.
- **Reglas concretas de validación de entrada** (longitudes mínimas y
  máximas de textos, campos obligatorios): se definen al implementar cada
  flujo, no por anticipado.
