package com.streamerplugin.auth;

public class KickToken {
    private final String accessToken;
    private final String refreshToken;
    private final long expiresAtMillis;
    private final String tokenType;
    private final String scope;

    public KickToken(String accessToken, String refreshToken, long expiresInSeconds, String tokenType, String scope) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds - 60) * 1000L;
        this.tokenType = tokenType;
        this.scope = scope;
    }

    public KickToken(String accessToken, String refreshToken, long expiresAtMillis) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = expiresAtMillis;
        this.tokenType = "Bearer";
        this.scope = "";
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getScope() {
        return scope;
    }
}
