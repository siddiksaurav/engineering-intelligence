package com.dsinnovators.ei.common;

import static com.dsinnovators.ei.common.CanonicalJson.canonicalize;
import static com.dsinnovators.ei.common.CanonicalJson.parse;
import static com.dsinnovators.ei.common.CanonicalJson.sha256Hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

    @Test
    void sortsKeysAndDropsWhiteSpace() {
        assertEquals("{\"a\":2,\"b\":1}", canonicalize(parse("{ \"b\": 1,\n \"a\": 2 }")));
    }

    @Test
    void sortsKeysAtEveryDepthAndKeepsArrayOrder() {
        assertEquals(
                "{\"a\":{\"x\":1,\"y\":[3,1,2]},\"z\":null}",
                canonicalize(parse("{\"z\":null,\"a\":{\"y\":[3,1,2],\"x\":1}}")));
    }

    @Test
    void sortsKeysOfObjectsInsideArrays() {
        assertEquals("[{\"a\":1,\"b\":2}]", canonicalize(parse("[{\"b\":2,\"a\":1}]")));
    }

    @Test
    void keyOrderDoesNotChangeTheHash() {
        assertEquals(sha256Hex(parse("{\"a\":1,\"b\":2}")), sha256Hex(parse("{\"b\":2,\"a\":1}")));
    }

    @Test
    void arrayOrderChangesTheHash() {
        assertNotEquals(sha256Hex(parse("[1,2]")), sha256Hex(parse("[2,1]")));
    }

    @Test
    void numbersStayAsTheSourceSpelledThem() {
        assertNotEquals(sha256Hex(parse("{\"n\":1}")), sha256Hex(parse("{\"n\":1.0}")));
        assertEquals("{\"n\":1.10}", canonicalize(parse("{\"n\":1.10}")));
        assertEquals("{\"n\":12345678901234567890123}", canonicalize(parse("{\"n\":12345678901234567890123}")));
    }

    @Test
    void aUnicodeEscapeAndTheCharacterItselfAreTheSameContent() {
        assertEquals(sha256Hex(parse("{\"s\":\"\\u00e9\"}")), sha256Hex(parse("{\"s\":\"é\"}")));
    }

    @Test
    void theHashIsSha256OfTheCanonicalTextInLowerCaseHex() {
        assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", sha256Hex(parse("{}")));
    }

    @Test
    void anyJsonValueHasACanonicalForm() {
        assertEquals("\"text\"", canonicalize(parse("\"text\"")));
        assertEquals("null", canonicalize(parse("null")));
    }

    @Test
    void aJavaNullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> canonicalize(null));
        assertThrows(IllegalArgumentException.class, () -> sha256Hex(null));
    }

    @Test
    void numbersAreWrittenInPlainDecimalFormWhateverExponentTheSourceUsed() {
        assertEquals("{\"n\":100}", canonicalize(parse("{\"n\":1E+2}")));
        assertEquals("{\"n\":100}", canonicalize(parse("{\"n\":1e2}")));
        assertEquals("{\"n\":0.0000001}", canonicalize(parse("{\"n\":0.0000001}")));
        assertEquals("{\"n\":0.0000001}", canonicalize(parse("{\"n\":1E-7}")));
        assertEquals("{\"n\":1000000000000000000000}", canonicalize(parse("{\"n\":1e21}")));
        assertEquals("{\"n\":-12.50}", canonicalize(parse("{\"n\":-12.50}")));
    }
}
