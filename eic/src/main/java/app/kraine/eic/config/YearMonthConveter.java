package app.kraine.eic.config;

import java.time.format.DateTimeFormatter;

public class YearMonthConveter implements jakarta.persistence.AttributeConverter<java.time.YearMonth, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public String convertToDatabaseColumn(java.time.YearMonth attribute) {
        return attribute != null ? attribute.format(FORMATTER) : null;
    }

    @Override
    public java.time.YearMonth convertToEntityAttribute(String dbData) {
        return dbData != null ? java.time.YearMonth.parse(dbData, FORMATTER) : null;
    }

}
