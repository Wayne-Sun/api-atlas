package com.api.atlas.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MyBatis interceptor that automatically populates audit fields
 * ({@code createdBy}, {@code createdAt}, {@code lastModifiedBy}, {@code updatedAt})
 * on INSERT and UPDATE operations.
 * <p>
 * Registered automatically as a {@link Component} — MyBatis-Spring-Boot
 * auto-discovers all {@link Interceptor} beans.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_MODIFIED_BY = "lastModifiedBy";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType sqlCommandType = ms.getSqlCommandType();

        // Only handle INSERT and UPDATE
        if (sqlCommandType != SqlCommandType.INSERT && sqlCommandType != SqlCommandType.UPDATE) {
            return invocation.proceed();
        }

        Object parameter = args[1];
        if (parameter == null) {
            return invocation.proceed();
        }

        try {
            String username = SecurityUtil.getCurrentUsername();
            LocalDateTime now = LocalDateTime.now();

            if (parameter instanceof Map) {
                handleMapParameter((Map<?, ?>) parameter, sqlCommandType, username, now);
            } else if (parameter instanceof List) {
                handleListParameter((List<?>) parameter, sqlCommandType, username, now);
            } else {
                handleSingleParameter(parameter, sqlCommandType, username, now);
            }
        } catch (Exception e) {
            // Never let interceptor failures propagate — log and proceed
            log.warn("AuditInterceptor failed to set audit fields: {}", e.getMessage(), e);
        }

        return invocation.proceed();
    }

    private void handleSingleParameter(Object parameter, SqlCommandType sqlCommandType,
                                       String username, LocalDateTime now) {
        MetaObject metaObject = SystemMetaObject.forObject(parameter);
        if (!metaObject.hasGetter(FIELD_CREATED_BY)) {
            return; // entity does not have audit fields
        }
        applyAuditFields(metaObject, sqlCommandType, username, now);
    }

    private void handleListParameter(List<?> parameter, SqlCommandType sqlCommandType,
                                     String username, LocalDateTime now) {
        for (Object item : parameter) {
            if (item == null) continue;
            MetaObject metaObject = SystemMetaObject.forObject(item);
            if (metaObject.hasGetter(FIELD_CREATED_BY)) {
                applyAuditFields(metaObject, sqlCommandType, username, now);
            }
        }
    }

    private void handleMapParameter(Map<?, ?> parameter, SqlCommandType sqlCommandType,
                                    String username, LocalDateTime now) {
        for (Object value : parameter.values()) {
            if (value == null) continue;
            // Skip JDK types that are definitely not entities
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                continue;
            }
            // Could be a Collection (List of entities from @Param)
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item == null) continue;
                    MetaObject metaObject = SystemMetaObject.forObject(item);
                    if (metaObject.hasGetter(FIELD_CREATED_BY)) {
                        applyAuditFields(metaObject, sqlCommandType, username, now);
                    }
                }
                continue;
            }
            // Single entity value
            MetaObject metaObject = SystemMetaObject.forObject(value);
            if (metaObject.hasGetter(FIELD_CREATED_BY)) {
                applyAuditFields(metaObject, sqlCommandType, username, now);
            }
        }
    }

    private static final String FIELD_LAST_MODIFIED_AT = "lastModifiedAt";

    private void applyAuditFields(MetaObject metaObject, SqlCommandType sqlCommandType,
                                  String username, LocalDateTime now) {
        if (sqlCommandType == SqlCommandType.INSERT) {
            safeSetValue(metaObject, FIELD_CREATED_BY, username);
            safeSetValue(metaObject, FIELD_CREATED_AT, now);
            safeSetValue(metaObject, FIELD_LAST_MODIFIED_BY, username);
            safeSetValue(metaObject, FIELD_UPDATED_AT, now);
            safeSetValue(metaObject, FIELD_LAST_MODIFIED_AT, now);
        } else if (sqlCommandType == SqlCommandType.UPDATE) {
            safeSetValue(metaObject, FIELD_LAST_MODIFIED_BY, username);
            safeSetValue(metaObject, FIELD_UPDATED_AT, now);
            safeSetValue(metaObject, FIELD_LAST_MODIFIED_AT, now);
        }
    }

    /**
     * Sets a value on the meta object only if the property has a setter.
     * This gracefully handles entities that use {@code lastModifiedAt} instead
     * of {@code updatedAt} (e.g. the User entity).
     */
    private void safeSetValue(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName)) {
            metaObject.setValue(fieldName, value);
        }
    }
}
