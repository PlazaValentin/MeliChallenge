# Plan del anexo de OpenTelemetry

Ocho bloques de implementación, con las decisiones abiertas y sus trade-offs.
Ninguna decisión está tomada: se cierran una por una y se anotan en la tabla del final.

**El orden importa:** no se toca la app hasta que la infraestructura levante.

## Cómo se evalúa

| Dimensión | Peso |
|---|---|
| Instrumentación y trazas | 30% |
| Métricas | 20% |
| Pipeline con Collector | 20% |
| Reproducibilidad | 15% |
| Criterio de ingeniería | 15% |

El 15% de reproducibilidad es el más barato de ganar: que alguien clone y haga `docker compose up` y funcione.

## ✔ Verificado en el código

Leído el proyecto, no hace falta volver a chequear esto:

- **`@EnableAsync` está presente**, en `infrastructure/config/NotificationBeanConfig`. La asincronía del listener de notificaciones es real y corre en otro hilo: el riesgo de span huérfano existe de verdad, no es una falsa alarma.
- **No hay Actuator ni Micrometer.** `build.gradle` declara exactamente tres dependencias: `spring-boot-starter-web`, `spring-boot-starter-validation` y `spring-boot-starter-test`. No hay ninguna fuente de métricas hoy, así que **de dónde salen las métricas HTTP automáticas depende enteramente de la Decisión 1** (ver bloque 6).
- **No hay llamadas HTTP salientes.** Los tres canales de notificación (email, Slack, SMS) escriben a log. El ítem del enunciado sobre propagación W3C saliente —"si la API realiza llamadas salientes"— no aplica. El listener `@Async` es el caso análogo que sí existe, y el enunciado menciona los jobs asíncronos como tema de la defensa.
- **No hay Gradle wrapper.** `./gradlew` no existe; hay que invocar `gradle` directamente desde `backend/`. Pesa sobre la Decisión 7.
- **El `ApiExceptionHandler` captura todas las excepciones**, incluida una red de contención `@ExceptionHandler(Exception.class)`. Ninguna excepción escapa del `DispatcherServlet`. Pesa fuerte sobre el bloque 5.
- **No hay ningún 500 reproducible desde afuera.** Los tres `IllegalStateException` del código (pregunta huérfana en `QuestionService`, producto ausente en `QuestionResponse`, tramo no cubierto en `QuestionImportanceScorer`) son violaciones de integridad o de configuración que las invariantes del dominio impiden. Pesa sobre los bloques 5 y 7.

---

# Bloque 0 — Decisiones de arranque

Sin código. Lo que se defina acá condiciona todo lo demás.

## ◆ Decisión 1: ¿Cómo se instrumenta la aplicación?

**A · Javaagent**
- ✓ Cero código para la instrumentación automática. Semantic conventions correctas garantizadas. Propaga contexto entre hilos —incluido el listener `@Async`— sin configurar nada. Trae las métricas HTTP del servidor por sí solo, sin depender de que el proyecto tenga Micrometer (no lo tiene).
- △ Es una caja negra: el evaluador no ve código de configuración. Suma un jar al runtime y un flag al arranque. "No escribí nada" puede leerse como poco mérito si no se explica.
- △ El agente no lee `application.properties`: se configura por variables de entorno o propiedades de sistema. **Esto cierra la opción B de la Decisión 8.**

**B · SDK + librerías de instrumentación**
- ✓ La configuración es código visible y versionado. Control explícito de qué se instrumenta. Se defiende mejor porque cada decisión está escrita.
- △ Hay que escribir el bootstrap del SDK. La propagación a través de `@Async` requiere configurar un `TaskDecorator` a mano, o el span de la notificación queda huérfano (verificado: `@EnableAsync` está activo, así que el hilo es otro de verdad).
- △ Hay que sumar explícitamente la instrumentación de Spring Web y la de métricas HTTP: sin Actuator ni Micrometer en el proyecto, nada de eso aparece solo.

**C · Spring Boot starter de OpenTelemetry**
- ✓ Punto medio: una dependencia y configuración por properties, coherente con el resto del proyecto, donde toda la política (`app.*`) ya vive en `application.properties`. Autoconfiguración de Spring, sin jar externo ni flags de arranque.
- △ Menos maduro que el agente en cobertura de librerías. La configuración vive en properties y no en código.
- △ Arrastra Micrometer como puente de métricas, que hoy el proyecto no tiene: es una dependencia más que aparece y hay que saber explicar de dónde salió.

> El enunciado acepta las tres: "SDK, distribución o auto-instrumentación oficial", y pide explicar la decisión.

> **De esta decisión dependen otras tres:** de dónde salen las métricas HTTP automáticas (bloque 6), si el contexto se propaga solo al listener `@Async` (bloque 3), y si la Decisión 8 tiene dos opciones o una sola.

## ◆ Decisión 2: ¿Qué backend para las trazas?

**A · Jaeger**
- ✓ Un contenedor, acepta OTLP nativo, UI propia lista. Cero configuración extra.
- △ Dos UIs separadas: Jaeger para trazas y otra cosa para métricas. El evaluador navega entre dos puertos.

**B · Grafana + Tempo**
- ✓ Una sola UI para trazas y métricas. Es lo que se usa a escala. Permite exemplars: saltar de una métrica a la traza concreta.
- △ Tres contenedores en vez de uno, con datasources y storage que configurar. Más superficie donde algo puede no levantar.

> El enunciado dice que no exige proveedor específico ni dashboards sofisticados.

## ◆ Decisión 3: ¿Qué entra en el `docker-compose`?

**A · Solo backend + stack de observabilidad**
- ✓ Menos piezas, arranque más rápido. El anexo es sobre la API: el frontend no genera telemetría propia.
- △ Para ver la app funcionando hay que levantar el front aparte.

**B · Todo, incluido el frontend**
- ✓ Un solo `docker compose up` levanta el sistema completo. El tráfico se puede generar navegando la UI en vez de con curl.
- △ Hay que resolver el build de producción del front y servirlo —nginx o similar—, y el CORS pasa a apuntar a otro origen.
- △ **`frontend/package.json` declara `"preinstall": "cd ../backend && gradle build -x test"`.** Ese hook corre dentro de un contexto de build donde `../backend` no existe, así que revienta el build de la imagen del front. Se resuelve con `npm ci --ignore-scripts`, pero es una pieza que hay que conocer de antemano: el síntoma no apunta al frontend.
- △ **El front tiene que quedar publicado en el puerto 3000, no en el 80.** `frontend/src/api/client.js` deriva la URL del backend reemplazando `3000` por `8080` sobre el host del navegador, con los dos puertos como constantes. Servido por nginx en el 80 ese reemplazo no encuentra nada y las llamadas se van al origen equivocado. La alternativa es pasar `VITE_API_BASE_URL` en tiempo de build, que es la vía prevista como override.

> **La opción B dejó de ser simétrica.** El plan la describía como "resolver el build y el CORS"; son cuatro piezas, no dos, y dos de ellas fallan de formas que no apuntan a su causa.

---

# Bloque 1 — Infraestructura de observabilidad

**~1 hora · 20% pipeline**

Levantar el Collector y los backends, sin tocar la app.

Archivos: `docker-compose.yml`, `otel/collector-config.yaml`, y la config del backend de métricas.

**Criterio de salida:** los servicios levantan, el health check del Collector responde, y las UIs abren. Nada más. Todavía no hay telemetría que mirar.

## ◆ Decisión 4: ¿Qué processors lleva el Collector y en qué orden?

**A · Solo `batch`**
- ✓ Mínimo indispensable. El enunciado lo pide explícito. Config corta y fácil de explicar.
- △ Deja afuera dos de los extras que el enunciado menciona como valorables.

**B · `memory_limiter` → `resource` → `batch`**
- ✓ Cubre dos extras del enunciado. El orden es defendible y muestra criterio: limitar antes de procesar, agrupar antes de exportar.
- △ Tres bloques más de YAML que hay que poder justificar si preguntan.

> El enunciado lista como extras: memory_limiter, health check, retry/queue de exportación.

## ◆ Decisión 5: ¿Cómo llegan las métricas a Prometheus?

**A · Exporter `prometheus` (pull)**
- ✓ El Collector expone un endpoint y Prometheus lo scrapea. Modelo nativo de Prometheus. Se debuggea entrando al endpoint y viendo las métricas crudas.
- △ Hay que configurar el scrape en `prometheus.yml` apuntando al Collector.

**B · Exporter `prometheusremotewrite` (push)**
- ✓ El Collector empuja. Un archivo de configuración menos.
- △ Requiere habilitar remote-write en Prometheus. Si algo no llega, no hay endpoint intermedio donde mirar.

## ◆ Decisión 6: ¿Sampling?

**A · Sin sampling**
- ✓ Todas las trazas visibles, que es lo correcto en local con tráfico de prueba. Una pieza menos.
- △ El enunciado lista "sampling configurable y explicación de cuándo lo usarías" como extra valorable.

**B · Head-based configurado al 100%**
- ✓ El mecanismo está y se ve en la config; bajar el porcentaje es cambiar un número. Demuestra el punto sin perder trazas.
- △ Un processor más que justificar, para algo que en local no cambia nada.

> En los dos casos se puede cubrir el extra explicando en el documento cuándo usar head y cuándo tail. La explicación sola ya suma.

---

# Bloque 2 — Contenerizar la app

**~45 min · 15% reproducibilidad**

Dockerfile del backend e integración al compose.

**Criterio de salida:** `docker compose up` levanta la app y responde a un curl contra un endpoint del seed. Todavía sin telemetría.

## ◆ Decisión 7: ¿Cómo se construye la imagen del backend?

> **El proyecto no tiene Gradle wrapper** (verificado). `./gradlew` no existe: el README ya advierte que hay que invocar `gradle` directamente. Eso cambia el costo de las tres opciones.

**A · Multi-stage: compila adentro, sobre una imagen `gradle:jdk17`**
- ✓ Quien clona solo necesita Docker: no hace falta JDK ni Gradle en la máquina. Reproducible de verdad.
- ✓ La imagen base ya trae Gradle, así que resuelve la ausencia del wrapper sin agregar nada al repo.
- △ El primer build tarda varios minutos bajando dependencias. Cada cambio de código implica rebuild.

**B · Espera el jar ya compilado**
- ✓ Build de imagen casi instantáneo. Iteración rápida durante el desarrollo.
- △ **Sin wrapper, quien clona necesita JDK 17 y Gradle instalados y en el `PATH`**, no alcanza con un `./gradlew` que se baje solo lo que falta. Tiene que correr `gradle build` desde `backend/` antes del compose. Baja la reproducibilidad, que es 15% de la nota.

**C · Agregar el wrapper al repo y multi-stage sobre una imagen de JDK**
- ✓ La versión de Gradle queda fijada en el repo y no depende de la de la imagen base ni de la de la máquina. Además arregla de paso lo que el README declara hoy como limitación.
- △ Suma al repo el `gradle-wrapper.jar`, un binario versionado, y toca el challenge principal en una entrega que debería ser solo del anexo.

> **La opción B es más cara de lo que el plan original decía.** No es "el que clona corre un comando de más": es "el que clona instala dos cosas". Se puede seguir desarrollando con B y cambiar a A o C al final.

## Piezas del compose que el plan no contemplaba

- El backend expone el 8080 (`server.port` en `application.properties`) y el front espera encontrarlo ahí: el mapeo de puertos no es libre (ver Decisión 3).
- Los orígenes de CORS ya son patrones configurables (`app.cors.allowed-origin-patterns`), así que sumar el origen del front containerizado es una línea de configuración y no un cambio de código.
- El store es en memoria: reiniciar el contenedor del backend vuelve al estado del seed. Es la forma más barata de dejar la demo en un estado conocido antes de generar tráfico.

## ◆ Decisión 8: ¿Dónde se configura el endpoint OTLP?

**A · Variables de entorno en el compose**
- ✓ Estándar de OTel (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`). La imagen no sabe a dónde exporta: lo decide quien la corre.
- △ La configuración vive en el compose y no en el repo de la app, así que hay que mirar dos archivos para entender el arranque.

**B · En `application.properties`**
- ✓ Toda la config de la app en un solo archivo, coherente con el resto del proyecto: scoring, notificaciones, CORS y seed ya viven ahí.
- △ El nombre del host del Collector es específico de Docker: correr la app fuera del compose requiere sobreescribirlo igual.
- △ **Depende de la Decisión 1: solo existe si se elige el SDK o el starter.** El javaagent no lee `application.properties`; se configura por variables de entorno o propiedades de sistema. Con la opción A de la Decisión 1, esta decisión queda resuelta por arrastre en la opción A.

---

# Bloque 3 — Instrumentación automática

**~45 min · 30% trazas**

Que la app emita telemetría sin escribir spans todavía. Según lo decidido en el bloque 0: el agente, el SDK o el starter. Configurar `service.name` y el export por OTLP.

**Criterio de salida:** pegarle a un endpoint y ver la traza en la UI, con el nombre de servicio correcto y los atributos HTTP semánticos.

## ◆ Decisión 9: ¿Qué instrumentación automática dejar activa?

**A · Todo lo que venga por defecto**
- ✓ Cero configuración. Cobertura máxima sin pensar.
- △ Genera spans de cosas irrelevantes para este sistema, y las trazas se llenan de ruido que hay que explicar.

**B · Solo HTTP entrante y runtime**
- ✓ Trazas limpias, centradas en lo que importa. La decisión de qué apagar es defendible por sí sola.
- △ Requiere identificar y desactivar lo que sobra, y justificar cada apagado.

> ✔ **Verificado:** la app no tiene base de datos, ni llamadas HTTP salientes, ni Actuator. Los tres canales de notificación escriben a log. Buena parte de la instrumentación automática no tiene nada que instrumentar, así que la diferencia real entre A y B es más chica de lo que el plan sugería: lo que sobra es sobre todo ruido de arranque y de framework, no spans de I/O.

## ⚠️ A verificar en este bloque

✔ **Ya verificado:** `@EnableAsync` está activo (`NotificationBeanConfig`), así que el listener de notificaciones corre de verdad en otro hilo. La duda no era infundada.

**Queda por verificar si el span de la notificación aparece colgado del request que creó la pregunta o si queda huérfano.** Depende de la Decisión 1: con el javaagent debería propagarse solo; con el SDK hace falta un `TaskDecorator`.

Si el contexto se propaga, hay un caso de demostración fuerte: una operación asincrónica visible dentro de la misma traza, que además es el único lugar donde este proyecto puede mostrar propagación de contexto —no hay llamadas salientes. Si no se propaga, hay que decidir si se fuerza la propagación o se documenta como limitación.

---

# Bloque 4 — Trazas manuales

**~1 hora · 30% trazas**

Spans de dominio con atributos propios. El enunciado pide **al menos un span hijo** alrededor de una operación relevante, con jerarquía padre-hijo entendible.

## ◆ Decisión 10: ¿Dónde poner los spans manuales?

Candidatos en el código, cada uno demuestra algo distinto:

**A · `QuestionService.createQuestion`**
- ✓ Es el único camino que conecta con la notificación asincrónica, o sea con el único caso de propagación de contexto que este proyecto tiene.
- ✓ La clasificación calculada ahí es la que decide si se notifica: es el dato de negocio más interesante que puede llevar un atributo.
- △ Es una escritura: para generar tráfico hay que crear preguntas, que mutan el estado del seed.
- △ **Corregido respecto del plan original: no es "la operación más rica".** Son cuatro líneas —resolver el pedido, construir la entidad, guardar, publicar el evento— y el cálculo del score vive en `publishCreated`, que es una sola llamada al scorer. Hay poco que anidar adentro.

**B · `QuestionService.listUnresolvedQuestions`**
- ✓ Es donde está el trabajo real, y ahora está verificado: `findUnresolved()` → `resolveOrders()` (que según haya filtro de vendedor pega a un repositorio o a otro) → N× `scorer.score()` → `sorted(byImportance())`. Son cuatro sub-operaciones separables de verdad, así que la jerarquía padre-hijo de la Decisión 11 sale natural acá y no hay que inventarla.
- ✓ Es la request que la vista de Operaciones dispara sola cada 30 segundos: genera tráfico de fondo sin que nadie haga nada.
- △ Es una lectura sin efectos: no conecta con el evento ni con la notificación.

**C · `QuestionImportanceScorer.score`**
- ✓ Es el corazón del challenge principal. Los atributos pueden exponer el desglose por factor —el `ScoreBreakdown` ya existe como objeto y trae los cinco—, lo que hace la traza autoexplicativa sin calcular nada nuevo.
- △ Se llama N veces por request de la cola: con las 12 preguntas del seed son 12 spans por request, y la cola se refresca sola cada 30 segundos. Y es cómputo puro, sin I/O que medir.

**D · Varios de los anteriores**
- ✓ Cobertura más completa y más superficie para demostrar. Combinar A y B cubre las dos mitades que ninguna opción sola cubre: A tiene la conexión asincrónica pero poco trabajo interno, B tiene el trabajo interno pero ningún efecto.
- △ Más código instrumentado que mantener y explicar. El enunciado pide "al menos uno", no todos.

> **A y B dejaron de ser intercambiables.** El plan las presentaba como dos operaciones ricas entre las que elegir; en el código cada una demuestra una cosa distinta y ninguna demuestra las dos.

## ◆ Decisión 11: ¿Qué granularidad de anidamiento?

**A · Un span por caso de uso**
- ✓ Traza legible de un vistazo. Bajo overhead.
- △ No muestra dónde se va el tiempo adentro de la operación.

**B · Span padre con hijos por sub-operación**
- ✓ Demuestra la jerarquía que el enunciado pide explícitamente. Se ve el reparto del tiempo entre buscar, calcular y persistir.
- △ Más código, y con operaciones en memoria los hijos duran microsegundos: la traza puede verse trivial.

> **Esta decisión está atada a la 10.** En `listUnresolvedQuestions` los hijos ya existen como métodos privados y la opción B es casi gratis; en `createQuestion` hay que partir cuatro líneas en tres spans, que es anidar por anidar. La granularidad no se elige en abstracto sino sobre la operación que se haya elegido instrumentar.

## ◆ Decisión 12: ¿Qué atributos de dominio poner en los spans?

Los spans admiten alta cardinalidad, a diferencia de las métricas. La pregunta es hasta dónde.

**A · Solo identificadores**
- ✓ Suficiente para correlacionar con los datos. Mínimo riesgo de exponer algo sensible.
- △ Para entender una traza hay que ir a buscar los datos aparte.

**B · Identificadores + datos de negocio**
- ✓ La traza se explica sola: score, clasificación, cantidad de keywords, estado del pedido. Es la diferencia entre "pasó algo" y "pasó esto por esto".
- △ Hay que revisar que nada sea PII. **El riesgo es concreto y está a mano:** `Buyer` no es entidad propia, sus datos (nombre y email) viajan embebidos en `Order`, y los dos candidatos a span de la Decisión 10 tienen el `Order` completo en la mano. El comprador no puede ir a los atributos, ni el texto de la pregunta, que es entrada libre del usuario.

> El enunciado menciona la protección de datos sensibles como tema de conversación en la defensa.

> **Hay un criterio ya establecido en el proyecto al que alinearse:** DECISIONS.md declara que los logs no registran PII y que los avisos de notificación llevan ids, clasificación y score, pero no el texto de la pregunta ni datos del comprador. Aplicar el mismo corte a los atributos de span es coherencia, no una decisión nueva.

---

# Bloque 5 — Errores en las trazas

**~45 min · 30% trazas**

Que un escenario con error se distinga de uno exitoso. Es un requisito explícito del *definition of done*.

La app tiene tres familias de error: validación de entrada (400), no encontrado (404) y regla de negocio (409), más lo inesperado (500).

## ⚠️ El hallazgo que condiciona todo este bloque

✔ **Verificado: el `ApiExceptionHandler` se traga todas las excepciones**, incluida una red de contención `@ExceptionHandler(Exception.class)`. Ninguna excepción escapa del `DispatcherServlet`.

**Consecuencia: hoy la instrumentación automática no va a registrar ninguna excepción en el span HTTP, ni siquiera en los 500.** Lo único que le queda al span es el status code, y por convención un span de servidor solo pasa a `ERROR` desde 5xx. O sea que, sin escribir nada, el 409 de transición inválida produce una traza **idéntica** a la de un request exitoso.

Esto no es un detalle de implementación: es lo que decide si el bloque 5 existe o no. El requisito de "distinguir un escenario exitoso de uno con error" no se cumple solo.

**Y hay un segundo hallazgo:** ✔ **no hay ningún 500 reproducible desde afuera.** Los tres `IllegalStateException` del código son violaciones de integridad o de configuración que las invariantes del dominio impiden que ocurran. El escenario de error demostrable es necesariamente un 4xx —lo que le da a la Decisión 14 un peso que el plan no le daba—, salvo que se agregue a propósito un camino que falle.

Dónde nace cada error, que es lo que determina qué span está abierto cuando ocurre:

| Familia | Nace en | ¿Hay span manual abierto? |
|---|---|---|
| 400 de conversión de tipo (UUID, enum, fecha) | Spring, **antes** del controller | No |
| 400 de Bean Validation | Frontera del controller | No |
| 400 de regla multi-campo (`from` > `to`) | Controller, a mano | No |
| 400 de invariante de dominio | Entidad, vía el service | Depende de la Decisión 10 |
| 404 | Service (`requireOrder`, `requireQuestion`) | Depende de la Decisión 10 |
| 409 | Entidad (`OrderStatus` / `QuestionStatus`) | Depende de la Decisión 10 |
| 500 | Violación de integridad | No reproducible |

## ◆ Decisión 13: ¿Dónde se marca el span como error?

**A · En el span manual del servicio**
- ✓ Es donde ocurre la falla y donde ya hay un span abierto. El contexto de dominio está a mano.
- △ Hay que capturar y relanzar en cada servicio instrumentado. Si la excepción nace en la entidad, el servicio la ve pero no su contexto interno.
- △ **Sola no alcanza, y ahora se sabe por qué:** según la tabla de arriba, los tres tipos de 400 más frecuentes (conversión de tipo, Bean Validation y regla multi-campo) nacen antes de llegar a cualquier service. Un `?status=EN_ADUANA` o un UUID mal formado no pasarían por ningún span manual, así que quedarían sin marcar por completo.

**B · En el `ApiExceptionHandler`**
- ✓ Un solo lugar, coherente con que el manejo de errores ya está centralizado ahí. Cero código repetido.
- ✓ **Es el único punto por el que pasan las cuatro familias de error**, incluidas las que nacen antes del controller. Y como el `@RestControllerAdvice` corre dentro del `DispatcherServlet`, el span de servidor todavía está abierto y activo cuando el handler se ejecuta.
- △ Cuando la excepción llega al handler, el span manual del servicio ya cerró: solo queda marcar el span del controller. Hay que verificar que sea el activo.
- △ Mete una dependencia de OpenTelemetry en una clase de la capa web que hoy no conoce nada de observabilidad.

**C · Los dos**
- ✓ El span de dominio marca la causa y el del borde marca el resultado HTTP.
- △ Duplica la señal y hay que explicar por qué aparece dos veces.

> **La decisión dejó de ser simétrica.** El plan presentaba B como "un solo lugar, cómodo"; verificado el handler, B es la única opción que cubre las cuatro familias de error, y A sola deja los 400 de frontera invisibles. Sigue habiendo elección —entre B y C, y sobre qué marca cada una—, pero no entre A y B.

## ◆ Decisión 14: ¿Qué hacer con los 4xx?

**A · Seguir la convención de OTel**
- ✓ Un 4xx no marca el span del servidor como error, porque el problema es del cliente. Es lo que espera cualquier herramienta y lo que hace la instrumentación automática.
- △ El requisito de "distinguir exitoso de con error" habría que cubrirlo por otro lado: las métricas, que sí llevan el status code.
- △ **El costo real es mayor de lo que el plan decía.** Como no hay ningún 500 reproducible desde afuera, con esta opción **no queda ninguna traza en rojo en toda la demo**: el escenario de error se demuestra únicamente por métricas y por atributos del span. Es defendible por convención, pero hay que poder sostenerlo frente a un *definition of done* que pide que el error "se refleje en la traza".

**B · Marcar también los de negocio (409)**
- ✓ Cumple el requisito de forma directa y visible en la UI de trazas: la traza sale en rojo.
- △ Se aparta de la convención. Un 409 de "no se puede cancelar un pedido entregado" es el sistema funcionando bien, no fallando.

**C · Agregar un camino que falle a propósito**
- ✓ Deja tener un 5xx real y reproducible, y con eso la convención de A se puede respetar sin quedarse sin escenario de error.
- △ Es código que existe solo para la demo, justo en un proyecto cuyo README declara el alcance excluido con cuidado. El enunciado del anexo además marca "complejidad innecesaria" como algo a evitar.

> Sea cual sea, conviene documentarla: es exactamente el tipo de cosa que se pregunta en una defensa.

> **Esta decisión subió de peso.** Con un 500 reproducible sería una discusión de convención; sin él, es la que determina si existe o no una traza en rojo para mostrar.

## ◆ Decisión 15: ¿Cuándo registrar la excepción como evento del span?

**A · Siempre que haya excepción**
- ✓ Simple y uniforme. Toda falla queda con su stacktrace.
- △ Las excepciones de negocio son flujo esperado: llenar las trazas de stacktraces de reglas que funcionaron ensucia y ocupa.

**B · Solo las inesperadas**
- ✓ El stacktrace aparece donde sirve para diagnosticar. Las de negocio quedan con status y mensaje, sin ruido.
- ✓ **El criterio ya está escrito en el código y no hay que inventarlo:** el handler tiene un método por tipo de excepción, y las tres del dominio (`DomainValidationException`, `ResourceNotFoundException`, `BusinessRuleException`) están separadas de la red de contención. Distinguir es elegir en qué métodos registrar.
- △ Requiere distinguir por tipo de excepción y justificar el criterio.
- △ Como no hay 500 reproducible, con esta opción el `recordException` no se ve nunca en la demo: el mecanismo está y no se puede mostrar funcionando.

---

# Bloque 6 — Métricas

**~45 min · 20% métricas**

Volumen, latencia, errores y al menos una métrica custom.

✔ **Verificado, y el resultado corrige el supuesto del plan.** El proyecto **no tiene Actuator ni Micrometer**: `build.gradle` declara solo `web`, `validation` y `test`. Hoy no existe ninguna fuente de métricas, así que las tres primeras no vienen "probablemente" de ningún lado: **dependen enteramente de la Decisión 1.**

- Con el **javaagent**, el histograma `http.server.request.duration` con método, ruta y status viene solo. Volumen, latencia y errores salen de esa única métrica, sin escribir nada.
- Con el **SDK**, hay que sumar a mano la instrumentación de Spring Web y la de métricas HTTP.
- Con el **starter**, llegan vía el puente de Micrometer, que la dependencia arrastra.

Si la Decisión 1 no cubre las métricas HTTP automáticas, este bloque crece: pasa de "una métrica custom" a "una métrica custom más las tres de base".

## ◆ Decisión 16: ¿Qué métrica custom?

Candidatos, cada uno cuenta una historia distinta:

**A · Preguntas creadas por clasificación**
- ✓ Counter con una etiqueta de 4 valores (✔ `QuestionPriority` tiene exactamente `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`): cardinalidad mínima y acotada por el enum. Conecta directo con el corazón del challenge y responde "¿cuántas críticas entran por hora?".
- ✓ El punto de instrumentación ya existe: `publishCreated` calcula la clasificación y la pone en el evento.
- △ Solo mide creación: no dice nada del estado de la cola.

**B · Distribución del score**
- ✓ Histograma: permite ver percentiles y si la fórmula discrimina bien o amontona todo en el medio. Es la métrica que usaría el equipo de negocio para ajustar los pesos.
- ✓ Los buckets no son arbitrarios: DECISIONS.md ya fija el score máximo teórico en 195 y los umbrales de clasificación en 50 / 90 / 130, que son los cortes naturales.
- △ Elegir los buckets requiere criterio, y un histograma pesa más que un counter.

**C · Notificaciones despachadas por canal y resultado**
- ✓ Cubre el objetivo 3 del challenge principal y hace observable el aislamiento de fallos entre canales, que hoy solo se ve como una línea de log en `sendSafely`.
- △ ✔ Verificado: solo el canal de email está habilitado (`app.notifications.channels.email.enabled=true`; slack y sms en `false` ni siquiera existen como bean). La métrica tiene una sola serie salvo que se encienda otro canal en el compose, que es una variable de entorno.
- △ Solo se incrementa cuando una pregunta supera el umbral `HIGH`: con tráfico de prueba común, la métrica queda en cero y hay que provocar el caso a propósito.

**D · Preguntas sin resolver en la cola**
- ✓ Es la métrica que Operaciones miraría todos los días. Un valor que sube y baja.
- ✓ `QuestionRepository.findUnresolved()` ya existe y no recibe parámetros: un gauge observable puede llamarlo directo, sin agregar nada al contrato de persistencia.
- △ Es un gauge sobre un estado derivado: hay que decidir cuándo se observa, porque no hay un evento que lo actualice.
- △ Con el store en memoria, el valor se reinicia al estado del seed cada vez que el contenedor arranca.

## ◆ Decisión 17: ¿Qué etiquetas llevan las métricas custom?

**A · Solo dimensiones acotadas**
- ✓ Clasificación, canal, estado: conjuntos de 3 a 5 valores. La cardinalidad no crece con el tráfico nunca.
- △ Para saber qué pregunta concreta disparó algo hay que ir a las trazas.

**B · Sumar el vendedor**
- ✓ Permite ver el comportamiento por vendedor, que es una pregunta de negocio razonable. El dato está a mano: `QuestionCreatedEvent` ya lleva el `sellerId`, así que no hay que ir a buscarlo.
- △ La cardinalidad pasa a crecer con la cantidad de vendedores. Con dos en el seed no se nota; con 50.000 sí. El enunciado marca la cardinalidad como criterio de evaluación.
- △ El `sellerId` es un UUID, así que la etiqueta sería un identificador opaco: lo que el enunciado lista como "IDs como labels de métricas" y pide evitar. Un nombre de vendedor legible tampoco está disponible sin ir a buscarlo al repositorio.

> Los identificadores de pregunta, pedido y request están explícitamente listados en el enunciado como algo a evitar.

---

# Bloque 7 — Verificación y evidencia

**~45 min**

Demostrar que funciona de punta a punta. Lo que hay que poder mostrar, según el *definition of done*:

- Un request genera una traza visible de punta a punta
- Hay al menos un span custom con jerarquía entendible
- Los errores se reflejan en la traza
- Métricas de volumen, latencia y errores, más la custom
- El escenario exitoso y el de error se distinguen

> ⚠️ **El escenario de error ya tiene forma decidida por el código:** verificado que no hay 500 reproducible desde afuera, lo que se puede demostrar es un 4xx —el 409 de transición inválida y el 400 de rango de fechas invertido, los dos con curls listos en el README—, y que se vea como error depende de lo que se resuelva en las Decisiones 13 y 14.

## ◆ Decisión 18: ¿Cómo se genera el tráfico de prueba?

**A · Script de curls versionado en el repo**
- ✓ Reproducible por cualquiera con un comando. Documenta los escenarios y se puede correr en la demo en vivo.
- ✓ Buena parte del trabajo ya está hecha: el README del challenge principal tiene curls con ids reales del seed para el camino feliz, la notificación `HIGH`, el 409 y el 400.
- △ Un archivo más que mantener alineado con el estado del seed.
- △ **No es idempotente, y eso importa acá.** El store es en memoria y los curls de escritura mutan el seed: correr el script dos veces crea preguntas nuevas, alarga la cola de Operaciones y hace que el segundo 409 no se reproduzca igual si el pedido ya cambió de estado. Reiniciar el contenedor del backend antes de cada corrida lo resuelve, pero hay que decirlo en el README.

**B · Navegando la UI**
- ✓ Tráfico realista y demo más vistosa: se genera la telemetría usando la app.
- ✓ La vista de Operaciones se refresca sola cada 30 segundos, así que dejarla abierta genera tráfico de fondo constante sin tocar nada: sirve para que las métricas tengan una línea de base en vez de picos aislados.
- △ Requiere el front levantado —lo que ata esta decisión a la 3— y es menos determinista: cuesta reproducir exactamente el mismo escenario dos veces.
- △ La UI no ofrece opciones que el backend vaya a rechazar (el selector de estado solo muestra transiciones válidas, el de producto solo ítems del pedido). **Los escenarios de error casi no se pueden producir desde la interfaz**, que es justamente uno de los dos escenarios a demostrar.

**C · Los dos**
- ✓ El script para reproducir, la UI para mostrar.
- ✓ Cubre el hueco de cada uno: la UI no puede generar errores y el script no genera tráfico sostenido.
- △ Más trabajo de preparación.

> **B dejó de alcanzar sola.** El plan las presentaba como dos formas equivalentes de generar tráfico; la UI está diseñada para no producir errores, así que por sí sola no cubre el *definition of done*.

---

# Bloque 8 — Documentación

**~45 min · 15% criterio**

El enunciado pide cinco cosas: cómo levantar todo, cómo generar tráfico y reproducir un error, dónde ver trazas y métricas, qué se instrumentó automático y qué manual con el porqué, y las decisiones con sus trade-offs.

**Y hay una sexta que no está en la lista pero vale:** los temas anunciados para la conversación de la defensa —sampling, cardinalidad, PII, backpressure, propagación, SLI/SLO— conviene tenerlos escritos aunque no estén implementados. La explicación cuenta.

## ◆ Decisión 19: ¿Dónde vive la documentación del anexo?

**A · Documento propio**
- ✓ El README del challenge principal queda intacto. El anexo se lee como una entrega separada, que es lo que es.
- ✓ Sigue el patrón que el repo ya usa: README para cómo correrlo, DECISIONS.md para el porqué. Un tercer documento no rompe ninguna convención.
- △ Quien abre el repo ve dos documentos y tiene que saber cuál mirar. Con DECISIONS.md ya son tres.

**B · Sección dentro del README existente**
- ✓ Un solo punto de entrada. Todo el proyecto se entiende de corrido.
- △ El README ya tiene 412 líneas y DECISIONS.md más de mil: no es "largo" en abstracto, ya está en el límite de lo que alguien lee de corrido. Y mezcla dos entregas que tienen fechas y criterios de evaluación distintos.
- △ **Hay secciones del README que el anexo contradice, no que complementa.** "Puesta en marcha" dice que hacen falta JDK, Gradle y Node y que el arranque tarda 40-55 segundos; con el compose eso pasa a ser el camino alternativo. Integrar el anexo obliga a reescribir partes de la entrega anterior.

## ◆ Decisión 20: ¿Sobre qué repositorio se entrega?

**A · El mismo repo, rama principal**
- ✓ Un solo lugar. El anexo se apoya sobre la API del challenge, así que están juntos por naturaleza.
- △ Mezcla en el historial dos entregas con fechas distintas. Si evalúan el challenge principal después, ven commits del anexo encima.

**B · Rama aparte**
- ✓ El challenge principal queda congelado en `main`, que es donde está hoy toda la entrega anterior. El anexo se ve como un diff limpio sobre esa base.
- ✓ Hace visible qué tocó el anexo del código existente, que es exactamente lo que el evaluador del anexo quiere ver.
- △ Hay que explicar en qué rama está cada cosa.

> **Esta decisión se cruza con la 13.** El anexo va a tener que modificar `ApiExceptionHandler`, que es código del challenge principal y no un archivo nuevo. Cuánto ensucia el anexo la entrega anterior no es hipotético: depende de lo que se resuelva en el bloque 5.

---

# Resumen de decisiones

| # | Decisión | Elegida | Porque |
|---|---|---|---|
| 1 | Forma de instrumentar | **A · Javaagent** | Se delega lo que se pueda mientras siga siendo consistente con la consigna, que acepta explícitamente la auto-instrumentación oficial. La caja negra se compensa explicando los conceptos que el agente aplica. De paso resuelve solas dos cosas que el proyecto no tiene: las métricas HTTP (no hay Actuator ni Micrometer) y la propagación de contexto al listener `@Async`. |
| 2 | Backend de trazas | **A · Jaeger** | Menos es más: un contenedor, OTLP nativo, UI lista, cero configuración. No hace falta complicarse con datasources y storage para un requisito que no exige proveedor específico ni dashboards sofisticados. Se acepta navegar entre dos UIs. |
| 3 | Alcance del compose | **A · Solo backend + stack de observabilidad** | El frontend no emite telemetría: en el compose de un anexo de observabilidad es superficie sin señal, y el enunciado pide evitar complejidad innecesaria. No se pierde la demo por UI: el front sigue levantando con `npm run client` contra el backend containerizado. Además la UI no puede producir los escenarios de error, así que el script de curls hay que escribirlo igual y B no ahorraba trabajo, lo movía de bloque. |
| 4 | Processors del Collector | **B · `memory_limiter` → `resource` → `batch`** | Son conceptos defendibles y muestran comprensión de observabilidad, que es parte de lo que se evalúa. El orden se justifica solo: limitar antes de procesar, agrupar antes de exportar. `health_check` (extension) y `retry`/`sending_queue` (config del exporter) se cubren aparte: no forman parte de esta decisión. |
| 5 | Métricas a Prometheus | **A · Exporter `prometheus` (pull)** | Es el modelo nativo de Prometheus y deja un punto de corte para diagnosticar: si las métricas no aparecen, un `curl` al endpoint del Collector dice de qué lado está el problema. Con push no hay lugar intermedio donde mirar. El ahorro de configuración de B era menor al que el plan sugería: remote-write necesita `--web.enable-remote-write-receiver`, así que el archivo que ahorra reaparece como argumento en el compose. |
| 6 | Sampling | **B · Head-based declarado explícito al 100%** | El javaagent ya hace head sampling al 100% por defecto (`parentbased_always_on`), así que la elección real era entre implícito y explícito: declararlo deja el mecanismo a la vista y bajar el porcentaje pasa a ser cambiar un número. Son dos variables de entorno, no un processor: mucho más barato de lo que el plan asumía. Tail sampling se descartó por sobreingeniería, porque descartar trazas choca con el *definition of done* (mostrar una traza concreta de punta a punta) y porque su política útil depende de que los errores estén marcados, que es lo que el Bloque 5 todavía tiene abierto. |
| 7 | Construcción de la imagen | **C · Wrapper al repo + multi-stage sobre imagen de JDK** | Es la única que hace que el build de Docker y el local usen el mismo Gradle: el Dockerfile corre `./gradlew` sobre una imagen de JDK pelada, en vez de traer su propia versión. Con A quedaban dos toolchains distintas sobre el mismo código, y la declarada era la que menos se usa para desarrollar. Además la versión de Gradle hoy no está escrita en ningún lado, que es exactamente el "conocimiento implícito del entorno" que el *definition of done* pide eliminar. Condición: el commit incluye correr los 183 tests con la versión pineada, para que sea un dato verificado y no otra suposición. |
| 8 | Dónde se configura OTLP | **A · Variables de entorno, repartidas por dueño** | B quedó fuera por arrastre de la Decisión 1: el javaagent no lee `application.properties`. Dentro de A, las variables se reparten según quién posee el valor, no por comodidad: el **endpoint y el sampler van al `docker-compose.yml`** (describen el despliegue, y `otel-collector:4317` ni siquiera se resuelve fuera de esa red), y **`OTEL_SERVICE_NAME` va como `ENV` en el Dockerfile** (describe la identidad del servicio, es la misma en cualquier entorno, y con varios backends cada uno debe identificarse solo). Se descartó un `.env` versionado: para tres variables agrega indirección sin agregar información. El criterio espeja la forma productiva —identidad en el artefacto, cableado en el deployment, secretos en un secret store— así que escalar cambia el mecanismo de inyección, no el diseño. |
| 9 | Instrumentación automática activa | **A · Todo lo que venga por defecto** | Cerrada con evidencia, no en abstracto: con el agente cableado, cada request produce **exactamente un span** (el de entrada HTTP) y cero ruido de framework. No hay nada que apagar, porque sin base de datos, sin llamadas salientes y sin Actuator, el resto del catálogo del agente no tiene librería que lo dispare. Elegir B habría sido escribir configuración para desactivar instrumentación que no se activa. Los atributos salen con las convenciones semánticas actuales y el span se nombra con la ruta parametrizada, así que la cardinalidad ya viene bien de fábrica. |
| 10 | Ubicación de los spans manuales | | |
| 11 | Granularidad de anidamiento | | |
| 12 | Atributos de dominio | | |
| 13 | Dónde se marca el error | | |
| 14 | Tratamiento de los 4xx | | |
| 15 | Cuándo registrar la excepción | | |
| 16 | Métrica custom | | |
| 17 | Etiquetas de las métricas | | |
| 18 | Generación de tráfico | | |
| 19 | Ubicación de la documentación | | |
| 20 | Repositorio de entrega | | |

## Decisiones que dependen de otras

Detectado al leer el código. No cambia el orden en que se cierran, pero sí qué queda condicionado:

- **1 → 8.** Con el javaagent, la Decisión 8 no tiene dos opciones: el agente no lee `application.properties`.
- **1 → bloque 6.** De la forma de instrumentar depende si las métricas HTTP de volumen, latencia y errores vienen solas o hay que sumarlas. No hay Actuator ni Micrometer que las provea por su cuenta.
- **1 → bloque 3 (`@Async`).** De la forma de instrumentar depende si el span de la notificación se cuelga solo del request o hay que forzar la propagación con un `TaskDecorator`.
- **10 → 11.** La granularidad de anidamiento no se elige en abstracto: en `listUnresolvedQuestions` los hijos ya existen, en `createQuestion` hay que fabricarlos.
- **13 → 14.** Sin 500 reproducible, qué se hace con los 4xx determina si hay o no una traza en rojo para mostrar.
- **13 → 20.** El bloque 5 modifica código del challenge principal, así que define cuánto ensucia el anexo la entrega anterior.
- **3 → 18.** Generar tráfico navegando la UI requiere que el frontend esté en el compose.

**Estimación total:** unas 6 horas sumando los ocho bloques, más el colchón de la primera vez que levanta la stack. El bloque 6 puede crecer si la Decisión 1 no trae las métricas HTTP automáticas, y el 5 es más trabajo del estimado: sin tocar el handler no hay ningún error visible en las trazas.

Los bloques 1 y 2 son los que más pueden trabar: hasta que el `docker compose up` no funcione, no conviene avanzar. A partir del bloque 3 el trabajo es más predecible.
