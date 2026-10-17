package app.kraine.eic.audit;

/** UA-25: everything that is written to the audit trail. */
public enum AuditEventType {

    LOGIN_SUCCESS("Login succeeded"),
    LOGIN_FAILED("Login failed"),
    LOGIN_BLOCKED("Login blocked (suspended account)"),
    LOGOUT("Logout"),
    ACCOUNT_LOCKED_OUT("Account locked out"),
    PASSWORD_CHANGED("Password changed"),
    USER_CREATED("User created"),
    USER_UPDATED("User updated"),
    USER_SUSPENDED("User suspended"),
    USER_ACTIVATED("User activated"),
    USER_DELETED("User deleted"),
    FIRST_ADMIN_CREATED("First administrator created"),

    EQUIPMENT_CREATED("Equipment created"),
    EQUIPMENT_UPDATED("Equipment updated"),
    EQUIPMENT_DELETED("Equipment deleted"),

    PROPERTY_CREATED("Property created"),
    PROPERTY_UPDATED("Property updated"),
    PROPERTY_DELETED("Property deleted"),

    LOCATION_CREATED("Location created"),
    LOCATION_UPDATED("Location updated"),
    LOCATION_DELETED("Location deleted"),

    MANUFACTURER_CREATED("Manufacturer created"),
    MANUFACTURER_UPDATED("Manufacturer updated"),
    MANUFACTURER_DELETED("Manufacturer deleted"),

    MODEL_CREATED("Model created"),
    MODEL_UPDATED("Model updated"),
    MODEL_DELETED("Model deleted");

    private final String label;

    AuditEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
