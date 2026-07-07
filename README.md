# Worker Java Reactivo para Procesamiento de Pedidos B2B

Este repositorio contiene la solución al caso práctico **Worker Java Reactivo para Procesamiento de Pedidos B2B**. La arquitectura está diseñada bajo los principios de la **Arquitectura Hexagonal (Ports & Adapters)**, utilizando un enfoque **100% Reactivo y No Bloqueante** para garantizar alta disponibilidad, tolerancia a fallos y observabilidad estructurada.

---

## 1. Diagrama de Arquitectura de la Solución

El ecosistema está compuesto por dos microservicios principales integrados mediante un clúster de mensajería asíncrona, soportado por capas de persistencia y caché distribuida:

```text
[ Cliente / Postman ]
         │
         ▼ (HTTP POST :3001)
┌────────────────────────────────────────────────────────────────────────┐
│ PRODUCER API (NestJS - Arquitectura Híbrida)                          │
│                                                                        │
│   [ AppController ] ──► (Inyección REST / orders/fire)                 │
│         │                                                              │
│         ▼                                                              │
│   [ ClientKafka ] ──► [ Tópico: orders-topic ]                         │
└─────────────────────────────┬──────────────────────────────────────────┘
                              │
                              ▼ (Asíncrono / No Bloqueante)
┌────────────────────────────────────────────────────────────────────────┐
│ ORDER WORKER (Java 21 + Spring WebFlux + Virtual Threads)               │
│                                                                        │
│  [ Inbound Adapter ]                                                   │
│    └─ OrderKafkaListener (Reactive Kafka Consumer)                     │
│               │                                                        │
│               ▼                                                        │
│  [ Application Service / Orchestrator ]                                │
│    └─ ProcessOrderService (Implementa ProcessOrderUseCase)             │
│         │                                                              │
│         ├─► [ Idempotencia ] Verifica existencia previa en MongoDB      │
│         │                                                              │
│         ▼ (Mono.zip Paralelo de Enriquecimiento)                        │
│  ┌────────────┴────────────┐                                           │
│  ▼                         ▼                                           │
│ [ Outbound Adapter HTTP ] [ Outbound Adapter HTTP ]                    │
│   ├─ ClientHttpAdapter      ├─ ProductHttpAdapter                      │
│   ├─ Retry (clientsApi)     ├─ Retry (productsApi)                     │
│   └─ CircuitBreaker         └─ CircuitBreaker                          │
│  └────────────┬────────────┘                                           │
│               │ (Datos de Cliente e Ítems Encontrados)                 │
│               ▼                                                        │
│  [ Core Domain Service / Pure Logic ]                                  │
│    └─ OrderDomainService                                               │
│         ├─► calculateTax() -> Cálculo preciso de IVA (BigDecimal)      │
│         └─► buildOrder()   -> Construcción inmutable de la Orden       │
│               │                                                        │
│               ▼ (Orden Enriquecida y Calculada)                       │
│  [ Outbound Adapter DB / Cache ]                                       │
│    ├─ Redis Cache (Hash por ID con TTL Atómico)                        │
│    └─ MongoDB Adapter (Colección: enriched-orders)                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Decisiones Técnicas Relevantes

### A. Orquestación del Caso de Uso y Lógica de Dominio Pura
Siguiendo los principios de Diseño Guiado por el Dominio (DDD) y Arquitectura Hexagonal, `ProcessOrderService` actúa como el orquestador de la capa de aplicación, encargándose de validar la idempotencia y coordinar las llamadas asíncronas concurrentes mediante `Mono.zip`. Por otro lado, la lógica de negocio y cálculos financieros quedan completamente aislados en `OrderDomainService`, un servicio de dominio puro e inmutable que procesa los impuestos con `BigDecimal` y ensambla la entidad final sin acoplamiento con frameworks.

### B. Patrón Bulkhead y Precedencia de Resiliencia (Resilience4j)
Para evitar el antipatrón de *Destino Compartido de Fallos*, se aislaron por completo los contextos de resiliencia en dos instancias independientes en el YAML: `clientsApi` y `productsApi`. 
* **Precedencia de Operadores:** Se estructuró la cadena reactiva de adentro hacia afuera: el `RetryOperator` abraza directamente al `WebClient` para capturar fluctuaciones de red limpias y aplicar **Backoff Exponencial**. Si y solo si los 3 reintentos fallan de forma consecutiva, el error escala hacia el `CircuitBreakerOperator` para proteger estadísticamente el ecosistema y desviar la orden a la DLT de forma controlada (*Fail-Fast*).

### C. Observabilidad Estructurada y Diagnóstico de Resiliencia
La trazabilidad y auditoría del sistema aprovechan los logs asíncronos nativos integrados con las extensiones de Resilience4j. El registro de los reintentos (`onRetry` / `onError`) se configuró de forma atómica en el constructor de los adaptadores, aislando las trazas de comunicación de la red de Netty y Lettuce. Esto permite diagnosticar fallos de red en tiempo de ejecución sin comprometer la inmutabilidad de los flujos de datos ni sobrecargar el Event Loop con fugas de memoria.

### D. Idempotencia en Capa de Aplicación y Persistencia Inmutable
Para blindar al sistema ante reintentos de red a nivel de Kafka, el caso de uso realiza una validación de idempotencia consumiendo la query especializada `findByOrderId` mapeando el ID de negocio de la orden en lugar del `_id` nativo de MongoDB. Si la orden ya existe con estado `PROCESSED`, el pipeline omite la duplicidad de forma segura. El documento final en MongoDB filtra las propiedades mutables de negocio (como el canal de ventas del cliente), garantizando la inmutabilidad exigida.

---

## 3. Variables de Entorno Documentadas

### Microservicio: `order-worker` (Java)

| Variable de Entorno | Valor por Defecto | Descripción |
| :--- | :--- | :--- |
| `KAFKA_SERVERS` | `kafka:29092` | Dirección y puerto del clúster o bróker de Kafka. |
| `MONGODB_URI` | `mongodb://localhost:27017/b2b_orders` | URI de conexión para la base de datos de MongoDB. |
| `REDIS_HOST` | `redis` | Host de la instancia de caché Redis. |
| `REDIS_PORT` | `6379` | Puerto de la instancia de caché Redis. |
| `CACHE_TTL_SECONDS` | `300` | Tiempo de vida (TTL) atómico por cada llave en Redis. |
| `PRODUCTS_API_URL` | `http://products-api:8081` | URL base de la API externa de Productos (Go). |
| `CLIENTS_API_URL` | `http://clients-api:8082` | URL base de la API externa de Clientes (NestJS externa). |

### Microservicio: `producer-api` (NestJS)

| Variable de Entorno | Valor por Defecto | Descripción |
| :--- | :--- | :--- |
| `NODE_ENV` | `production` | Entorno de ejecución de la aplicación Node.js. |
| `KAFKA_SERVERS` | `kafka:29092` | Dirección y puerto del bróker de Kafka para la emisión de eventos. |
| `MONGODB_URI` | `mongodb://localhost:27017/b2b_orders` | URI de la base de datos para auditoría directa de persistencia. |
| `PORT` | `3001` | Puerto interno en el que NestJS levanta el servidor HTTP. |

---

## 4. Cómo Correr los Tests

Las pruebas unitarias y de integración del núcleo de la aplicación están blindadas contra condiciones de carrera mediante el uso de `StepVerifier` y planificadores inmediatos de Project Reactor.

### Ejecución de Pruebas en el Worker (Java)
Siguiendo las directrices del caso práctico, el proyecto incluye el Maven Wrapper para asegurar la portabilidad y homogeneidad en la ejecución de la suite de pruebas sin requerir una instalación local de Maven. Abre tu terminal en la carpeta `order-worker` y ejecuta:
```bash
cd order-worker
./mvnw test
```
---

## 5. Instrucciones de Despliegue con Docker Compose

Para levantar toda la infraestructura (Kafka KRaft, MongoDB, Redis) y los dos microservicios con un único comando, ejecuta lo siguiente en la raíz principal del proyecto:

```bash
# Construir imágenes Multi-stage y levantar contenedores en segundo plano
docker compose up --build -d
```

Puedes auditar que todos los servicios estén sanos y en ejecución con:
```bash
docker compose ps
```

---

## 6. Cómo Producir un Mensaje de Prueba y Validar la Solución

### Paso 1: Inyectar una Orden válida (Happy Path)
Envía una petición HTTP POST utilizando Postman o cURL al inyector de NestJS para publicar un evento en Kafka:

* **Método:** `POST`
* **URL:** `http://localhost:3001/orders/fire`
* **Headers:** `Content-Type: application/json`
* **Payload (JSON):**
```json
{
  "orderId": "ORD-2024-COL-00300",
  "clientId": "CLI-001",
  "channel": "B2B",
  "items": [
    {
      "productId": "PROD-001",
      "quantity": 10,
      "unitPrice": 150.00
    }
  ]
}
```

### Paso 2: Auditar los Logs del Worker de Java
Abre una terminal paralela para verificar cómo el Worker procesa el evento, ejecuta el cálculo de impuestos a través del servicio de dominio e imprime el flujo reactivo de forma impecable:
```bash
docker logs -f b2b-order-worker
```
### Paso 3: Verificar la Persistencia y Enriquecimiento en la BD (MongoDB)
Para auditar que el pipeline se completó correctamente, consume el endpoint de verificación de la `producer-api`. Este método consulta la colección de la base de datos, valida que los cálculos de IVA se hayan estructurado y comprueba las reglas de inmutabilidad de la orden:

* **Método:** `GET`
* **URL:** `http://localhost:3001/orders/check?orderId=ORD-2026-MARIPOSA-001`
* **Estructura de Respuesta Exitosa (200 OK):**
```json
{
  "status": "VERIFIED_IN_MONGODB",
  "message": "La orden fue procesada, enriquecida y persistida de forma correcta.",
  "databaseValidation": {
    "collection": "enriched-orders",
    "isChannelFieldOmitted": true
  },
  "document": {
    "_id": "ORD-2026-MARIPOSA-001",
    "orderId": "ORD-2026-MARIPOSA-001",
    "status": "PROCESSED",
    "client": {
      "clientId": "CLI-002",
      "name": "Comercializadora del Sur E.I.R.L.",
      "segment": "MAYORISTA",
      "taxRegime": "RESPONSABLE_IVA",
      "region": "Arequipa"
    },
    "items": [
      {
        "productId": "PROD-001",
        "name": "Gaseosa Inca Kola 3L",
        "sku": "GAS-INK-3L",
        "taxCategory": "GRAVADO",
        "quantity": 10,
        "unitPrice": 150.00,
        "subtotal": 1500.00,
        "taxRate": 0.19,
        "taxAmount": 285.00,
        "lineTotal": 1785.00
      }
    ],
    "summary": {
      "subtotal": 1500.00,
      "totalTax": 285.00,
      "grandTotal": 1785.00,
      "currency": "COP"
    },
    "processedAt": "2026-07-07T03:57:32.875Z"
  }
}
```

### Paso 4: Monitorear la Cola de Errores (Dead Letter Topic - DLT)
Para comprobar el flujo de resiliencia, apaga momentáneamente los contenedores de las APIs externas de negocio (`docker compose stop b2b-clients-api`) e inyecta una orden con un ID de prueba erróneo (ej. `CLI-ERROR`). Al agotarse los 3 intentos con backoff exponencial, la orden viajará a la DLT. Consúmela ejecutando:
```bash
docker exec -it mariposa-kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic orders-dlt --from-beginning
```