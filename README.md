# Challenge técnico

Gestión de pedidos y preguntas de compradores, con priorización automática de
las preguntas sin resolver y notificación al vendedor cuando una consulta es
grave.

Las decisiones de diseño y el contrato de la API están en
[DECISIONS.md](DECISIONS.md). Este README cubre cómo correr el proyecto y qué
hallazgos dejó el desarrollo.

> Pendiente de completar: puesta en marcha del frontend y guía de uso de la API.

## Backend

Requiere JDK 17+ y Gradle (el proyecto no incluye wrapper).

```bash
cd backend
gradle bootRun     # levanta la API en http://localhost:8080
gradle test        # corre la suite completa
```

El arranque demora entre 40 y 55 segundos. La carga de datos de arranque se
controla con `app.seed.enabled` en `application.properties`.

### Tests

180 tests, sin dependencia del contexto de Spring salvo el `contextLoads` del
scaffold. Cubren el scoring factor por factor y en los bordes de cada brecha,
las transiciones de estado (con foco en las prohibidas), las invariantes del
dominio, la derivación de la prioridad del pedido y el despacho de
notificaciones.

Los tests arman sus propios datos mediante builders y no usan el seed: el seed
existe para la demo, y acoplarse a él haría que un cambio de dataset rompa
tests sin razón. Donde interviene el tiempo se usa un `Clock` fijo.

La salida por consola informa cada test y cierra con un resumen. Gradle cachea
la tarea, así que para volver a correrla sin cambios de por medio hay que usar
`gradle test --rerun-tasks`.

El criterio de qué se testea y qué no, y por qué, está en
[DECISIONS.md](DECISIONS.md), sección "Testing".

### Notas de entorno

Dos cosas que no se deducen del código y hacen perder tiempo:

- **El proyecto no incluye Gradle wrapper.** `./gradlew` falla; hay que invocar
  `gradle` directamente.
- **Si el puerto 8080 responde con datos que no se corresponden con el código,**
  suele haber un `bootRun` anterior todavía vivo. Conviene verificar con
  `netstat -lptn | grep 8080` antes de diagnosticar el problema en el código:
  el proceso viejo sirve las clases con las que arrancó y, como el store es en
  memoria, también el estado que haya mutado desde entonces.

## Hallazgos

### Una lista inmutable hacía fallar la validación de invariantes con NPE

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
es el caso que fallaba.

Vale como observación sobre el valor de los tests: el caso no se encontró
revisando el código ni ejercitando la API, sino al construir las entidades
desde afuera con datos propios.

## Detectado y no resuelto

- **El techo declarado de las palabras clave no coincide con el configurado.**
  DECISIONS.md fija un techo de 50 puntos para el factor de keywords, pero el
  diccionario por defecto tiene diez palabras de 10 puntos cada una y el scorer
  no aplica tope, así que el máximo real es 100. En la práctica no se alcanza
  (requeriría una pregunta con las diez palabras), pero el techo declarado y el
  efectivo difieren. Queda pendiente definir si corresponde acotar el factor o
  ajustar lo declarado.

El resto de los puntos detectados y no resueltos, junto con las ambigüedades
abiertas, está en [DECISIONS.md](DECISIONS.md).
