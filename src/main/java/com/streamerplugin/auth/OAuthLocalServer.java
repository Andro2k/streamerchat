package com.streamerplugin.auth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class OAuthLocalServer {

    private static final Logger LOGGER = Logger.getLogger(OAuthLocalServer.class.getName());

    private final int port;
    private final AuthManager authManager;
    private HttpServer server;
    private final Map<String, AuthState> pendingStates = new ConcurrentHashMap<>();

    public static class AuthState {
        private final UUID playerUuid;
        private final String codeVerifier;
        private final long createdAt;

        public AuthState(UUID playerUuid, String codeVerifier) {
            this.playerUuid = playerUuid;
            this.codeVerifier = codeVerifier;
            this.createdAt = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getCodeVerifier() {
            return codeVerifier;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 600_000L;
        }
    }

    public OAuthLocalServer(int port, AuthManager authManager, Logger logger) {
        this.port = port;
        this.authManager = authManager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            CallbackHandler callbackHandler = new CallbackHandler();
            server.createContext("/auth/callback", callbackHandler);
            server.createContext("/kick/callback", callbackHandler);
            server.createContext("/callback", callbackHandler);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            LOGGER.log(Level.INFO, "[StreamerChat] Servidor HTTP OAuth2 iniciado en 0.0.0.0:{0}", port);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[StreamerChat] Error al iniciar el servidor HTTP OAuth2 en puerto " + port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            LOGGER.info("[StreamerChat] Servidor HTTP OAuth2 detenido.");
        }
    }

    public void registerPendingState(String state, UUID playerUuid, String codeVerifier) {
        pendingStates.values().removeIf(s -> s != null && s.isExpired());
        pendingStates.put(state, new AuthState(playerUuid, codeVerifier));
    }

    public AuthState getPendingStateForPlayer(UUID playerUuid) {
        pendingStates.values().removeIf(s -> s != null && s.isExpired());
        for (AuthState state : pendingStates.values()) {
            if (state != null && state.getPlayerUuid().equals(playerUuid) && !state.isExpired()) {
                return state;
            }
        }
        return null;
    }

    public AuthState getPendingState(String state) {
        pendingStates.values().removeIf(s -> s != null && s.isExpired());
        AuthState authState = pendingStates.get(state);
        if (authState != null && !authState.isExpired()) {
            return authState;
        }
        return null;
    }

    public void removePendingState(String state) {
        if (state != null) {
            pendingStates.remove(state);
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> queryParams = parseQueryParams(query);

            String state = queryParams.get("state");
            String code = queryParams.get("code");
            String error = queryParams.get("error");

            String responseHtml;
            int responseCode = 200;

            if (error != null) {
                responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                        + "<h2 style='color:#e74c3c;'>Error de Autenticación</h2>"
                        + "<p>" + error + "</p>"
                        + "</body></html>";
            } else if (state == null || code == null || !pendingStates.containsKey(state)) {
                responseCode = 400;
                responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                        + "<h2 style='color:#e74c3c;'>Solicitud Inválida o Sesión Expirada</h2>"
                        + "<p>Por favor, ejecuta el comando <b>/streamkick auth</b> nuevamente en Minecraft.</p>"
                        + "</body></html>";
            } else {
                AuthState authState = pendingStates.remove(state);
                if (authState.isExpired()) {
                    responseCode = 400;
                    responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                            + "<h2 style='color:#e74c3c;'>Tiempo Expirado</h2>"
                            + "<p>El enlace de autenticación ha caducado. Inténtalo de nuevo.</p>"
                            + "</body></html>";
                } else {
                    responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                            + "<h2 style='color:#2ecc71;'>¡Autenticación Exitosa con Kick!</h2>"
                            + "<p>Tu cuenta ha sido vinculada correctamente. Puedes cerrar esta ventana y regresar a Minecraft.</p>"
                            + "</body></html>";

                    authManager.handleAuthorizationCode(authState.getPlayerUuid(), code, authState.getCodeVerifier());
                }
            }

            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(responseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isEmpty()) {
                return map;
            }
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    map.put(pair[0], pair[1]);
                } else if (pair.length == 1) {
                    map.put(pair[0], "");
                }
            }
            return map;
        }
    }
}
