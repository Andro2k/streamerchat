package com.streamerplugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EmoteFilterTest {

    private String filterEmotes(String message) {
        if (message == null) return "";
        String filtered = message.replaceAll("\\[emote:\\d+:[^\\]]+\\]", "").trim();
        return filtered.replaceAll(" +", " ");
    }

    @Test
    public void testEmoteFiltering() {
        String messageWithEmote = "Hola a todos [emote:12345:KAPPAPOG] como estan?";
        String clean = filterEmotes(messageWithEmote);
        assertEquals("Hola a todos como estan?", clean);

        String onlyEmote = "[emote:999:GG]";
        assertEquals("", filterEmotes(onlyEmote));
    }
}
