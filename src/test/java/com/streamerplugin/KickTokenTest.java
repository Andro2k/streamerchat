package com.streamerplugin;

import com.streamerplugin.auth.KickToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KickTokenTest {

    @Test
    public void testTokenExpiration() {
        KickToken token = new KickToken("access123", "refresh123", 3600, "Bearer", "user:read");
        assertFalse(token.isExpired(), "Fresh token should not be expired");
        assertEquals("access123", token.getAccessToken());
        assertEquals("refresh123", token.getRefreshToken());

        KickToken expiredToken = new KickToken("oldAccess", "oldRefresh", System.currentTimeMillis() - 600_000L);
        assertTrue(expiredToken.isExpired(), "Old token should be marked as expired");
    }
}
