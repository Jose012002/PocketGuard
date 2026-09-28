# Decisiones técnicas

Registro de decisiones tomadas donde la especificación era ambigua o inviable (ver `SPEC.md`, sección 0, punto 9).

## Fase 1 — Proyecto base

| # | Decisión | Motivo |
|---|---|---|
| D-01 | AGP 9.3.2 y Gradle 9.5.0 | Son los que ya usa el Android Studio instalado y están en caché local |
| D-02 | Kotlin integrado de AGP 9 (sin plugin `kotlin-android`) | AGP 9 lo trae por defecto; solo se aplica el plugin del compilador de Compose |
| D-03 | `compileSdk` y `targetSdk` = 37 | Es la plataforma estable más reciente instalada en el SDK local (RNF-09) |
| D-04 | `applicationId` y paquete `com.equipo.pocketguard` | Es el paquete de la sección 7.3. Se puede renombrar cuando el equipo defina su identificador |
| D-05 | `allowBackup=false` y reglas de extracción que excluyen todo | El hash del PIN y la configuración no deben salir del dispositivo (RNF-07) |

## Fase 2 — Paquete `decision`

| # | Decisión | Motivo |
|---|---|---|
| D-06 | Los `AvailableSensors` viajan dentro de `Arming`, `Stored` y `Suspicion` (segunda alternativa de la sección 7.4) | El motor queda como reducer puro y sin estado propio; si el sistema reinicia el servicio no se pierde información |
| D-07 | `SensorSnapshot` se crea en `processing/` desde esta fase | `GuardInput.Snapshot` lo necesita. Es Kotlin puro, así que `decision` puede depender de él. El test de arquitectura solo permite ese import del proyecto |
| D-08 | Un `Snapshot` que llega con la ventana ya vencida (sin `Tick` previo) no puede completar la detección: se pasa a `Stored` y se evalúa como tal, lo que reabre la sospecha por nivel si algún indicador sigue activo | Los ticks llegan cada 100 ms; sin esto, un snapshot tardío podría completar una detección con indicadores repartidos en más tiempo que la ventana |
| D-09 | `Stored → Suspicion` y `Suspicion → PreAlarm/Alarm` nunca ocurren en el mismo snapshot | Se sigue la tabla 6.4 al pie de la letra. Con el acelerómetro a `SENSOR_DELAY_UI` el siguiente snapshot llega en decenas de ms |
| D-10 | `Disarm` estando en `Disarmed` se ignora, tanto con `authOk` verdadero como falso | La tabla 6.4 dice "Cualquiera" para `authOk = false`, pero registrar `AUTH_FAILED` sin nada armado no tiene sentido y nunca ocurre en la práctica |
| D-11 | Los `Log(...)` del motor no llevan `detail` | El servicio ya registra estado anterior, nuevo y motivo por cada transición (RNF-15) |
| D-12 | Kover mide solo el paquete `decision` | RNF-10 exige 90 % ahí. `./gradlew koverVerify` falla si baja de ese umbral; en la fase 11 se puede ampliar el reporte |
| D-13 | `DetectionConfig` incluye `validate()`, `coerced()`, `withProfile()` y `matchingProfile()` | Ajustes (fase 10) necesita validar rangos y aplicar perfiles sin duplicar la lógica en la UI |

## Fase 3 — Paquete `processing`

| # | Decisión | Motivo |
|---|---|---|
| D-14 | Se define `SensorReading` (Proximity, Light, Acceleration) en `processing`; `capture` convertirá cada `SensorEvent` a este tipo, con `elapsedRealtimeNanos()` como marca de tiempo | Deja `SignalProcessor` en Kotlin puro y testeable sin Android, y cumple RNF-11 (capas conectadas por tipos de datos). La sección 8.1 habla de `Flow<SensorEvent>`, y el mapeo ocurre en el borde de `capture` |
| D-15 | `SignalProcessor` recibe `ProcessingConfig` (3 umbrales de luz) y `SensorHardware` (rango de proximidad, luz, aceleración lineal) en vez de `DetectionConfig` | Evita un ciclo de dependencias `decision ↔ processing`. El servicio construye `ProcessingConfig` a partir de `DetectionConfig` |
| D-16 | Con el sensor presente pero sin ninguna lectura aún, el snapshot dice "lejos" y "claro" (`isNear = false`, `isDark = false`) | Nunca se adelanta la condición de bolsillo al armar; para un sensor ausente se mantienen los valores de la especificación (`true`) |
| D-17 | `GravityFilter` inicializa la gravedad con la primera muestra | Con gravedad inicial cero, la primera lectura (≈9,8 m/s²) daba ~7,8 m/s² de falso movimiento |
| D-18 | Las lecturas de un sensor ausente se ignoran | Un `SensorHardware` sin proximidad no debe dejar que una lectura suelta cambie `isNear` |
| D-19 | La regla de Kover exige 90 % de líneas **por paquete** (`decision` y `processing`) | Kover 0.9 no admite filtros por regla; la regla por paquete cubre RNF-10 y protege también a `processing` |

## Fase 4 — Paquete `capture`

| # | Decisión | Motivo |
|---|---|---|
| D-20 | `SensorDataSource` es una interfaz con `proximity()`, `light()` y `acceleration(mode)`, más `readings(samplingMode: Flow<SamplingMode>)` que los fusiona. La implementación Android es `AndroidSensorDataSource` | RNF-11 (interfaces entre capas) y permite probar la fusión con un origen falso |
| D-21 | Al cambiar el modo de muestreo solo se vuelve a registrar el acelerómetro (`flatMapLatest`); proximidad y luz siguen registradas | La sección 7.5 dice que `SetSampling` re-registra los listeners. Re-registrar solo el que cambia evita perder el último valor de proximidad y luz (sensores por cambio) |
| D-22 | Cada listener recibe sus eventos en un `HandlerThread` propio, que se cierra en `awaitClose` | No carga el hilo principal con `SENSOR_DELAY_GAME` y no deja hilos huérfanos |
| D-23 | Los flujos usan buffer de 64 con `DROP_OLDEST` | Si el consumidor se atrasa se conserva lo más reciente en vez de bloquear el hilo del sensor |
| D-24 | Si `registerListener` devuelve `false`, el flujo termina con `IllegalStateException` | Un sensor obligatorio que no se puede registrar es un fallo grave; el servicio (fase 6) lo captura y desarma |
| D-25 | El acelerómetro cuenta como disponible si existe `TYPE_ACCELEROMETER` **o** `TYPE_LINEAR_ACCELERATION` | Cualquiera de los dos sirve como fuente de movimiento |
| D-26 | `SensorCapabilities` es un tipo de datos puro con `support` (FULL / DEGRADED / UNSUPPORTED), `missing`, `toAvailableSensors()` y `toHardware()` | La UI de inicio (fase 8) y el servicio (fase 6) leen el estado sin depender de Android |
| D-27 | `AndroidSensorDataSource` no tiene pruebas unitarias | Solo se puede verificar con sensores reales: se comprueba en el teléfono con la pantalla Monitor (fase 9) |
