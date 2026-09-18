# Gestión de pedidos y preguntas

Un vendedor que recibe muchas preguntas de compradores no tiene forma de saber
cuál atender primero: la bandeja llega ordenada por fecha, y esa fecha no dice
nada sobre qué hay en juego detrás de cada consulta. Un reclamo por un televisor
de $620.000 cancelado hace una semana y una duda sobre el color de una mochila
pesan lo mismo en una lista cronológica. El costo de equivocarse no es parejo:
la consulta grave que queda al fondo es la que termina en una cancelación o en
un reclamo formal.

El sistema resuelve eso puntuando cada pregunta sin resolver con cinco factores
—tiempo de espera, palabras clave del texto, monto del pedido, estado del pedido
y estado de la pregunta—, y traduciendo ese total a una clasificación (`LOW`,
`MEDIUM`, `HIGH`, `CRITICAL`). Con eso se arma una cola de Operaciones ordenada
por importancia, y el pedido hereda como prioridad la más alta de sus preguntas
abiertas. Cuando una pregunta nace clasificada `HIGH` o más, se dispara un aviso
al vendedor por los canales activos. Los pesos, los tramos y el umbral de aviso
viven en `application.properties`, no en el código: ajustar la política de
priorización no requiere recompilar ni tocar una clase.

El detalle de por qué cada decisión se tomó así está en
[DECISIONS.md](DECISIONS.md). Este README cubre cómo correrlo y cómo verificarlo.

> **Anexo de observabilidad.** La instrumentación con OpenTelemetry —trazas,
> métricas, Collector, Jaeger y Prometheus— se documenta aparte en
> **[OBSERVABILITY.md](OBSERVABILITY.md)**. Con esa stack todo el sistema
> levanta con un solo `docker compose up -d --build`, sin necesidad de JDK ni
> Gradle en la máquina.

## Stack

- **Backend:** Java 17, Spring Boot 3.2.5, Gradle. Persistencia en memoria
  (`ConcurrentHashMap`), sin base de datos.
- **Frontend:** React 19 con Vite, sin librería de estado ni de routing.
- **Tests:** JUnit 5 y Mockito (vía `spring-boot-starter-test`).

```
backend/src/main/java/com/hackerrank/challenge/
  api/            controllers, DTOs y el handler de errores centralizado
  application/    services, eventos, despacho de notificaciones
  domain/         entidades, enums con sus transiciones, reglas de scoring
  infrastructure/ config, canales de notificación, repositorios, seed
backend/src/main/resources/
  application.properties   pesos, umbrales, flags de canal y de seed
  seed/seed-data.json      datos de demo
frontend/src/
  api/            cliente HTTP, etiquetas, vendedores del seed
  components/     detalle de pedido, filtros, cambio de estado, chat, alta, UI
  views/          SellerView (vendedor) y OpsView (cola de Operaciones)
```

## Puesta en marcha

Requiere JDK 17+, Gradle y Node. **El proyecto no incluye Gradle wrapper:**
`./gradlew` falla, hay que invocar `gradle` directamente desde `backend/`.

**Backend** (API en el puerto 8080):

```bash
cd backend
gradle bootRun
```

**Frontend** (puerto 3000), en otra terminal:

```bash
cd frontend
npm install
npm run client
```

El script del frontend es `npm run client`, no `npm run dev`. Desde
`frontend/` también está `npm start`, que levanta backend y frontend juntos con
`concurrently`.

El arranque del backend demora entre 40 y 55 segundos.

**Sobre la URL del backend:** el frontend no apunta a `localhost:8080` fijo. La
deriva del host del navegador reemplazando el puerto del frontend por el del
backend ([frontend/src/api/client.js](frontend/src/api/client.js)), porque en un
entorno con proxy la página se sirve desde un host generado por sesión
(`vm-xxx-3000...`) donde `localhost` es la máquina del navegador y no la que
corre la API. Detrás del proxy el puerto viaja en el nombre del host, así que el
reemplazo se hace sobre el hostname. `VITE_API_BASE_URL` funciona como override.
Los orígenes habilitados por CORS son patrones por la misma razón
(`app.cors.allowed-origin-patterns`).

> Si el puerto 8080 responde con datos que no se corresponden con el código,
> suele haber un `bootRun` anterior todavía vivo. Verificarlo con
> `netstat -lptn | grep 8080` antes de buscar el problema en el código: el
> proceso viejo sirve las clases con las que arrancó y, como el store es en
> memoria, también el estado que haya mutado desde entonces.

## La aplicación

Son dos vistas, sin router: el header tiene un selector de **Vista**
(*Vendedor* / *Operaciones*) y, solo cuando se está en la del vendedor, un
selector de **Vendedor** con los dos del seed. Ese segundo selector no aparece
en Operaciones porque esa cola es global y cruza vendedores por definición. Los
vendedores están hardcodeados en [frontend/src/api/sellers.js](frontend/src/api/sellers.js)
como consecuencia directa de no tener login: en un sistema real el `sellerId`
saldría del token de sesión, no de una lista que el frontend conoce. Cambiar de
vista o de vendedor cierra el pedido que estuviera abierto, porque el detalle se
resuelve dentro del vendedor de la ruta y uno ajeno respondería 404.

La **vista del vendedor** abre en el listado de sus pedidos, con fecha,
comprador, estado, total, una columna de preguntas que dice *Sin responder* o
nada —es un "tenés algo que contestar", no un contador— y la prioridad heredada
de sus preguntas abiertas. Arriba hay filtros que combinan con AND: estado como
checkboxes (los cinco, ninguno tildado significa todos), rango de fechas y
búsqueda por comprador sobre nombre o email, esta última con un retardo de 300 ms
para no disparar una request por tecla. No hay botón de aplicar, y "Limpiar
filtros" se habilita solo si hay alguno puesto. Al abrir un pedido se ve su UUID
completo —ahí sirve para copiarlo a un curl—, los datos del comprador, la tabla
de items con subtotales y total, y la conversación. Ahí también puede **cambiar
el estado del pedido**: el selector ofrece únicamente las transiciones que el
estado actual admite, y en un pedido terminal (`DELIVERED` o `CANCELLED`) no se
muestra, porque un control que no puede hacer nada es peor que su ausencia. Ese
mapa no decide nada: el backend sigue validando y respondiendo 409, y acá solo
se evita ofrecer opciones que ya se sabe que serían rechazadas. Sobre cada
pregunta el vendedor puede **responder** si está `OPEN` o **marcarla como
resuelta** si ya está `ANSWERED`: nunca las dos a la vez, porque son acciones
secuenciales. Al pie
hay un formulario de alta de preguntas rotulado explícitamente como *simulación
del comprador*, que existe para poder demostrar el disparo de notificaciones sin
recurrir a curl; el selector de producto ofrece solo los items de ese pedido,
porque preguntar por uno ajeno sería un 400.

La **vista de Operaciones** muestra la cola de preguntas sin resolver (`OPEN` y
`ANSWERED`) de todos los vendedores, en el orden que devuelve el backend: por
score descendente y, ante empate, la más antigua primero. El frontend no reordena
nada, porque ese criterio es una regla de negocio y vive donde se calcula el
score. Cada fila trae el texto de la pregunta, el vendedor con el estado y el
monto del pedido —dos de los cinco factores, a la vista para no tener que abrir
el detalle solo para entender el puntaje—, el score y la prioridad. "Ver
desglose" despliega los cinco factores con su aporte y el total, colapsado por
defecto y por fila. Hay un filtro por vendedor propio de esta cola (con la opción
*Todos*), que vive acá y no en el header justamente porque significa otra cosa
que el del vendedor. La cola se **refresca sola cada 30 segundos**, de forma
silenciosa —sin parpadear el indicador de carga por algo que nadie pidió— y con
una marca de "Actualizado a las hh:mm" para saber si el dato es de recién o
quedó viejo por un fallo; si el refresco falla, la lista no se vacía, porque lo
que está en pantalla se consultó bien. "Ver pedido" abre el mismo componente de
detalle pero **en modo lectura**: se ve la conversación completa, sin cambio de
estado, sin acciones de responder ni resolver y sin el formulario de alta. La
diferencia no está en el componente sino en el permiso: solo el vendedor
gestiona el pedido y contesta las dudas del comprador.

## Datos de arranque

Al levantar, un `CommandLineRunner` lee
[backend/src/main/resources/seed/seed-data.json](backend/src/main/resources/seed/seed-data.json)
y carga 2 vendedores, 7 productos, 16 pedidos y 12 preguntas. Los pedidos y las
preguntas no se insertan con su estado forzado: el loader los construye en el
estado inicial y recorre las transiciones reales hasta el estado objetivo, de
modo que el seed no puede producir datos que las reglas del dominio rechazarían.
Las fechas son relativas al arranque (`offsetDays`, `offsetHours`), así que el
tiempo de espera de las preguntas es representativo en cualquier momento.

Para levantar sin datos precargados, poner en
[backend/src/main/resources/application.properties](backend/src/main/resources/application.properties):

```properties
app.seed.enabled=false
```

El bean del seed es condicional a ese flag, así que en `false` no se carga nada.
Como el store es en memoria, reiniciar el backend vuelve al estado del seed.

## Endpoints

Todos los ejemplos usan IDs reales del seed y funcionan tal cual contra un
backend recién levantado.

| Método | Ruta | Parámetros |
|---|---|---|
| GET | `/api/sellers/{sellerId}/orders` | `status` (repetible), `from`, `to` (ISO `yyyy-MM-dd`), `buyer` (máx. 120) |
| GET | `/api/sellers/{sellerId}/orders/{orderId}` | — |
| PATCH | `/api/sellers/{sellerId}/orders/{orderId}/status` | body: `{"status": "..."}` |
| POST | `/api/orders/{orderId}/questions` | body: `{"productId": ..., "questionText": ...}` (`productId` opcional) |
| POST | `/api/questions/{questionId}/answer` | body: `{"answerText": ...}` |
| PATCH | `/api/questions/{questionId}/resolve` | sin cuerpo |
| GET | `/api/ops/questions/unresolved` | `sellerId` (opcional) |

Pedidos del vendedor, con filtros combinables con AND:

```bash
curl "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders?status=PENDING&status=CONFIRMED"

curl "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders?buyer=lucia"
```

Detalle del pedido, con sus líneas y sus preguntas:

```bash
curl "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders/30000000-0000-0000-0000-000000000006"
```

Avanzar el estado de un pedido (`30000000-...-000000000001` está en `PENDING`):

```bash
curl -X PATCH "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders/30000000-0000-0000-0000-000000000001/status" \
  -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

Crear una pregunta sobre un pedido (responde 201 con el id):

```bash
curl -X POST "localhost:8080/api/orders/30000000-0000-0000-0000-000000000010/questions" \
  -H "Content-Type: application/json" \
  -d '{"productId":"20000000-0000-0000-0000-000000000006","questionText":"Viene con garantia?"}'
```

Responder y resolver (`40000000-...-000000000005` está en `OPEN`; responder una
ya respondida o resolver una no respondida devuelve 409):

```bash
curl -X POST "localhost:8080/api/questions/40000000-0000-0000-0000-000000000005/answer" \
  -H "Content-Type: application/json" \
  -d '{"answerText":"El logo viene bordado."}'

curl -X PATCH "localhost:8080/api/questions/40000000-0000-0000-0000-000000000005/resolve"
```

Cola de Operaciones, global o filtrada por vendedor. Trae las preguntas `OPEN` y
`ANSWERED` de todos los vendedores, ordenadas por importancia y, ante empate, la
más antigua primero, cada una con su score y su desglose:

```bash
curl "localhost:8080/api/ops/questions/unresolved"

curl "localhost:8080/api/ops/questions/unresolved?sellerId=10000000-0000-0000-0000-000000000001"
```

## Demostrar la notificación

El umbral es `app.notifications.minimum-priority=HIGH`: se avisa desde esa
clasificación hacia arriba, evaluada al crear la pregunta. El único canal activo
por defecto es el email (`app.notifications.channels.email.enabled=true`); slack
y sms están en `false` y ni siquiera existen como bean.

Esta pregunta suma 115 puntos y clasifica `HIGH`: el pedido
`30000000-...-000000000006` está `CANCELLED` (30) y vale $620.000 (40), la
pregunta nace `OPEN` (15) y el texto trae tres palabras clave —*devolucion*,
*urgente*, *reclamo*— (30). El tiempo de espera aporta 0 por ser recién creada.

```bash
curl -X POST "localhost:8080/api/orders/30000000-0000-0000-0000-000000000006/questions" \
  -H "Content-Type: application/json" \
  -d '{"productId":"20000000-0000-0000-0000-000000000003","questionText":"Necesito la devolucion urgente, esto es un reclamo"}'
```

El aviso se ve en la terminal donde corre `gradle bootRun`:

```
[EMAIL] Aviso al vendedor 10000000-... : la pregunta ... del pedido ... se clasifico HIGH (115 puntos).
```

El envío es asíncrono, así que el log aparece un instante después de la
respuesta HTTP. Una pregunta que no llega al umbral no imprime nada en `INFO`
(queda en `DEBUG`).

## Reproducir errores

**Transición inválida (409).** El pedido `30000000-...-000000000006` está
`CANCELLED`, que es terminal:

```bash
curl -X PATCH "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders/30000000-0000-0000-0000-000000000006/status" \
  -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED"}'
```

```json
{"statusCode":409,"description":"No se puede pasar el pedido de CANCELLED a SHIPPED.","errors":[]}
```

**Rango de fechas invertido (400).** No es una búsqueda sin resultados, es una
consulta mal armada, y se valida a mano porque involucra dos campos:

```bash
curl "localhost:8080/api/sellers/10000000-0000-0000-0000-000000000001/orders?from=2026-09-10&to=2026-09-01"
```

```json
{"statusCode":400,"description":"La fecha desde no puede ser posterior a la fecha hasta.","errors":[]}
```

Todos los errores salen con el mismo cuerpo (`statusCode`, `description`,
`errors`), así que el frontend no contempla dos formas distintas de error.

## Testing

```bash
cd backend
gradle test
```

**183 tests, todos verdes.** Ninguno levanta el contexto de Spring salvo el
`contextLoads` que venía con el scaffold: las reglas que importan son lógica
propia y se ejercitan instanciando las clases. Cubren el scoring factor por
factor y en los bordes de cada tramo, las transiciones de pedido y de pregunta
(con foco en las prohibidas), las invariantes del dominio, los services, la
derivación de la prioridad del pedido, el despacho de notificaciones y la
validación del rango de fechas del controller.

Los tests arman sus propios datos con builders (`TestData.anOrder()`) y no usan
el seed: el seed existe para la demo, y acoplarse a él haría que un cambio de
dataset rompa tests sin que haya cambiado ninguna regla. La configuración de
scoring se declara en el test en vez de leerse de `application.properties`, para
que un fallo no sea ambiguo entre "cambió la regla" y "cambió la config". Donde
interviene el tiempo se usa un `Clock` fijo y se envejece la pregunta moviendo
el reloj, no esperando.

La salida por consola informa cada test y cierra con un resumen; está
configurada en [backend/build.gradle](backend/build.gradle). Gradle cachea la
tarea, así que para volver a correrla sin cambios de por medio hay que usar
`gradle test --rerun-tasks`.

### Verificación por mutación deliberada

Un test que pasa no distingue entre verificar y acompañar. Para las dos
afirmaciones centrales —que la prioridad del pedido es el **máximo** de sus
preguntas abiertas y no la suma, y que el desempate de la cola es por
antigüedad— se mutó a propósito el código productivo (`max` por `min`, y
quitando el criterio de desempate) y se confirmó que la suite los detecta.

### Hallazgo: una lista inmutable hacía fallar la validación con NPE

Escribir los tests del dominio expuso un defecto en el constructor de `Order`.
La guarda contra líneas nulas estaba escrita así:

```java
if (lines.contains(null)) {
    throw new DomainValidationException("El pedido no puede tener lineas vacias.");
}
```

Sobre una lista inmutable —`List.of(...)`, que es la forma natural de construir
un pedido— `contains(null)` **lanza `NullPointerException`** en lugar de
responder `false`: las colecciones inmutables de Java rechazan el `null` como
argumento de búsqueda. Con `ArrayList`, `Arrays.asList` o `Stream.toList()` el
mismo código funciona sin problemas.

El efecto era que el constructor explotaba antes de terminar de validar, y el
handler centralizado reportaba como `500` (error del sistema) lo que debía ser
un `400` (dato inválido). El seed no lo exponía porque arma las líneas con
`.toList()`, de modo que el defecto solo aparecía por el camino que ningún
código existente recorría todavía.

La corrección recorre la lista en vez de buscar dentro de ella:

```java
if (lines.stream().anyMatch(Objects::isNull)) {
```

Quedó cubierto por dos tests: uno que verifica que una línea nula se rechaza
como dato inválido, y otro que construye un pedido con una lista inmutable, que
es el caso que fallaba. Vale como observación sobre el valor de los tests: el
caso no se encontró revisando el código ni ejercitando la API, sino al
construir las entidades desde afuera con datos propios.

## Decisiones principales

El detalle y el razonamiento completo de cada una está en
[DECISIONS.md](DECISIONS.md).

- **El scoring es configuración, no código.** Pesos, tramos y umbrales se
  declaran en `application.properties` y se validan al arrancar. Cambiar la
  política de priorización no recompila nada.
- **Las palabras clave tienen techo** (`keyword-max-points=50`): sin tope, un
  diccionario amplio volvería ese factor dominante por encima del tiempo de
  espera, que llega a 60.
- **Las transiciones viven en el enum**, con el mapa de las permitidas. Ningún
  estado admite ir hacia sí mismo: pedir el estado actual es una solicitud
  incorrecta (409), no un no-op idempotente.
- **El vendedor es parte de la ruta de pedidos** por ser una entidad del modelo,
  y un pedido de otro vendedor responde 404. La cola de Operaciones, en cambio,
  es global por definición y el `sellerId` es un filtro opcional.
- **Un canal de notificación es un bean condicional a su flag.** Agregar uno no
  obliga a tocar los existentes ni el código que los usa, y un canal que falla
  se loguea sin tumbar a los demás.
- **El aviso se dispara por evento asíncrono**, para no atar el tiempo de
  respuesta de crear una pregunta al de los canales.
- **Todos los errores comparten un cuerpo único**, emitido por un handler
  centralizado.

## Alcance excluido

Declarado a propósito, no omitido: autenticación (y por lo tanto aislamiento de
datos entre vendedores), multi-tenancy, paginación, devoluciones, hilo real de
mensajes dentro de una pregunta, reevaluación periódica del score, creación de
pedidos por endpoint (los pedidos nacen solo del seed), categorización de
vendedores por métricas operacionales, eviction del store en memoria y
resiliencia de notificaciones (reintentos, backpressure, DLQ).

Los puntos detectados y no resueltos, con su propuesta a futuro, y las
ambigüedades detectadas y cómo se resolvieron están en
[DECISIONS.md](DECISIONS.md).

## Nota sobre los scripts de package.json

Dos scripts de [frontend/package.json](frontend/package.json) no son del
proyecto y quedaron intactos a propósito:

- **`posttest`** mueve un archivo de resultados llamado
  `TEST-com.hackerrank.corebanking.controller.AccountControllerTest.xml`, que
  pertenece a otro proyecto (*corebanking*) y no existe acá. Parece parte del
  harness de la plataforma para recolectar resultados, así que no se tocó;
  termina en `|| true` y no rompe el build.
- **`test-frontend`** sigue siendo el placeholder original
  (`echo 'Replace with your frontend test command'`). **No hay tests de
  frontend:** el esfuerzo de testing se puso en las reglas de negocio del
  backend, que es donde vive la lógica del challenge.

En consecuencia, `npm test` desde `frontend/` ejecuta el placeholder y después
la suite real del backend (`gradle test`).
