# Observabilidad con OpenTelemetry

Anexo al challenge principal. Instrumenta la API de pedidos y preguntas para
poder observarla mientras recibe tráfico: trazas, métricas, un Collector como
componente separado, y backends donde inspeccionarlas.

El README del challenge principal está en [README.md](README.md); las
decisiones de diseño de la aplicación, en [DECISIONS.md](DECISIONS.md). Las
decisiones de este anexo, con sus alternativas descartadas y el porqué, están en
[PLAN_ANEXO_OTEL.md](PLAN_ANEXO_OTEL.md).

## Arquitectura

```
  curl / navegador
        |  HTTP
        v
  +-----------+  OTLP gRPC  +----------------+   OTLP   +------------+
  |  backend  |------------>| OTel Collector |--------->|   Jaeger   |  trazas
  | +javaagent|    :4317    | memory_limiter |          +------------+
  +-----------+             | resource       |   :8889  +------------+
                            | batch          |<---------| Prometheus |  métricas
                            +----------------+  scrape  +------------+
```

La aplicación no conoce a Jaeger ni a Prometheus: exporta OTLP al Collector y
ahí termina su responsabilidad. Cambiar de backend no toca una línea de código.

## Cómo levantarlo

Requiere únicamente Docker. No hace falta JDK, Gradle ni Node: la imagen del
backend compila adentro con el wrapper del repo.

```bash
docker compose up -d --build
```

Levanta cuatro servicios. El backend tarda unos 20 segundos en responder.

| Servicio | URL | Para qué |
|---|---|---|
| Backend | http://localhost:8080 | La API |
| **Jaeger** | **http://localhost:16686** | **Trazas** |
| **Prometheus** | **http://localhost:9090** | **Métricas** |
| Collector (health) | http://localhost:13133 | Estado del pipeline |
| Collector (métricas) | interno, `:8889/metrics` | Lo que Prometheus scrapea |

Para bajarlo: `docker compose down`.

> El frontend queda fuera del compose a propósito: no emite telemetría propia.
> Se levanta aparte con `npm run client` si se lo quiere usar para generar
> tráfico navegando la aplicación.

## Cómo generar tráfico y cómo reproducir un error

```bash
bash otel/generar-trafico.sh
```

Cubre los escenarios exitosos, la notificación asincrónica y cinco errores (un
409, tres 400 distintos y un 404). No es idempotente: crea preguntas y el store
es en memoria, así que para repetirlo desde un estado conocido conviene
`docker compose restart backend` antes.

Un error puntual, sin el script — transición inválida sobre un pedido que está
`CANCELLED`:

```bash
curl -X PATCH "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders/30000000-0000-0000-0000-000000000006/status" \
  -H "Content-Type: application/json" -d '{"status":"SHIPPED"}'
```

## Dónde consultar las trazas y las métricas

**Trazas** — en Jaeger, servicio `seller-dashboard-api`.

- *Escenario exitoso con jerarquía:* buscá `GET /api/ops/questions/unresolved`.
  La traza tiene tres niveles y muestra el reparto del tiempo:

  ```
  GET /api/ops/questions/unresolved          15.7ms
    +- ops.listUnresolvedQuestions           15.7ms   questions.unresolved.count=11
         +- ops.resolveOrders                 0.9ms
         +- ops.scoreAndSort                  3.4ms   questions.top.score=145
  ```

- *Continuidad asincrónica:* buscá `POST /api/orders/{orderId}/questions`. Si la
  pregunta clasificó `HIGH` o más, la traza incluye `notifications.dispatch`,
  que corre **después** de que la respuesta HTTP se envió, en otro hilo.
- *Escenario con error:* filtrá por el tag `error.type`. Por ejemplo
  `error.type=BusinessRuleException` trae los 409.

**Métricas** — en Prometheus.

> Las métricas tardan **hasta un minuto y medio** en aparecer después de generar
> tráfico: el SDK las exporta cada 60 segundos, el Collector batchea, y
> Prometheus scrapea cada 15. Las trazas, en cambio, aparecen en pocos segundos.
> Si una consulta devuelve vacío recién arrancado, es esto y no un fallo.

```promql
sum by (http_response_status_code) (http_server_request_duration_seconds_count)

histogram_quantile(0.95, sum by (le, http_route) (rate(http_server_request_duration_seconds_bucket[5m])))

questions_created_total

notifications_dispatched_total
```

## Qué instrumenté automáticamente y qué manualmente

**Automático — el javaagent de OpenTelemetry (2.31.1).** Aporta el span de
entrada HTTP con convenciones semánticas, los atributos de recurso
(`service.name`, `service.version`, `container.id`, runtime), el histograma
`http_server_request_duration_seconds` con método, ruta y status, 14 métricas de
JVM, y la propagación de contexto entre hilos.

Se eligió el agente sobre el SDK manual porque el proyecto no tiene Actuator ni
Micrometer: por el SDK habría que sumar a mano la instrumentación de Spring Web
y la de métricas HTTP para llegar al mismo punto. El costo aceptado es que la
instrumentación automática es una caja negra; lo que se controla explícitamente
está abajo.

Se verificó que no hace falta desactivar nada: cada request produce exactamente
un span de entrada y cero ruido de framework. La aplicación no tiene base de
datos ni llamadas HTTP salientes, así que buena parte del catálogo del agente no
tiene librería que lo dispare.

**Manual — tres cosas que el agente no puede inferir:**

1. **Spans de dominio** (`ops.listUnresolvedQuestions`, `ops.resolveOrders`,
   `ops.scoreAndSort`, `notifications.dispatch`), declarados con `@WithSpan`. El
   agente no sabe qué operación de negocio vale la pena medir.
2. **Atributos de dominio**: score, clasificación, cantidad de preguntas
   puntuadas, canales activos.
3. **Dos métricas custom** y **el marcado de errores** en el handler.

### Por qué los spans están donde están

`listUnresolvedQuestions` y el listener asincrónico viven en endpoints
distintos, así que sus spans nunca aparecen en la misma traza: no hay
redundancia posible entre ellos.

- **La cola de Operaciones** es donde está el trabajo real —traer las preguntas,
  resolver los pedidos, puntuar N y ordenar— y donde la jerarquía padre-hijo
  sale de la estructura que el código ya tenía, sin fabricarla.
- **El listener** es el único lugar donde esta API cruza una frontera
  asincrónica. Como no hace llamadas HTTP salientes, es el único sitio donde se
  puede demostrar continuidad de contexto.

**El scoring es un span, no N.** Con 11 preguntas sin resolver, un span por
pregunta daría 11 spans por request cada 30 segundos, porque la vista de
Operaciones se refresca sola. La cantidad viaja como atributo
`questions.scored.count`, que dice lo mismo sin inflar la traza.

## Errores en la telemetría

Este fue el punto que más trabajo requirió, por una característica del código
existente: **el `ApiExceptionHandler` captura todas las excepciones**, incluida
una red de contención `@ExceptionHandler(Exception.class)`. Ninguna escapa al
`DispatcherServlet`, así que la instrumentación automática no ve nada que
registrar: sin intervención, un 409 produce una traza idéntica a la de un
request exitoso.

Por eso el marcado se hace en el handler y no en los spans de dominio: es el
único punto por el que pasan las cuatro familias de error, incluidas las que
nacen **antes** del controller (conversión de tipos, Bean Validation) y que
nunca llegan a un service.

| Familia | Nace en | Marcado |
|---|---|---|
| 400 conversión de tipo | Spring, antes del controller | `error.type` |
| 400 Bean Validation | Frontera del controller | `error.type` |
| 400 invariante de dominio | Entidad | `error.type` |
| 404 | Service | `error.type` |
| 409 regla de negocio | Entidad | `error.type` |
| 5xx | Red de contención | `error.type` + status `ERROR` + stacktrace |

**Los 4xx no marcan el span como `ERROR`**, siguiendo la convención de OTel: el
problema es del cliente, no del servidor, y un 409 de "no se puede despachar un
pedido cancelado" es el sistema funcionando bien. Quedan identificables por el
atributo `error.type` y contabilizados en las métricas por status code.

**El stacktrace se registra solo en lo inesperado.** Una excepción de negocio es
flujo previsto: llenar las trazas de stacktraces de reglas que funcionaron
correctamente es ruido que además ocupa lugar.

## Métricas y cardinalidad

| Métrica | Tipo | Etiquetas | Valores posibles |
|---|---|---|---|
| `http_server_request_duration_seconds` | Histograma | método, ruta, status | Acotado: la ruta es el template, no el path |
| `questions_created_total` | Counter | `question.priority` | 4 (`LOW` a `CRITICAL`) |
| `notifications_dispatched_total` | Counter | canal, resultado | 3 x 2 |
| `jvm_*` | Varias | — | — |

Ninguna etiqueta crece con el tráfico. Se descartó agregar el vendedor: es un
UUID, o sea un identificador opaco como label, que es exactamente lo que el
enunciado marca como algo a evitar, y su cardinalidad crecería con el negocio.
Los ids de pregunta y de pedido viven en los **spans**, donde la alta
cardinalidad sí es apropiada.

**Limitación conocida:** el agente emite `service.instance.id` como atributo de
recurso, y el exporter de Prometheus lo convierte en la etiqueta
`exported_instance`. Es un UUID nuevo por cada arranque del proceso, así que
cada reinicio crea series nuevas que quedan para siempre. En local es
irrelevante; en producción es crecimiento de cardinalidad sin techo, y habría
que descartarlo con un processor en el Collector.

## Datos sensibles

Ningún atributo lleva PII. El nombre y el email del comprador viajan embebidos
en `Order`, que está a mano en los dos servicios instrumentados, así que la
omisión es deliberada y no incidental. Tampoco viaja el texto de la pregunta,
que es entrada libre del usuario. Es el mismo criterio que el proyecto ya
aplicaba a los logs y a los avisos de notificación (ver DECISIONS.md): viajan
ids, clasificación y score, que alcanzan para actuar.

## Decisiones y trade-offs

Las veinte decisiones del anexo, con sus opciones descartadas y el porqué de
cada una, están en [PLAN_ANEXO_OTEL.md](PLAN_ANEXO_OTEL.md). Las principales:

- **Javaagent sobre SDK manual.** Sin Actuator ni Micrometer en el proyecto, el
  SDK obligaba a sumar a mano lo que el agente trae resuelto.
- **Jaeger antes que Grafana + Tempo.** Un contenedor, OTLP nativo, UI lista. El
  costo aceptado son dos UIs separadas. Se usa v2 porque v1 llegó a
  end-of-life el 31/12/2025.
- **Collector core, no contrib.** El manifest de core ya trae los seis
  componentes del pipeline: la distribución más acotada que cubre el caso.
- **`memory_limiter` antes que `resource` y `batch`.** Limitar en la entrada
  antes de gastar trabajo procesando lo que no se va a poder exportar.
- **Prometheus por pull.** El endpoint del Collector es un punto de corte para
  diagnosticar: si las métricas están ahí, lo que falla es el scrape; si no
  están, falla la aplicación o el pipeline.
- **Wrapper de Gradle agregado al repo.** Es lo que hace que el build de Docker y
  el local usen la misma versión de Gradle. Antes no estaba declarada en ningún
  lado, o sea que era conocimiento implícito del entorno.

### Variables de entorno, repartidas por dueño

| Variable | Dónde | Por qué |
|---|---|---|
| `OTEL_SERVICE_NAME` | Dockerfile | Identidad del servicio: la misma en cualquier entorno, y la imagen no debería ser anónima si alguien la corre suelta |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | compose | Describe el despliegue; ese hostname solo se resuelve dentro de esa red |
| `OTEL_TRACES_SAMPLER` y `_ARG` | compose | Política de muestreo, típicamente distinta por entorno |

En Kubernetes el mecanismo de inyección cambia, pero el reparto no: identidad en
el artefacto, cableado en el deployment, credenciales en un secret store. Un
`service.name` en un store compartido permitiría que un deploy mal parametrizado
le diera a un servicio el nombre de otro, y eso no falla: solo etiqueta mal la
telemetría, en silencio.

## Sampling

Head sampling declarado explícito al 100% (`parentbased_traceidratio` con
`1.0`). Es el mismo comportamiento que el default del agente, pero visible:
bajar el muestreo es cambiar un número en el compose.

**Cuándo usaría cada uno.** *Head* decide al crear la traza, sin conocer su
desenlace: es barato, no necesita memoria y sirve cuando el volumen es alto y
las trazas son homogéneas. *Tail* decide después de ver la traza completa, así
que permite quedarse con el 100% de los errores y de las lentas y muestrear el
resto — que es casi siempre lo que se quiere en producción, a cambio de que el
Collector bufferee las trazas hasta poder decidir.

Acá se descartó tail por dos razones: descartar trazas choca con poder mostrar
una request concreta de punta a punta durante la demo, y su política útil depende
de que los errores estén marcados, que es precisamente lo que había que resolver
primero.

Hay además un argumento a favor de tail que aplica en producción: las variables
del SDK se leen al arrancar el proceso, así que cambiar el muestreo por
configuración exige reiniciar los pods. En el Collector se recarga sin tocar la
aplicación.

## Limitaciones conocidas

- **No hay ningún 5xx reproducible desde afuera.** Los tres
  `IllegalStateException` del código son violaciones de integridad que las
  invariantes del dominio impiden que ocurran. El escenario de error demostrable
  es un 4xx y, por convención, no pinta la traza de rojo: se identifica por
  `error.type` y por las métricas de status code.
- **`exported_instance` crece con cada reinicio del proceso** (ver
  Cardinalidad).
- **Storage en memoria en Jaeger y en Prometheus.** Reiniciar los contenedores
  borra la telemetría acumulada. Es deliberado: la stack existe para demostrar,
  no para retener.
- **Sin correlación de logs.** El agente ya pone `trace_id` y `span_id` en el
  MDC —se verificó—, así que alcanzaría con ajustar el patrón de log y agregar
  una pipeline de logs al Collector. Se dejó fuera por alcance.
- **Sin dashboards.** Las consultas de la sección "Dónde consultar" cubren
  tráfico, latencia y errores; el enunciado no exige dashboards.
- **Un solo servicio.** La propagación W3C entre procesos no se puede demostrar
  porque la API no hace llamadas salientes. Lo que sí se demuestra es la
  propagación a través de una frontera asincrónica, que es el caso análogo
  disponible.
