# Visión del proyecto — Astrofoto

> Este documento es la fuente de verdad del proyecto. Se actualiza a medida
> que evoluciona el alcance. Cualquier trabajo futuro (humano o asistido)
> debería leer esto antes de tocar código.

## Qué es esta app

**No es una app casual para sacarle fotos al cielo.** Es una herramienta
científica de astrofotografía: captura datos crudos del sensor para que
puedan procesarse con rigor después, y con el tiempo incorpora sus propias
herramientas de análisis y mejora de imagen.

## Requisitos core (no negociables)

1. **Captura en RAW (DNG)**, no JPEG. El JPEG pierde el rango dinámico y la
   información lineal del sensor que hace falta para stacking, calibración
   y análisis científico.
2. **Carpeta propia de la app** para todas las fotos, separada del carrete
   general del teléfono:
   - `Pictures/Astrofoto/RAW/` — capturas DNG sin procesar.
   - `Pictures/Astrofoto/` — JPEGs de vista rápida / procesados (a futuro).

## Herramientas de procesamiento planeadas

- **Reducción de ruido** — stacking temporal (promedio/mediana de varios
  frames), y a futuro sustracción de dark frames.
- **Corrección de contaminación lumínica** — extracción del gradiente de
  fondo de cielo (técnica tipo "Automatic Background Extraction").
- **Píxeles quemados / manchas** — mapa de píxeles defectuosos e
  interpolación, comparando contra dark frames cuando estén disponibles.
- **Detección de estrellas, planetas y nebulosas** — detección de blobs
  brillantes sobre fondo oscuro, con catálogo de referencia (Messier/NGC,
  catálogo estelar) para identificar qué es cada uno.

## Guía del cielo — capas y detección

- **Sistema de capas** sobre la vista del cielo: nombres y etiquetas de
  estrellas, planetas, constelaciones, objetos de catálogo — cada capa se
  puede prender/apagar independientemente.
- **Detección de objetos en movimiento**: satélites, meteoritos — analizando
  diferencias entre frames consecutivos de una secuencia (intervalómetro).
- **Corrección de barrido por rotación terrestre** (star trailing): compensar
  el desplazamiento de las estrellas en exposiciones largas, ya sea alineando
  frames en el stacking o corrigiendo el encuadre en vivo según la tasa
  sideral, la distancia focal y la ubicación apuntada.

## Otras funciones propuestas (a evaluar)

- **Frames de calibración**: flujo guiado para capturar darks, flats y bias,
  y aplicarlos automáticamente al procesar — esto es lo que más sube la
  calidad real en astrofotografía amateur.
- **Plate solving**: identificar automáticamente qué región del cielo capturó
  cada foto comparando el patrón de estrellas contra un catálogo.
- **Asistencia de enfoque**: máscara de Bahtinov o un indicador de nitidez
  por contraste en vivo (el enfoque manual a ciegas de noche es el mayor
  punto de fricción real).
- **Histograma en vivo** durante la captura, clave para exponer bien sin
  quemar highlights ni perder sombras en la oscuridad.
- **Metadata astronómica embebida**: GPS + timestamp preciso en cada DNG,
  útil para referencia astrométrica posterior.
- **Estimación de Bortle / fase lunar** según ubicación y fecha, para avisar
  si las condiciones de la noche son buenas para salir a fotografiar.
- **Exportación a FITS**, el formato estándar en astronomía, además de
  DNG/JPEG, para interoperar con software de escritorio (PixInsight, Siril).
- **Bitácora de sesión**: equipo usado, temperatura ambiente, ubicación —
  metadata de reproducibilidad científica por sesión de captura.

## Estado actual de implementación

- [x] Captura manual (ISO, exposición, foco) con controlador Camera2 directo
      (no CameraX, porque CameraX no expone `RAW_SENSOR`).
- [x] Guardado en DNG dentro de `Pictures/Astrofoto/RAW/`.
- [x] Galería en-app (grid MediaStore + thumbnails DNG).
- [x] UI en tabs (Exposición/Enfoque/Intervalómetro/Calibración).
- [x] Calibración: captura N frames + promedio píxel a píxel → master DNG
      (dark/flat/bias). El promedio corre en la propia app, sin libs externas.
- [x] Apilado (stacking) básico: promedia N lights consecutivas, sin
      alineación de estrellas todavía — sirve para tomas cortas con poco
      barrido. La alineación real (plate solving / registration) queda
      pendiente, es lo que permitiría apilar exposiciones largas con rotación
      de campo visible.
- [x] Thumbnail embebido en cada DNG (se toma del preview en vivo al
      disparar) — antes el DNG no tenía preview visible, por eso la galería
      no mostraba nada.
- [x] Galería: tocar una foto abre vista completa + "Abrir con" (intent
      externo, útil si el usuario tiene una app que sepa render DNG).
- [ ] Aplicar la calibración a los lights `(Light-Dark)/(Flat-Bias)` —
      pendiente, es el corazón del módulo de reducción de ruido.
- [ ] Intervalómetro adaptado a RAW (pendiente de revalidar tras el cambio).
- [ ] Todo lo demás listado arriba: pendiente, se va marcando acá a medida
      que se implementa.
