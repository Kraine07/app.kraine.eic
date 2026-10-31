package app.kraine.eic.config;


/** Shared CSV helpers, so the audit export and the report export defuse and quote values the same way. */
public final class Csv {

    private Csv() {
    }

    /**
     * One value, always quoted. A value that starts with = + - @ is run as a formula by Excel and others
     * (CSV injection), so it gets a leading apostrophe.
     */
    public static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}