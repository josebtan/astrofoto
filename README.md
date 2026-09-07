# Astrofoto

App Android de astrofotografía (Kotlin + Jetpack Compose). Este es el esqueleto
base del proyecto: compila, corre y tiene CI en GitHub Actions, pero todavía
sin funcionalidad real — solo la estructura para ir sumando módulos.

## Módulos previstos

- `capture/` ✅ — control manual de cámara (ISO, exposición, foco, intervalómetro) vía CameraX + Camera2Interop. Es la pantalla principal actual.
- `stacking/` — apilado y alineación de imágenes para reducir ruido. Pendiente.
- `skyguide/` — guía del cielo nocturno (posición de objetos según ubicación/hora). Pendiente.

Los módulos pendientes tienen un archivo `*Module.kt` con un `TODO` como punto de partida.

## Cómo abrirlo

1. Cloná el repo.
2. Abrilo con Android Studio (versión Koala o más nueva). Android Studio
   genera automáticamente el `gradlew`/`gradle-wrapper.jar` que faltan en
   este esqueleto (no se pudieron generar desde este entorno por
   restricciones de red).
   - Alternativa manual: con Gradle instalado localmente, correr
     `gradle wrapper --gradle-version 8.7` en la raíz del proyecto.
   - El CI de GitHub Actions no depende del wrapper: usa
     `gradle/actions/setup-gradle` para instalar Gradle 8.7 directamente.
3. Sincronizá Gradle y corré la app en un emulador o dispositivo (minSdk 26).

## CI en GitHub Actions

El workflow `.github/workflows/android-ci.yml` corre en cada push/PR a `main`:
1. Configura JDK 17.
2. Corre los tests unitarios (`./gradlew test`).
3. Compila el APK debug (`./gradlew assembleDebug`).
4. Sube el APK como artifact descargable desde la pestaña **Actions** del repo.

No hace falta keystore ni secrets para este build debug. Cuando quieras armar
un release firmado, se agrega un job aparte con el keystore como GitHub Secret.

## Subir esto a GitHub

```bash
git init
git add .
git commit -m "Estructura base del proyecto Astrofoto"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/astrofoto.git
git push -u origin main
```
