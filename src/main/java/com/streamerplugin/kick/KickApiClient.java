package com.streamerplugin.kick;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.streamerplugin.auth.KickToken;

public class KickApiClient {

    private static final Logger LOGGER = Logger.getLogger(KickApiClient.class.getName());

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient httpClient;

    private static final String KICK_OAUTH_TOKEN_URL = "https://id.kick.com/oauth/token";
    private static final String KICK_API_BASE = "https://api.kick.com/public/v1";

    public KickApiClient(String clientId, String clientSecret, String redirectUri, Logger logger) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<KickToken> exchangeAuthorizationCode(String code, String codeVerifier) {
        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "authorization_code");
        formData.put("client_id", clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            formData.put("client_secret", clientSecret);
        }
        formData.put("redirect_uri", redirectUri);
        formData.put("code", code);
        formData.put("code_verifier", codeVerifier);

        String formBody = buildFormData(formData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KICK_OAUTH_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.log(Level.SEVERE, "[KickApiClient] Error intercambiando token code ({0}): {1}",
                                new Object[]{response.statusCode(), response.body()});
                        throw new RuntimeException("Error en respuesta de token Kick HTTP " + response.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String accessToken = json.get("access_token").getAsString();
                    String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : "";
                    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
                    String tokenType = json.has("token_type") ? json.get("token_type").getAsString() : "Bearer";
                    String scope = json.has("scope") ? json.get("scope").getAsString() : "";

                    return new KickToken(accessToken, refreshToken, expiresIn, tokenType, scope);
                });
    }

    public CompletableFuture<KickToken> refreshToken(String refreshToken) {
        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "refresh_token");
        formData.put("client_id", clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            formData.put("client_secret", clientSecret);
        }
        formData.put("refresh_token", refreshToken);

        String formBody = buildFormData(formData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KICK_OAUTH_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.log(Level.SEVERE, "[KickApiClient] Error renovando token ({0}): {1}",
                                new Object[]{response.statusCode(), response.body()});
                        throw new RuntimeException("Error renovando token HTTP " + response.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String newAccessToken = json.get("access_token").getAsString();
                    String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
                    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;

                    return new KickToken(newAccessToken, newRefreshToken, expiresIn, "Bearer", "");
                });
    }

    public CompletableFuture<JsonObject> getUserProfile(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KICK_API_BASE + "/users"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.log(Level.WARNING, "[KickApiClient] Error al obtener perfil de usuario ({0}): {1}",
                                new Object[]{response.statusCode(), response.body()});
                        return null;
                    }
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }

    public CompletableFuture<JsonObject> getChannelInfo(String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://kick.com/api/v2/channels/" + URLEncoder.encode(username, StandardCharsets.UTF_8)))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.log(Level.WARNING, "[KickApiClient] No se pudo obtener canal para ''{0}'' ({1})",
                                new Object[]{username, response.statusCode()});
                        return null;
                    }
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }

    public CompletableFuture<Boolean> sendChatMessage(String accessToken, int broadcasterUserId, String messageContent) {
        JsonObject body = new JsonObject();
        body.addProperty("content", messageContent);
        body.addProperty("type", "user");
        if (broadcasterUserId > 0) {
            body.addProperty("broadcaster_user_id", broadcasterUserId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KICK_API_BASE + "/chat"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        return true;
                    }
                    LOGGER.log(Level.WARNING, "[KickApiClient] Error enviando mensaje a Kick ({0}): {1}",
                            new Object[]{response.statusCode(), response.body()});
                    return false;
                });
    }

    private String buildFormData(Map<String, String> data) {
        return data.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
