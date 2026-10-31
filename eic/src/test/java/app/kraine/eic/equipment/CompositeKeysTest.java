package app.kraine.eic.equipment;

import org.junit.jupiter.api.Test;

import app.kraine.eic.equipmentManagement.CompositeKeys;

import static org.junit.jupiter.api.Assertions.*;

class CompositeKeysTest {

    @Test
    void packsAndUnpacksAnIdAndAName() {
        CompositeKeys.Key key = CompositeKeys.decode(CompositeKeys.encode(12L, "Store Room"));

        assertEquals(12L, key.id());
        assertEquals("Store Room", key.text());
    }

    @Test
    void survivesCharactersThatBreakUrlsAndSeparators() {
        String awkward = "Floor 2/Room | \"A\" & 100% ünï";

        CompositeKeys.Key key = CompositeKeys.decode(CompositeKeys.encode(7L, awkward));

        assertEquals(7L, key.id());
        assertEquals(awkward, key.text());
    }

    @Test
    void theTokenIsSafeInAUrlAndAFormValue() {
        String token = CompositeKeys.encode(1L, "a/b+c d?");

        assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
    }

    @Test
    void rejectsAnythingItDidNotProduce() {
        assertNull(CompositeKeys.decode(null));
        assertNull(CompositeKeys.decode(""));
        assertNull(CompositeKeys.decode("   "));
        assertNull(CompositeKeys.decode("!!! not base64 !!!"));
        assertNull(CompositeKeys.decode(java.util.Base64.getUrlEncoder().encodeToString("abc|Store".getBytes())));
        assertNull(CompositeKeys.decode(java.util.Base64.getUrlEncoder().encodeToString("no separator".getBytes())));
    }
}
