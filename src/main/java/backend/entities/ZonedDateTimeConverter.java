package backend.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Converter(autoApply = true)
public class ZonedDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime attribute) {
        if (attribute == null) return null;
        return Timestamp.from(attribute.toInstant());
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp dbData) {
        if (dbData == null) return null;
        return ZonedDateTime.ofInstant(dbData.toInstant(), ZoneId.systemDefault());
    }
}
