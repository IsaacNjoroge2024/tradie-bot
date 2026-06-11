package com.tradie.alert.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramEscaperTest {

    @Test
    void escape_null_returnsEmpty() {
        assertEquals("", TelegramEscaper.escape(null));
    }

    @Test
    void escape_emptyString_returnsEmpty() {
        assertEquals("", TelegramEscaper.escape(""));
    }

    @Test
    void escape_plainText_unchanged() {
        assertEquals("AAPL", TelegramEscaper.escape("AAPL"));
        assertEquals("BUY", TelegramEscaper.escape("BUY"));
        assertEquals("100", TelegramEscaper.escape("100"));
    }

    @Test
    void escape_period_escaped() {
        assertEquals("150\\.50", TelegramEscaper.escape("150.50"));
    }

    @Test
    void escape_dash_escaped() {
        assertEquals("\\-2\\.3", TelegramEscaper.escape("-2.3"));
    }

    @Test
    void escape_plus_escaped() {
        assertEquals("\\+4\\.7", TelegramEscaper.escape("+4.7"));
    }

    @Test
    void escape_parentheses_escaped() {
        assertEquals("\\(WIN\\)", TelegramEscaper.escape("(WIN)"));
    }

    @Test
    void escape_underscore_escaped() {
        assertEquals("FVG\\_BULLISH", TelegramEscaper.escape("FVG_BULLISH"));
    }

    @Test
    void escape_asterisk_escaped() {
        assertEquals("\\*bold\\*", TelegramEscaper.escape("*bold*"));
    }

    @Test
    void escape_backslash_escaped() {
        assertEquals("\\\\", TelegramEscaper.escape("\\"));
    }

    @Test
    void escape_mixedSpecialChars_allEscaped() {
        String input    = "Price: $150.50 (-2.3%)";
        String expected = "Price: $150\\.50 \\(\\-2\\.3%\\)";
        assertEquals(expected, TelegramEscaper.escape(input));
    }
}
