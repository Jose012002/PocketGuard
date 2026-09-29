# PocketGuard

Alarma antirrobo para teléfonos Android: la armas, guardas el teléfono en el bolsillo o la mochila y, si alguien lo saca sin autorización, combina **proximidad, luz y acelerómetro** para activar automáticamente una sirena, un flash estroboscópico y la vibración.

Taller 2 — Desarrollo de un Sistema Integrado (UNI, 2026-2). Android · Kotlin · Jetpack Compose.

**Integrantes:** 

| Nombre | Código |
|---|---|
| Jose Luis Diaz Silva | 20222025D |
| Yadira Jhenyfer Cantorin Lope | 20222174J |
| Rosse Emily Morales Santiago | 20222105H |

## 1. Flujo del sistema

```
ENTRADA FÍSICA ──► PROCESAMIENTO ──► DECISIÓN ──► ACCIÓN FÍSICA
 proximidad         filtro EMA          máquina de     sirena (AudioTrack)
 luz                histéresis          estados con    flash estroboscópico
 acelerómetro       filtro de gravedad  decisión       vibración
                    fusión              conjunta       notificación
```

| Etapa | Sensores / actuadores | Paquete |
|---|---|---|
| Entrada | Proximidad, luminosidad, aceleración lineal (o acelerómetro) | `capture` |
| Procesamiento | Filtro EMA de luz, histéresis oscuro/claro, filtro pasa-altos, fusión en `SensorSnapshot` | `processing` |
| Decisión | `TheftDetectionEngine`: reducer puro `reduce(estado, entrada) → (estado, efectos)` | `decision` |
| Acción | Sirena por el canal de alarma, flash, vibración y notificación de alta prioridad | `actuation` |
| Orquestación | Servicio en primer plano con un único canal de entradas y ticker de 100 ms | `service` |

La alarma **solo** se dispara si los tres indicadores (proximidad "lejos", salto de luz y movimiento) coinciden dentro de una ventana de 1 s. Ningún sensor por sí solo puede dispararla. Si falta un sensor, funciona en **modo degradado** con los que hay (siempre con al menos el acelerómetro y uno de los otros dos).

Máquina de estados: `Desarmado → Armando → Guardado → Sospecha → Pre-alarma → Alarma`. Detalle en [`docs/SPEC.md`](docs/SPEC.md), sección 6.

## 2. Requisitos

- **Android Studio** estable reciente (probado con la compilación `AI-261`), que incluye el JDK necesario. Si compilas desde la terminal, usa JDK 17 o superior.
- **Dispositivo físico** con Android 8.0 (API 26) o superior. Los emuladores no reproducen bien los sensores de proximidad y luz.
- SDK de Android con la plataforma 37 (`compileSdk` y `targetSdk` = 37).

## 3. Compilar e instalar

Abre la carpeta en Android Studio (File → Open) y espera el Gradle sync. Con el teléfono conectado por USB y la depuración USB activada, pulsa Run. Desde la terminal:

```
# Windows
.\gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## 4. Cómo usar la app

1. **Primer uso:** lee las 3 pantallas de introducción, crea un PIN de 4 a 6 dígitos, confírmalo y concede el permiso de notificaciones.
2. **Armar:** en Inicio pulsa **Armar** (un solo toque). La notificación pide "Guarda el teléfono en tu bolsillo". Tienes 15 s.
3. **Guardado:** cuando el teléfono lleva 3 s en el bolsillo vibra una vez y el estado pasa a **Guardado**.
4. **Extracción:** si alguien lo saca, entra en **Pre-alarma** (vibración suave y pantalla de desbloqueo durante 3 s) y después en **Alarma**.
5. **Desarmar:** con el PIN (o la huella, si la habilitas en Ajustes). Tras 5 intentos fallidos el ingreso se bloquea 30 s.

Otras pantallas: **Monitor** (valores en vivo y grabación de trazas CSV en debug), **Ajustes** (perfiles de sensibilidad y parámetros) e **Historial** de eventos.

### Guion de la demostración

| Escenario | Qué hacer | Resultado esperado |
|---|---|---|
| **E1** Extracción normal | Armar, guardar en el bolsillo, esperar la vibración de confirmación y sacar el teléfono | Pre-alarma y luego alarma con sirena, flash y vibración |
| **E2** Sensor tapado | Teléfono sobre la mesa; tapar y destapar el sensor con la mano | Sin alarma: nunca llega a *Guardado* o no hay movimiento |
| **E3** Luz de la habitación | Teléfono guardado; encender y apagar la luz | Sin alarma |
| **E6** El dueño lo saca | Sacar el teléfono e ingresar el PIN durante la gracia | Se desarma sin que suene la sirena |

La lista completa de pruebas de aceptación (E1 a E10 y los requisitos no funcionales) está en [`docs/PRUEBAS_ACEPTACION.md`](docs/PRUEBAS_ACEPTACION.md).

### Calibrar los umbrales con tu teléfono

Los umbrales por defecto son un punto de partida y dependen del hardware:

1. Abre **Monitor** y comprueba que la proximidad cambia al tapar el sensor (algunos teléfonos tienen un sensor "virtual" que solo funciona en llamadas).
2. En una compilación debug, pulsa **Grabar**, haz el gesto de sacar el teléfono varias veces y **Detener**. Comparte el CSV.
3. Ajusta los umbrales (o elige un perfil Baja/Media/Alta) en **Ajustes** con la alarma desarmada.

## 5. Requisito del taller → código

| Requisito del taller | Dónde se cumple |
|---|---|
| ≥ 2 capacidades físicas de entrada | [`capture/SensorDataSource.kt`](app/src/main/java/com/equipo/pocketguard/capture/SensorDataSource.kt), [`capture/AndroidSensorDataSource.kt`](app/src/main/java/com/equipo/pocketguard/capture/AndroidSensorDataSource.kt), [`capture/SensorCapabilityChecker.kt`](app/src/main/java/com/equipo/pocketguard/capture/SensorCapabilityChecker.kt) |
| ≥ 1 capacidad física de salida | [`actuation/SirenActuator.kt`](app/src/main/java/com/equipo/pocketguard/actuation/SirenActuator.kt), [`actuation/TorchActuator.kt`](app/src/main/java/com/equipo/pocketguard/actuation/TorchActuator.kt), [`actuation/VibrationActuator.kt`](app/src/main/java/com/equipo/pocketguard/actuation/VibrationActuator.kt), [`actuation/AlarmNotifier.kt`](app/src/main/java/com/equipo/pocketguard/actuation/AlarmNotifier.kt) |
| Entradas que deciden conjuntamente | [`decision/TheftDetectionEngine.kt`](app/src/main/java/com/equipo/pocketguard/decision/TheftDetectionEngine.kt), [`decision/Indicators.kt`](app/src/main/java/com/equipo/pocketguard/decision/Indicators.kt) |
| Procesar, decidir y actuar automáticamente | [`processing/SignalProcessor.kt`](app/src/main/java/com/equipo/pocketguard/processing/SignalProcessor.kt), [`service/GuardPipeline.kt`](app/src/main/java/com/equipo/pocketguard/service/GuardPipeline.kt), [`service/GuardService.kt`](app/src/main/java/com/equipo/pocketguard/service/GuardService.kt), [`actuation/AlarmController.kt`](app/src/main/java/com/equipo/pocketguard/actuation/AlarmController.kt) |
| Responder a cambios del entorno en ejecución | [`service/GuardPipeline.kt`](app/src/main/java/com/equipo/pocketguard/service/GuardPipeline.kt) (muestreo dinámico y ticker), [`capture/SensorDataSource.kt`](app/src/main/java/com/equipo/pocketguard/capture/SensorDataSource.kt) |
| Separar captura, procesamiento, decisión y actuación | Un paquete por capa (`capture`, `processing`, `decision`, `actuation`) y los tests de arquitectura [`DecisionArchitectureTest`](app/src/test/java/com/equipo/pocketguard/decision/DecisionArchitectureTest.kt) y [`ProcessingArchitectureTest`](app/src/test/java/com/equipo/pocketguard/processing/ProcessingArchitectureTest.kt) |

## 6. Estructura del proyecto

```
app/src/main/java/com/equipo/pocketguard/
├── capture/      Sensores (Flow), capacidades del dispositivo, grabación de trazas
├── processing/   Filtros, histéresis y fusión en SensorSnapshot
├── decision/     Motor de detección: Kotlin puro, sin android.*
├── actuation/    Sirena, flash, vibración, notificaciones y AlarmController
├── service/      GuardService, GuardPipeline, WakeLock
├── data/         Estado compartido, DataStore (PIN, ajustes), Room (historial)
├── security/     PBKDF2, límite de intentos, biometría
├── ui/           Compose: onboarding, inicio, monitor, ajustes, historial, alarma
└── di/           Módulos de Hilt
```

Cada capa se comunica con la siguiente mediante interfaces o tipos de datos (`SensorReading`, `SensorSnapshot`, `GuardInput`, `Effect`, `EffectExecutor`), no mediante clases concretas.

## 7. Pruebas y cobertura

```
.\gradlew.bat testDebugUnitTest     # 315 pruebas unitarias
.\gradlew.bat koverVerify           # falla si decision o processing bajan del 90 % de líneas
.\gradlew.bat koverHtmlReport       # informe en app/build/reports/kover/html/index.html
.\gradlew.bat lintDebug             # informe en app/build/reports/lint-results-debug.html
```

Cobertura de líneas medida: `decision` 100 % (234/234), `processing` 100 % (100/100); el mínimo exigido (RNF-10) es 90 %. Además de la máquina de estados, hay pruebas del pipeline completo con sensores y actuadores falsos en tiempo virtual, de los ViewModels, del PIN, del límite de intentos, de los ajustes y del historial. Lo que depende de hardware real (audio, cámara, biometría, sensores) se comprueba con las pruebas de aceptación manuales.

## 8. Limitaciones conocidas

- No se puede impedir que alguien apague el teléfono con el botón físico.
- Algunos teléfonos usan un sensor de proximidad "virtual" que solo funciona durante las llamadas: verifícalo en **Monitor** antes de la demostración.
- Algunas capas de personalización (MIUI, EMUI, One UI…) pueden cerrar servicios en segundo plano pese al servicio en primer plano. La app sugiere excluirla de la optimización de batería.
- Desde Android 14 el sistema puede no permitir la pantalla completa sobre el bloqueo; la alarma sigue con la notificación emergente y los actuadores físicos.
- Con "No molestar" en modo "Silencio total" el sistema puede silenciar incluso la sirena.
- Los umbrales dependen del hardware y requieren calibración por dispositivo.
- El bloqueo por intentos fallidos vive en memoria: reiniciar el proceso lo restablece.
- Si el proceso muere con la sirena sonando, el volumen de alarma queda en el máximo.
- La detección en dispositivo real no se ha medido todavía (latencia, tasa de detección y falsas alarmas: ver `docs/PRUEBAS_ACEPTACION.md`).

Las decisiones técnicas y las desviaciones de la especificación están en [`docs/DECISIONES.md`](docs/DECISIONES.md).
