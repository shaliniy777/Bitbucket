/*
 * Copyright (c) Experian, 2020. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

/*
 * [SWAGGER] Refer to the Swagger section of the readme.md for the swagger API specification. This class does not
 * form part of the API specification.
 */

import com.experian.eda.casemanagement.generated.model.CreatedCommentData;
import com.experian.saas.service.wrapper.logging.ExpLogger;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Size;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A collection of utilities for the common service functionality.
 */
@UtilityClass
public class ServicesUtil {
    private static final ExpLogger LOGGER = new ExpLogger(ServicesUtil.class);

    private static final int CONTENT_SIZE_MAX_DEFAULT = 2000;

    public static final int CONTENT_SIZE_MAX
            = getAnnotationSizeMax(CreatedCommentData.class, "getContent", CONTENT_SIZE_MAX_DEFAULT); // NOI18N

    private String bpsStaticInternalDateFormat;

    private DateTimeFormatter cmDateFormatter;

    static int getAnnotationSizeMax(Class<?> clazz, String methodName, int defaultSize) {
        try {
            Method getContent = clazz.getDeclaredMethod(methodName);
            Size annotation = getContent.getAnnotation(Size.class);
            int max = annotation.max();
            return maxNotSet(max) ? defaultSize : max;
        } catch (Exception ex) {
            LOGGER.warn("Cannot determine the maximum permitted size for the comment content text so defaulting to {}",  // NOI18N
                    defaultSize, ex);
            return defaultSize;
        }
    }

    private static boolean maxNotSet(int max) {
        return max == Integer.MAX_VALUE;
    }

    /**
     * Convert Characteristic values to a strong typed value
     * Note that this is not the same as Data Definition strong typed
     * Instead of throwing INTERNAL_SERVER_ERROR (500), return BPS response value
     *
     * @param value a {@link java.lang.Object} object
     * @param dataType a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public Object toStrongTypedCharacteristics(Object value, String dataType) {
        if (null == value) {
            return null;
        }

        String stringValue = String.valueOf(value);

        try {
            switch (dataType) {
                case "NumericInteger": // NOI18N
                    return new BigInteger(stringValue);
                case "Numeric": // NOI18N
                case "BigDecimal": // NOI18N
                    return new BigDecimal(stringValue);
                case "Boolean": // NOI18N
                    return Boolean.valueOf(stringValue);
                case "Date": // NOI18N
                    ZonedDateTime dateTime = ZonedDateTime.from(
                            LocalDate.parse(stringValue, DateTimeFormatter.ofPattern(bpsStaticInternalDateFormat))
                                    .atStartOfDay(ZoneId.of("Z"))); // NOI18N
                    return cmDateFormatter.format(dateTime);
                case "String": // NOI18N
                case "Any": // NOI18N
                default:
                    return value;
            }
        } catch (Exception ex) {
            LOGGER.warn("Unable to convert value [{}] to data type [{}] due to {}", value, dataType, ex.getLocalizedMessage(), ex); // NOI18N
            return value;
        }
    }

    @Component
    class InjectValueInStaticFieldServicesUtil {
        @Value("${case-management.datetime-format}")
        private String cmDateFormat;

        @Value("${case-management.bps-internal-date-format}")
        private String bpsInternalDateFormat;

        @PostConstruct
        private void init() {
            ServicesUtil.bpsStaticInternalDateFormat = bpsInternalDateFormat;
            ServicesUtil.cmDateFormatter = DateTimeFormatter.ofPattern(cmDateFormat);
        }
    }
}
