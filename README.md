# PlayerScreenEvent

Mod Forge 1.20.1 para mostrar una pantalla de espera controlada por el servidor.

## Comandos

- `/playerscreen show` — activa la pantalla para todos los jugadores conectados.
- `/playerscreen hide` — la desactiva para todos con un fade out de 500 ms.

Los comandos requieren permiso de operador nivel 2.

## Personalización

Al arrancar el servidor se genera `config/playerscreenevent-common.toml`.

- `texto`: texto principal, por defecto `Esperando jugadores...`.
- `jugadores_maximos`: cantidad máxima mostrada. Usa `-1` para utilizar el máximo real del servidor.
- `imagen`: ruta de la imagen respecto a la carpeta del servidor.

Por defecto se busca `config/playerscreenevent/screen.png`.

La imagen debe ser exactamente 16:9 y medir como mínimo 1280x720. Imágenes superiores a 1920x1080 se reducen para enviarlas a los clientes.

## Comportamiento

- La pantalla cubre toda la interfaz del juego.
- Mientras está activa no se puede mover, hacer clic ni cerrar con Esc.
- Solo los operadores pueden abrir el chat con T o /.
- El chat de un operador vuelve a la pantalla al cerrarse.
- Un jugador que entra durante el evento recibe la pantalla automáticamente.
- El contador se actualiza y debajo aparece `<jugador> se ha unido.` en rojo.
- No hay animaciones durante la espera.
- `/playerscreen hide` hace un fade out de 500 ms.

## Build

GitHub Actions compila automáticamente el JAR Forge 1.20.1 y publica el artefacto.
