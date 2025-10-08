package io.github.a2geek.clth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {
    @Test
    public void testMatchType() {
        // Ignore
        assertTrue(Config.MatchType.ignore.matches("right", "wrong"));
        // Exact
        assertTrue(Config.MatchType.exact.matches("right", "right"));
        assertFalse(Config.MatchType.exact.matches("right", "wrong"));
        // Trim
        assertTrue(Config.MatchType.trim.matches("right", "right  "));
        assertTrue(Config.MatchType.trim.matches("right", "  right"));
        assertFalse(Config.MatchType.trim.matches("right", "wrong"));
        // Contains
        assertTrue(Config.MatchType.contains.matches("right", "This is the right answer"));
        assertFalse(Config.MatchType.contains.matches("right", "This is the wrong answer"));
    }

    @Test
    public void testTestFile_contentAsBytes() {
        // Text
        Config.TestFile textFile = new Config.TestFile(Config.FileType.text, "HELLO", "", "");
        assertArrayEquals("HELLO".getBytes(), textFile.contentAsBytes());
        // Binary
        var hex = """
                20 58 fc
                60
                """;
        Config.TestFile binFile = new Config.TestFile(Config.FileType.binary, hex, "", "");
        assertArrayEquals(new byte[] { 0x20, 0x58, (byte)0xfc, 0x60 }, binFile.contentAsBytes());
    }
}
