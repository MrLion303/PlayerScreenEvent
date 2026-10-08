# PlayerScreenEvent

Mod Forge 1.20.1 para mostrar pantallas controladas por el servidor.

## Comandos

### Pantalla principal

- `/playerscreen show` — activa la pantalla principal para todos los jugadores.
- `/playerscreen hide` — desactiva la pantalla principal con un fade out de 500 ms.
- `/playerscreen hide me` — oculta la pantalla principal solamente para quien ejecuta el comando.

### Segunda pantalla GIF

- `/playerscreengif show` — activa la segunda pantalla para todos los jugadores.
- `/playerscreengif hide` — desactiva la segunda pantalla con el mismo fade out.
- `/playerscreengif hide me` — oculta la segunda pantalla solamente para quien ejecuta el comando.

Todos los comandos requieren permiso de operador nivel 2.

## Configuración

Al iniciar se crea:

`config/playerscreenevent/`

Dentro se generan:

- `screen.png` — imagen inicial de la pantalla principal.
- `screen.gif` — GIF inicial de la segunda pantalla.
- `config.json` — configuración editable.
- `playerscreenevent-common.toml` — configuración de Forge.

Ejemplo de `config.json`:

```json
{
  "texto": "Esperando jugadores...",
  "jugadores_maximos": -1,
  "imagen": "screen.png",
  "gif": "screen.gif"
}
```

### Pantalla principal

El campo `imagen` acepta PNG, JPG y GIF.

Si colocas un GIF, por ejemplo:

```json
"imagen": "evento.gif"
```

`/playerscreen show` lo reproduce como animación.

### Segunda pantalla

El campo `gif` define el GIF utilizado por `/playerscreengif show`.

Ejemplo:

```json
"gif": "presentacion.gif"
```

El archivo debe estar dentro de `config/playerscreenevent/`.

Los archivos multimedia enviados a los clientes tienen un límite de aproximadamente 1.9 MB para mantener el paquete de red dentro del tamaño previsto por el mod.

## Comportamiento

- Las pantallas cubren toda la interfaz del juego.
- Mientras una pantalla está activa no se puede mover ni hacer clic en el juego.
- `Esc` abre el menú de pausa sobre la pantalla.
- Los botones Progresos, Estadísticas, Enviar opinión, Reportar error, Opciones, Abrir en LAN y Mods permanecen deshabilitados mientras se espera.
- Los operadores pueden abrir el chat con T o /; el chat aparece encima de la pantalla y al cerrarlo la pantalla continúa visible.
- Los jugadores que entren mientras una pantalla está activa la reciben automáticamente.
- El contador y el aviso de entrada se conservan en ambas pantallas.
- No hay animación de entrada durante la espera; solamente `hide` realiza el fade out.
- Si la pantalla principal y la segunda pantalla GIF están activas al mismo tiempo, la segunda pantalla GIF tiene prioridad visual. Al ocultarla, vuelve automáticamente la pantalla principal si esta sigue activa.
- `hide me` solo oculta la pantalla correspondiente para ese jugador.

## GIF

Los GIF se decodifican en el cliente usando el soporte de imágenes de Java, sin añadir dependencias externas. Se respetan los tiempos de cada frame y el GIF se repite automáticamente.

## Build

GitHub Actions compila automáticamente el JAR Forge 1.20.1 y publica el artefacto.
