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
