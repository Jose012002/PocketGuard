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

## Fase 5 — Paquete `actuation`

| # | Decisión | Motivo |
|---|---|---|
| D-28 | `DetectionConfigProvider` (interfaz en `decision`) da acceso a la configuración vigente. Por ahora `AppModule` devuelve `DetectionConfig.Default`; la fase 7 lo reemplaza por DataStore | `TorchActuator` necesita `strobeIntervalMs` y `Actuator` solo tiene `start()`/`stop()`. Así los actuadores no dependen de cómo se guarda la configuración |
| D-29 | `AlarmController.execute(effects, state)` recibe también el estado resultante y solo atiende los efectos de actuación. Monitoreo, wake lock, muestreo y `Log` quedan para `GuardService` | `UpdateStatusNotification` necesita el estado para elegir el texto. El `when` sobre `Effect` es exhaustivo, así que un efecto nuevo obliga a decidir quién lo atiende |
| D-30 | Cada actuador se ejecuta dentro de un `try/catch` del controlador | RNF-16: un fallo del flash, del audio o de una notificación no impide que actúen los demás |
| D-31 | La lógica que no toca hardware es Kotlin puro: `SirenWaveform` (síntesis), `VibrationPatterns` (patrones) y `TorchDriver` (interfaz sobre la linterna) | Permite probar la forma de onda, los patrones y el estroboscopio (incluido el caso de cámara ocupada) sin dispositivo |
| D-32 | La sirena usa `pause()` + `flush()` antes de liberar el `AudioTrack`, no `stop()` | `stop()` deja sonar lo ya escrito en el búfer; así el corte es inmediato. El búfer es de 100 ms |
| D-33 | `TorchActuator` reintenta en cada ciclo si la cámara está ocupada y registra solo el primer fallo de cada racha; el apagado final va en `finally` con `NonCancellable`, y un `start()` inmediato espera a que termine el apagado anterior | RNF-16 y evita que el apagado de una corrida pise el encendido de la siguiente |
| D-34 | Las acciones de las notificaciones (tocar, "Desarmar") abren `AlarmActivity` con un extra de modo (`AlarmScreenContract`), no desarman directo | CU-07 exige autenticarse para desarmar. La clase se referencia por nombre para que `actuation` no dependa de `ui`; la actividad se crea en la fase 8 |
| D-35 | Solo tres canales (`guard_status`, `pre_alarm`, `alarm`). Los avisos puntuales (tiempo de armado agotado) usan `guard_status` | Es lo que pide la sección 8.3 |
| D-36 | Los canales `pre_alarm` y `alarm` no tienen sonido ni vibración propios | Los aportan la sirena y `VibrationActuator`; si no, sonaría dos veces |
| D-37 | Sin el permiso de notificaciones no se publica nada, pero el servicio y los actuadores siguen funcionando | RF-04: el permiso puede negarse |

**Limitaciones conocidas de esta fase**
- Si el proceso muere con la sirena sonando, el volumen de alarma queda en el máximo, porque el volumen anterior solo se guarda en memoria.
- Con "No molestar" en modo "Silencio total", el sistema puede bloquear incluso el canal de alarma.
- Desde Android 14 el sistema puede permitir descartar la notificación de alarma aunque sea `ongoing`; la alarma sigue sonando hasta autenticarse.

## Fase 6 — Paquete `service`

| # | Decisión | Motivo |
|---|---|---|
| D-38 | La orquestación vive en `GuardPipeline` (sin `Service`); `GuardService` solo aporta el ciclo de vida de Android | Permite probar el bucle, el ticker, los sensores y el wake lock con dobles y tiempo virtual (17 pruebas), sin dispositivo |
| D-39 | `EffectExecutor` (interfaz en `decision`) es lo que el pipeline usa para actuar; `AlarmController` la implementa | RNF-11: el servicio no depende de la clase concreta de actuación, y `actuation` no depende de `service` |
| D-40 | `GuardStateRepository` se crea ya en esta fase (la sección 12 lo pone en la fase 7). Expone `uiState` (estado + última lectura), `state` (solo cambios de estado) y `messages` (avisos puntuales sin repetición) | El servicio necesita publicar estado; los avisos no se reproducen a quien se suscribe tarde, porque además se publican como notificación |
| D-41 | Se añaden `ArmedStateStore` (DataStore) y `EventRecorder` (por ahora solo Logcat; Room llega en la fase 10) | `ArmedStateStore` permite rearmar tras un reinicio del sistema (sección 7.5); `EventRecorder` es el punto donde se registrará el historial |
| D-42 | Hay un único `DataStore<Preferences>` (`pocketguard`) provisto por Hilt | DataStore no admite dos instancias sobre el mismo archivo; las fases 7 y 10 lo reutilizan |
| D-43 | La configuración se lee y se congela al armar (`DetectionConfig` pasa por `coerced()`) | Los ajustes están bloqueados mientras está armado (CU-09). `coerced()` evita que un valor corrupto rompa la histéresis |
| D-44 | `ACTION_DISARM` lleva `EXTRA_AUTH_OK` (por defecto `false`); la interfaz verifica el PIN y avisa al servicio | El servicio no se exporta, así que solo esta app puede enviar la orden. Un intento fallido queda registrado como `AUTH_FAILED` |
| D-45 | Si falla la captura de sensores (`registerListener` devuelve `false` o el flujo lanza), el pipeline se desarma | Es preferible a aparentar que se vigila. Por ahora el dueño lo nota porque desaparece la notificación; un aviso específico queda como mejora |
| D-46 | La latencia entre el snapshot y el inicio de la alarma se guarda en el detalle del evento `ALARM` (`latencyMs=`) cuando la alarma es directa (gracia 0) | Es la medición de RNF-01 |
| D-47 | El `startForeground` usa `FOREGROUND_SERVICE_TYPE_MANIFEST` mediante `ServiceCompat` y se protege contra `IllegalStateException` (Android 12+ no permite iniciar servicios en primer plano desde segundo plano) | Un reinicio pegajoso desde segundo plano podría ser rechazado; el servicio se cierra en lugar de fallar |
| D-48 | El wake lock parcial se toma sin tiempo límite | Se libera al desarmar y en `onDestroy`; si el proceso muere, el sistema lo suelta. Un límite haría que la vigilancia se apague sola |

## Fase 7 — Paquetes `data` y `security`

| # | Decisión | Motivo |
|---|---|---|
| D-49 | `PinHasher` usa PBKDF2WithHmacSHA256, 120 000 iteraciones, sal de 16 bytes y clave de 256 bits, y compara con `MessageDigest.isEqual`. Se verificó contra el vector del RFC 7914 y otro calculado con `hashlib` | RF-02 y sección 7.3. La comparación en tiempo constante evita filtrar información por el tiempo de respuesta |
| D-50 | `PinAuthenticator` (no está en la sección 7.3) une `PinRepository` con `AuthAttemptLimiter` y devuelve `Success`, `WrongPin(attemptsLeft)` o `Locked(remainingMs)` | Es la única vía de autenticación por PIN para las pantallas, así ninguna se salta el límite de intentos (RF-05) |
| D-51 | `AuthAttemptLimiter` es un singleton en memoria: 5 fallos bloquean 30 s y el contador se reinicia; un fallo durante el bloqueo no lo extiende; un acierto o una biometría correcta lo levantan | Compartido por todas las pantallas. **Limitación:** reiniciar el proceso lo restablece |
| D-52 | `BiometricAuthenticator` usa `BIOMETRIC_STRONG or BIOMETRIC_WEAK` con botón "usar PIN"; los intentos fallidos no se reportan y el diálogo sigue abierto; devuelve una función para cancelarlo | Con botón negativo no se puede añadir `DEVICE_CREDENTIAL`; el respaldo es el PIN de la app (RF-03). Requiere `FragmentActivity`: las actividades de la fase 8 deben heredar de ella |
| D-53 | La biometría solo confirma la identidad del dueño; no se ata a una clave criptográfica (`CryptoObject`) | La especificación no lo pide y la alarma no protege secretos. Se anota como limitación |
| D-54 | `SettingsRepository` es también el `DetectionConfigProvider`. `current()` lee DataStore una sola vez (bloqueando unos milisegundos) y luego responde desde memoria; `update()` mantiene esa copia al día | El servicio y los actuadores necesitan la configuración sin suspender. Todos los cambios pasan por el repositorio, así que la copia no se desincroniza |
| D-55 | Toda la configuración se recorta a su rango al leer y al escribir (`coerced()`) | Un valor corrupto o editado a mano no puede romper la histéresis ni el motor |
| D-56 | `PinRepository`, `SettingsRepository` y `ArmedStateStore` comparten el `DataStore` único (`pocketguard`) con claves distintas | Sección 7.3 y D-42. Los archivos del DataStore están excluidos de copias de seguridad (D-05) |
| D-57 | El hash del PIN se calcula en `Dispatchers.Default` | Con 120 000 iteraciones tarda del orden de 100 ms: no debe bloquear el hilo principal |
