# Pruebas de aceptación (manuales, en dispositivo real)

Cumplen la sección 11.2 de la especificación. Marca cada fila con el resultado y anota el modelo del teléfono.

**Dispositivo:** _(modelo)_ · **Android:** _(versión)_ · **Fecha:** _(fecha)_ · **Compilación:** debug / release

## Preparación

1. Instala la app (`.\gradlew.bat installDebug`), crea el PIN y concede las notificaciones.
2. Abre **Monitor** y comprueba que la proximidad cambia al tapar el sensor y que la luz responde a una linterna.
3. Abre **Inicio** y revisa la tarjeta de sensores. Si algo falta, la app funciona en modo degradado y lo indica.
4. Opcional: excluye la app de la optimización de batería (Inicio lo sugiere).
5. Para ver los registros del sistema: `adb logcat -s PocketGuard`.

## Escenarios (sección 11.2)

| ID | Escenario | Resultado esperado | Resultado | Notas |
|---|---|---|---|---|
| E1 | Armar, guardar en el bolsillo, esperar la confirmación y sacar el teléfono con normalidad | Pre-alarma y luego alarma | ☐ | |
| E2 | Teléfono sobre la mesa; tapar y destapar el sensor con la mano | Sin alarma (nunca llega a *Guardado*, o no hay movimiento) | ☐ | |
| E3 | Teléfono guardado; encender y apagar la luz de la habitación | Sin alarma | ☐ | |
| E4 | Teléfono guardado; caminar, sentarse y subir escaleras durante 2 min | Sin alarma | ☐ | |
| E5 | Teléfono guardado; sacarlo muy despacio y luego moverlo | Alarma al moverlo | ☐ | |
| E6 | Teléfono guardado; el dueño lo saca e ingresa el PIN durante la gracia | Desarmado sin sirena | ☐ | |
| E7 | Armar, guardar, esperar 10 min con la pantalla apagada y sacarlo | Alarma | ☐ | |
| E8 | Armar y cerrar la app desde recientes; guardar y sacar | Alarma | ☐ | |
| E9 | Teléfono en modo silencio; provocar la alarma | La sirena suena | ☐ | |
| E10 | Abrir la cámara en otra app y provocar la alarma | Sirena y vibración funcionan aunque el flash falle | ☐ | |

## Requisitos no funcionales que se verifican a mano

| ID | Cómo verificar | Resultado |
|---|---|---|
| RNF-01 (latencia < 300 ms, p95) | En Ajustes pon el periodo de gracia en 0, provoca 20 alarmas y revisa en **Historial** (o en `adb logcat -s PocketGuard`) el campo `latencyMs=` del evento *Alarma*. Calcula el percentil 95 | ☐ |
| RNF-02 (cero falsas alarmas en E2–E4) | Repetir E2, E3 y E4 al menos 10 veces cada uno | ☐ |
| RNF-03 (≥ 9 de 10 extracciones) | Repetir E1 diez veces | ☐ _/10 |
| RNF-04 (30 min con la pantalla apagada) | Armar, guardar, esperar 30 min con la pantalla apagada y sacarlo | ☐ |
| RNF-05 (sin listeners ni wake lock desarmado) | Desarmada la alarma: `adb shell dumpsys sensorservice` no debe listar `com.equipo.pocketguard`, y `adb shell dumpsys power` no debe mostrar el wake lock `PocketGuard:guard` | ☐ |
| RNF-08 (cerrar desde recientes no desarma) | Es el escenario E8 | ☐ |
| RNF-14 (accesibilidad) | Analizar cada pantalla con Accessibility Scanner: botones con descripción y tamaño táctil ≥ 48 dp | ☐ |
| RNF-16 (sin flash o con la cámara ocupada) | Es el escenario E10 | ☐ |

## Cómo interpretar un fallo

- **Falsa alarma** (suena sin extracción): baja la sensibilidad en Ajustes (perfil *Baja*) o sube el umbral de movimiento. Graba una traza en debug para ver qué indicadores se activaron.
- **No detecta la extracción**: sube la sensibilidad (perfil *Alta*), amplía la ventana de coincidencia o baja el umbral de movimiento.
- **Nunca llega a *Guardado***: mira en el **Monitor** si el teléfono ve "cerca" y "oscuro" a la vez dentro del bolsillo. Si la luz no baja del umbral, sube el *umbral para oscuro*.
- **El estado se queda en *Armando***: la proximidad del teléfono puede ser un sensor virtual (ver Limitaciones en el README).
