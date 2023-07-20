package com.box.l10n.mojito.converter;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.ZonedDateTimeJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.joda.time.DateTime;

import java.sql.Types;
import java.time.ZonedDateTime;

public class JodaDateTimeJavaType extends AbstractClassJavaType<DateTime> {
    public static final JodaDateTimeJavaType INSTANCE = new JodaDateTimeJavaType();

    public JodaDateTimeJavaType() {
        super(DateTime.class);
    }

    @Override
    public <X> X unwrap(DateTime value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return ZonedDateTimeJavaType.INSTANCE.unwrap(value.toGregorianCalendar().toZonedDateTime(), type, options);
    }

    @Override
    public <X> DateTime wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        ZonedDateTime wrap = ZonedDateTimeJavaType.INSTANCE.wrap(value, options);
        return new DateTime(wrap.toInstant().toEpochMilli());
    }

    @Override
    public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
        return indicators.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .getDescriptor(Types.TIMESTAMP);
    }

    @Override
    public String toString(DateTime value) {
        ZonedDateTime dateTime = value.toGregorianCalendar().toZonedDateTime();
        return ZonedDateTimeJavaType.INSTANCE.toString(dateTime);
    }

    public DateTime fromString(CharSequence string) {
        ZonedDateTime dateTime = ZonedDateTimeJavaType.INSTANCE.fromString(string);
        return new DateTime(dateTime.toInstant().toEpochMilli());
    }
}

