package app.kraine.eic.equipmentManagement;


import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A model is identified by (manufacturer id, description) and a location by (property id, name). A
 * drop-down needs one value per entry, so the pair is packed into a single URL-safe token and unpacked
 * again on the way back. The name can contain any characters, so it is encoded rather than joined.
 */
public final class CompositeKeys {

    private CompositeKeys() {
    }

    public record Key(Long id, String text) {
    }

    public static String encode(Long id, String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((id + "|" + text).getBytes(StandardCharsets.UTF_8));
    }

    /** The pair packed by {@link #encode}, or null if the token is empty or was not produced by it. */
    public static Key decode(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String plain = new String(Base64.getUrlDecoder().decode(key.trim()), StandardCharsets.UTF_8);
            int bar = plain.indexOf('|');
            if (bar < 1) {
                return null;
            }
            return new Key(Long.valueOf(plain.substring(0, bar)), plain.substring(bar + 1));
        } catch (IllegalArgumentException ex) { // bad Base64 or a non-numeric id
            return null;
        }
    }
}