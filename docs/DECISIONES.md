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
