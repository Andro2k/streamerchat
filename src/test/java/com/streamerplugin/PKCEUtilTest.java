package com.streamerplugin;

import com.streamerplugin.auth.PKCEUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PKCEUtilTest {

    @Test
    public void testCodeVerifierLengthAndFormat() {
        String verifier = PKCEUtil.generateCodeVerifier();
        assertNotNull(verifier);
        assertTrue(verifier.length() >= 43 && verifier.length() <= 128, "Code verifier length should be between 43 and 128 chars");
    }

    @Test
    public void testCodeChallengeGeneration() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = PKCEUtil.generateCodeChallenge(verifier);
        assertNotNull(challenge);
        assertFalse(challenge.contains("="), "Code challenge in Base64 URL format should not have padding");
    }

    @Test
    public void testStateGeneration() {
        String state1 = PKCEUtil.generateState();
        String state2 = PKCEUtil.generateState();
        assertNotNull(state1);
        assertNotNull(state2);
        assertNotEquals(state1, state2, "Generated states should be unique and random");
    }
}
