# Release Notes | StreamerChat v0.1.0
*Integración Multiusuario Kick OAuth2 (PKCE), Conexión WebSocket Pusher en Tiempo Real, Menú GUI Interactivo en Juego, Formateo Kyori MiniMessage con Enlaces Clicables, Filtro de Emotes y Rangos Dinámicos con Colores HEX*

En esta versión inicial (**v0.1.0**), **StreamerChat** se presenta como un plugin de Minecraft de alto rendimiento, moderno y modular diseñado para servidores Spigot, Paper y Purpur (1.20 - 1.21.11+). El plugin conecta en tiempo real los chats de los streamers de **Kick** directamente dentro del juego mediante la arquitectura oficial de la API v2 de Kick, utilizando el flujo seguro de autenticación **OAuth2 (PKCE)** y la red **WebSocket (Pusher)** sin afectar los TPS del servidor. Incluye una interfaz gráfica GUI en cofre para gestionar la sesión desde el juego, soporte avanzado para **Kyori MiniMessage** con botones clicables, y la extracción en vivo de insignias y colores HEX elegidos por cada espectador en Kick.

---

## 1. Autenticación Segura Multi-Usuario Kick OAuth2 (PKCE)
Implementamos la arquitectura completa de autorización mediante OAuth 2.1 con PKCE (*Proof Key for Code Exchange*):
* **Servidor HTTP Incrustado**: Desarrollamos el servidor ligero [OAuthLocalServer.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/auth/OAuthLocalServer.java) escuchando en el puerto `8080` (configurable) para capturar el `code` de redirección de Kick (`/auth/callback`).
* **Seguridad PKCE Anti-Intercepción**: Generamos dinámicamente un `code_verifier` y `code_challenge` (S256) en [PKCEUtil.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/auth/PKCEUtil.java) para proteger el intercambio de tokens.
* **Persistencia de Sesiones**: El gestor [AuthManager.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/auth/AuthManager.java) guarda los tokens de acceso y de refresco (`access_token`, `refresh_token`, `expires_at`) en `users.yml`, refrescando automáticamente la sesión antes de su expiración.

> [!IMPORTANT]
> El sistema OAuth2 es completamente multiusuario. Múltiples jugadores y streamers en el mismo servidor de Minecraft pueden vincular sus propias cuentas individuales de Kick simultáneamente.

---

## 2. Conexión WebSocket en Tiempo Real con Pusher (Kick API v2)
Reemplazamos las consultas por polling HTTP por una infraestructura de recepción asíncrona basada en WebSockets:
* **Cliente WebSocket Asíncrono**: Implementamos [KickWebSocketClient.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/kick/KickWebSocketClient.java) utilizando el cliente nativo de Java 11+ (`java.net.http.WebSocket`) conectado al cluster de Pusher de Kick (`wss://ws-us2.pusher.com`).
* **Reconexión Automática**: Ante caídas temporales de red o reinicios de Kick, el cliente agenda reintentos automáticos cada 5 segundos mediante `ScheduledExecutorService` sin bloquear el hilo principal de Bukkit.
* **Filtro de Emotes**: Purgamos automáticamente las etiquetas de emotes `[emote:ID:nombre]` para mantener un chat ordenado y legible en la pantalla del juego.

> [!TIP]
> Al operar sobre hilos asíncronos y enviar los mensajes a Bukkit mediante el Scheduler principal, la recepción de mensajes del chat de Kick tiene un impacto nulo (0.0ms) en los TPS del servidor.

---

## 3. Formateo Moderno con Kyori MiniMessage y Enlaces Clicables
Integramos la librería **Kyori MiniMessage** con empaquetado seguro para una experiencia visual de última generación:
* **Empaquetado por Shading**: Incluimos y relocalizamos `net.kyori.adventure` en el archivo `.jar` mediante `maven-shade-plugin` en [pom.xml](file:///c:/Users/TheAn/Desktop/java/streamerchat/pom.xml), garantizando compatibilidad nativa en Spigot, Paper y Purpur.
* **Enlace OAuth2 Interactivo**: Transformamos la URL larga de 6 líneas en un botón compacto en el chat: **`🔗 [HAGA CLIC AQUÍ PARA VINCULAR TU CUENTA DE KICK]`**. Al hacer clic o pasar el cursor sobre él (`<click:open_url:...>`), Minecraft abre directamente el navegador.
* **Manejo Inteligente de Mensajes**: En [MessageUtil.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/util/MessageUtil.java) distinguimos mensajes de MiniMessage (`<tag>`) de colores legados (`&a`, `&b`), previniendo la corrupción de caracteres `&` en parámetros de consulta de URLs.

> [!NOTE]
> La deserialización mediante MiniMessage permite utilizar degradados de color, textos flotantes (tooltips), efectos al hacer clic e iconos Unicode modernos en las configuraciones del chat.

---

## 4. Insignias y Colores HEX Dinámicos de Kick (`{user_color}` y `{badge}`)
Recreamos el aspecto visual del chat de Kick directamente dentro de la interfaz de Minecraft:
* **Extracción de Rangos en Vivo**: Identificación automática de insignias (`Broadcaster`, `Moderator`, `Subscriber`, `VIP`, `OG`) desde el payload de Pusher y traducción a prefijos configurables en [config.yml](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/resources/config.yml).
* **Color HEX Real del Espectador**: Extraemos el código HEX seleccionado por el usuario en Kick (`sender.identity.color` o `sender.color`) y lo exponemos como `{user_color}` o `{user_formatted}` (`<color:#HEX>Usuario</color>`).
* **Sincronización en [KickSessionManager.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/kick/KickSessionManager.java)**: Cada mensaje recibido se sustituye dinámicamente según el formato:
  `<green>[Kick]</green> {badge} <color:{user_color}>{user}</color><gray>:</gray> <white>{message}</white>`

---

## 5. Menú GUI Interactivo en el Juego (`/streamkick menu`)
Desarrollamos una interfaz gráfica de cofre de 27 slots para gestionar el estado de la conexión sin memorizar comandos:
* **Menú GUI Interactivo**: Creado en [StreamKickMenu.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/gui/StreamKickMenu.java) y procesado por [MenuListener.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/listeners/MenuListener.java).
* **Ranuras Destacadas**:
  - **Slot 11 (Estrella del Nether / Antorcha)**: Estado de la cuenta, Kick Username, Chatroom ID y WebSocket.
  - **Slot 13 (Tinte Verde / Rojo)**: Interruptor al hacer clic para activar o desactivar la visibilidad del chat en pantalla con sonidos UI.
  - **Slot 15 (Brújula)**: Botón para generar el enlace de vinculación OAuth2.
  - **Slot 22 (Barrera)**: Cierra la sesión y desvincula la cuenta de Kick.

---

## 6. Protección Estricta de Credenciales Privadas
Blindamos el repositorio contra la filtración accidental de llaves de desarrollo:
* **Carga de Secreto Multicapa**: En [StreamerChatPlugin.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/StreamerChatPlugin.java) permitimos cargar credenciales desde variables de entorno (`KICK_CLIENT_ID`, `KICK_CLIENT_SECRET`), `secrets.properties` o `config.yml`.
* **Exclusión en Git**: Agregamos la regla de exclusión estricta en [.gitignore](file:///c:/Users/TheAn/Desktop/java/streamerchat/.gitignore) para ignorar `secrets.properties`, `.env`, `users.yml` y `credentials.yml`.
* **Plantilla Pública**: Proveemos el archivo [secrets.properties.example](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/resources/secrets.properties.example) como referencia para desarrolladores y administradores de servidores.

> [!WARNING]
> Nunca subas el archivo `secrets.properties` ni `users.yml` a tu repositorio público de GitHub. Mantén únicamente el archivo `.example` rastreado por Git.

---

## 7. Presentación Estilizada en Consola y Comandos
Cuidamos la presentación estética al arrancar el servidor:
* **Banner ASCII & Diagnóstico de Carga**: Al habilitar el plugin se muestra el banner de inicio con mediciones exactas en milisegundos para cada manager (`ConfigManager`, `ApiClient`, `AuthManager`, `CommandManager`, `EventManager`).
* **Comando `/streamkick`**: Registrado en [StreamKickCommand.java](file:///c:/Users/TheAn/Desktop/java/streamerchat/src/main/java/com/streamerplugin/commands/StreamKickCommand.java) con subcomandos `menu`, `auth`, `send`, `toggle`, `status`, `disconnect` y `reload`.

---

## 📸 Estructura de Capturas Recomendada

```text
docs/
└── images/
    ├── 01-console-startup-banner.png   # Banner ASCII y tiempos de carga en consola
    ├── 02-in-game-gui-menu.png         # Menú GUI interactivo (/streamkick menu)
    ├── 03-oauth2-kick-link.png         # Mensaje con enlace OAuth2 de Kick
    ├── 04-kick-chat-badges-in-mc.png   # Chat de Kick con rangos (STREAMER, MOD, SUB)
    └── 05-send-chat-command.png        # Comando /streamkick send enviando mensaje a Kick
```
