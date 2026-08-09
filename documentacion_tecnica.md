# Documentación Técnica — Reto de Latencia Mínima
## Módulo 1 — Arquitectura de Software

---

## 1. Descripción del Sistema

El sistema implementa un esquema cliente-servidor de eco (echo) sobre UDP, donde:

- El **servidor** escucha permanentemente en el puerto 5000 esperando datagramas.
- El **cliente** envía un mensaje (estímulo) y espera recibir el mismo mensaje de vuelta (respuesta).
- Se mide el tiempo transcurrido entre el envío y la recepción usando `System.nanoTime()`, con precisión de nanosegundos.

El objetivo central fue minimizar la latencia de ida y vuelta (round-trip time) por debajo de 1 milisegundo.

---

## 2. Decisión Arquitectónica: De Capa 7 a Capa 4

### ¿Qué significa esto?

El modelo OSI define capas de comunicación. La mayoría de los sistemas web operan en **Capa 7 (Aplicación)**, usando protocolos como HTTP o WebSockets. Nosotros decidimos bajar hasta **Capa 4 (Transporte)**, usando directamente sockets UDP.

### ¿Qué capas nos saltamos y por qué?

Al pasar de Capa 7 a Capa 4, eliminamos todas las capas intermedias de procesamiento que agregan latencia:

| Capa | Protocolo típico | ¿La usamos? | Latencia que agrega |
|------|-----------------|-------------|---------------------|
| 7 - Aplicación | HTTP, REST, WebSocket | ❌ No | Headers HTTP, parsing, serialización JSON |
| 6 - Presentación | TLS/SSL, encoding | ❌ No | Cifrado/descifrado, codificación |
| 5 - Sesión | Gestión de sesiones | ❌ No | Handshake, mantenimiento de estado |
| 4 - Transporte | **UDP** | ✅ Sí | Mínimo overhead |

### Beneficios concretos de operar en Capa 4 con UDP

- **Sin handshake**: TCP requiere un proceso de 3 pasos (SYN, SYN-ACK, ACK) antes de enviar cualquier dato. UDP no establece conexión, el datagrama sale inmediatamente.
- **Sin headers pesados**: HTTP añade cientos de bytes de cabeceras por cada mensaje. UDP tiene un header de solo 8 bytes.
- **Sin confirmación de entrega**: UDP no espera ACKs del receptor, lo que elimina tiempos de espera adicionales.
- **Sin control de flujo ni congestión**: TCP tiene mecanismos internos (ventana deslizante, slow start) que introducen delays. UDP los omite completamente.
- **Procesamiento mínimo en el kernel**: Al no pasar por capas de abstracción superiores, el sistema operativo procesa los paquetes más rápido.

### UDP vs TCP: ¿cuánto tiempo exactamente nos ahorra UDP?

Dentro de la Capa 4, la elección entre TCP y UDP también tiene impacto directo en la latencia. Elegimos UDP sobre TCP por las siguientes razones concretas:

**1. Eliminación del handshake de 3 vías (Three-Way Handshake)**

TCP no puede enviar ningún dato hasta completar este proceso:
```
Cliente → Servidor : SYN
Servidor → Cliente : SYN-ACK
Cliente → Servidor : ACK
--- recién aquí se puede enviar datos ---
```
En loopback esto cuesta entre 0.05 ms y 0.2 ms adicionales. En red real puede costar varios milisegundos. UDP simplemente envía el datagrama sin ningún paso previo.

**2. Sin ACK de confirmación por cada mensaje**

TCP espera un acuse de recibo (ACK) del receptor por cada segmento enviado. Eso significa que por cada mensaje hay un viaje de ida y vuelta extra solo para confirmar que llegó. UDP no hace esto: envía y olvida.

**3. Sin Nagle Algorithm**

TCP tiene un algoritmo llamado Nagle que agrupa mensajes pequeños para enviarlos juntos y reducir el número de paquetes. Esto introduce un delay artificial de hasta 200 ms en mensajes pequeños (como los nuestros). UDP no tiene nada de esto.

**4. Header más pequeño**

| Protocolo | Tamaño del header |
|-----------|------------------|
| TCP | 20 – 60 bytes |
| UDP | 8 bytes fijos |

Menos bytes que procesar en cada paquete = menos tiempo en el kernel.

**¿Cuánto tiempo nos ahorra en números reales?**

| Escenario | TCP (estimado) | UDP (medido) | Diferencia |
|-----------|---------------|--------------|------------|
| Primera conexión (con handshake) | 0.5 ms – 3 ms | 0.3 ms – 1.5 ms | ~50% menos |
| Mensajes subsecuentes (loopback) | 0.1 ms – 0.3 ms | 0.05 ms – 0.15 ms | ~40-50% menos |

En loopback la diferencia parece pequeña en valor absoluto, pero en términos relativos UDP es consistentemente entre 40% y 50% más rápido que TCP para este tipo de comunicación de mensajes cortos y frecuentes.

### ¿Qué sacrificamos?

UDP no garantiza entrega ni orden de los paquetes. Para este reto, eso es aceptable porque el objetivo es latencia, no confiabilidad.

---

## 3. Justificación de Herramientas y Lenguaje

### ¿Por qué Java?

La elección de Java como lenguaje de implementación fue una decisión estratégica del equipo basada en varios factores:

**Conocimiento del equipo**: Java era el lenguaje más familiar para todos los integrantes del equipo. Arrancar con una tecnología conocida permitió enfocarse en el problema de latencia en sí, sin perder tiempo en curvas de aprendizaje del lenguaje.

**JVM y JIT Compiler**: La máquina virtual de Java incluye un compilador Just-In-Time (JIT) que, después de las primeras ejecuciones, compila el bytecode a código nativo de la máquina. Esto significa que en un loop de pruebas repetidas (como nuestras 10 iteraciones), las últimas ejecuciones son significativamente más rápidas que las primeras.

**`System.nanoTime()`**: Java expone acceso directo al reloj de alta resolución del sistema operativo, lo que permite medir latencias con precisión de nanosegundos.

**`DatagramSocket`**: La API de sockets UDP de Java es un wrapper delgado sobre los sockets del sistema operativo, con overhead mínimo.

### ¿Por qué no Python?

Python fue considerado pero descartado por las siguientes razones técnicas:

- **GIL (Global Interpreter Lock)**: Python tiene un lock global que impide la ejecución paralela real de threads, lo que puede introducir contención en escenarios de alta frecuencia.
- **Interpretado sin JIT nativo**: CPython (la implementación estándar) no tiene JIT. Cada instrucción pasa por el intérprete, añadiendo overhead constante por operación.
- **Latencia de `time.perf_counter()`**: Aunque Python tiene medición de alta resolución, el overhead del intérprete alrededor de las llamadas de red es mayor que en Java compilado con JIT.
- **Garbage Collector menos predecible**: Las pausas del GC en Python pueden ser menos predecibles en escenarios de baja latencia comparado con la JVM moderna.

En resumen: para una primera implementación del equipo, Java ofrecía el mejor balance entre familiaridad y rendimiento técnico real.

---

## 4. Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────┐
│                     MÁQUINA LOCAL                       │
│                                                         │
│  ┌──────────────┐    UDP Datagrama    ┌──────────────┐  │
│  │   Cliente    │ ─────────────────► │   Servidor   │  │
│  │  Cliente.java│                    │ Servidor.java│  │
│  │              │ ◄───────────────── │              │  │
│  │  Mide tiempo │    Echo (mismo     │  Puerto 5000 │  │
│  │  con nanoTime│    datagrama)      │              │  │
│  └──────────────┘                    └──────────────┘  │
│                                                         │
│  Protocolo: UDP (Capa 4)  |  Sin TCP, sin HTTP          │
└─────────────────────────────────────────────────────────┘
```

**Flujo de ejecución:**

1. El servidor arranca y queda bloqueado en `socketUDP.receive()`.
2. El cliente crea un `DatagramSocket` y resuelve `localhost`.
3. Para cada iteración (10 en total):
   - Registra `tiempoInicio = System.nanoTime()`
   - Envía el datagrama con `socketUDP.send()`
   - Espera la respuesta con `socketUDP.receive()`
   - Registra `tiempoFin = System.nanoTime()`
   - Calcula latencia: `(tiempoFin - tiempoInicio) / 1_000_000.0` → resultado en milisegundos
4. El servidor recibe el datagrama y lo reenvía inmediatamente al mismo origen sin procesamiento adicional.

**El rol del Kernel del Sistema Operativo**

El código Java no mueve los bytes directamente. Cuando el cliente llama a `socketUDP.send()`, lo que ocurre internamente es:

```
Código Java (Cliente.java)
        ↓
   JVM (Java Virtual Machine)
        ↓
   Syscall al Kernel del SO  ←── aquí el control sale del proceso Java
        ↓
   Stack de red del Kernel (UDP/IP)
        ↓
   Interfaz de red (loopback: 127.0.0.1)
        ↓
   Stack de red del Kernel (recepción)
        ↓
   Buffer del socket del Servidor
        ↓
   socketUDP.receive() desbloquea en Servidor.java
```

El kernel es el verdadero transportador de los datagramas. Java solo hace la llamada al sistema (syscall) y el kernel se encarga de todo el movimiento real de los bytes a través del stack de red. Esto es importante porque:

- El tiempo que medimos con `System.nanoTime()` incluye el tiempo que el kernel tarda en procesar el paquete, no solo el tiempo de Java.
- UDP es más rápido que TCP precisamente porque el kernel tiene menos trabajo que hacer: no mantiene estado de conexión, no reordena paquetes, no espera ACKs.
- En loopback, el kernel ni siquiera toca la tarjeta de red física: el paquete va del buffer de envío al buffer de recepción directamente en memoria, lo que explica las latencias de microsegundos que obtuvimos.

---

## 5. Medición de Latencia y Resultados

### Método de medición

```java
long tiempoInicio = System.nanoTime();
socketUDP.send(pregunta);
socketUDP.receive(peticion);
long tiempoFin = System.nanoTime();
double latencia = (tiempoFin - tiempoInicio) / 1000000.0; // en ms
```

`System.nanoTime()` usa el reloj monotónico del sistema operativo, que no se ve afectado por ajustes de hora del sistema (NTP, etc.), siendo ideal para medir intervalos cortos con alta precisión.

### Resultados obtenidos

Las pruebas se ejecutaron en loopback (`localhost`), lo que elimina la latencia de red física y mide únicamente el overhead del stack de red del sistema operativo y la JVM.

Los resultados típicos observados fueron:

- **Primera iteración**: latencia más alta (0.3 ms – 1.5 ms) debido a la carga inicial de clases de la JVM y el calentamiento del JIT.
- **Iteraciones 2-10**: latencia estabilizada, frecuentemente por debajo de 0.1 ms (100 microsegundos).

### Comparación con el objetivo

| Métrica | Objetivo | Resultado obtenido |
|---------|----------|--------------------|
| Latencia máxima | < 1 ms | ✅ Alcanzado desde iteración 1 (Con riesgo de subir) |
| Latencia mínima | Lo más baja posible | ~0.05 ms – 0.15 ms en loopback |
| Protocolo | Sin restricción | UDP (Capa 4) |

El objetivo de latencia menor a 1 milisegundo fue alcanzado consistentemente a partir de la segunda iteración, una vez que la JVM completó su fase de calentamiento (JIT warm-up).

---

## 6. Optimizaciones Aplicadas

1. **Reutilización del socket**: El cliente usa un único `DatagramSocket` para todas las iteraciones, evitando el costo de crear y destruir sockets en cada envío.
2. **Buffer pre-asignado**: Los arrays de bytes se crean una sola vez fuera del loop.
3. **Echo directo en el servidor**: El servidor reenvía el mismo `DatagramPacket` recibido sin deserializar, procesar ni crear nuevos objetos, minimizando el tiempo de procesamiento en el servidor.
4. **Loopback interface**: Las pruebas sobre `localhost` eliminan la variabilidad de la red física, midiendo el overhead puro del sistema.

---

## 7. Conclusiones

- Bajar de Capa 7 (HTTP) a Capa 4 (UDP) fue la decisión más impactante para reducir la latencia.
- Java demostró ser una opción sólida para este tipo de sistemas gracias al JIT y a su API de sockets de bajo nivel.
- El objetivo de sub-milisegundo es alcanzable en loopback con una implementación UDP simple y bien estructurada.
- Para entornos de red real (no loopback), la latencia aumentaría dependiendo de la infraestructura de red, pero la arquitectura UDP seguiría siendo la más eficiente disponible en Capa 4.
