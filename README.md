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

Al iniciar el juego/servidor se crea automáticamente `config/playerscreenevent/` y dentro se coloca `screen.png` como imagen inicial. El campo `imagen` solo contiene el nombre del archivo, por ejemplo `evento.png`; no se escribe la ruta completa.

La imagen debe ser exactamente 16:9 y medir como mínimo 1280x720. El nombre se coloca en `imagen`, por ejemplo `screen.png` o `evento.png`. Imágenes superiores a 1920x1080 se reducen para enviarlas a los clientes.

## Comportamiento

- La pantalla cubre toda la interfaz del juego.
- Mientras está activa no se puede mover ni hacer clic. `Esc` abre el menú de pausa.
- Solo los operadores pueden abrir el chat con T o /. El menú de pausa sigue disponible con `Esc`.
- El chat de un operador vuelve a la pantalla al cerrarse.
- Un jugador que entra durante el evento recibe la pantalla automáticamente.
- El contador se actualiza y debajo aparece `<jugador> se ha unido.` en rojo.
- No hay animaciones durante la espera.
- `/playerscreen hide` hace un fade out de 500 ms.

## Build

GitHub Actions compila automáticamente el JAR Forge 1.20.1 y publica el artefacto.
