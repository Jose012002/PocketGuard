# PocketGuard

Alarma antirrobo para teléfonos Android: se arma, se guarda en el bolsillo y, si alguien lo saca sin autorización, combina proximidad, luz y acelerómetro para activar sirena, flash y vibración.

Taller 2 — Desarrollo de un Sistema Integrado (UNI, 2026-2).

> Este README es provisional (fase 1). La versión final se genera en la fase 11.

- Especificación completa: [`docs/SPEC.md`](docs/SPEC.md)
- Decisiones técnicas: [`docs/DECISIONES.md`](docs/DECISIONES.md)

## Requisitos

- Android Studio (versión estable reciente) con JDK 17 o superior
- Dispositivo físico con Android 8.0 (API 26) o superior

## Compilar

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
