package io.github.turbopro.ism.common.infrastructure.tenant;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class})
})
public class TenantIsolationInterceptor implements Interceptor {
    private static final Pattern TENANT_PREDICATE = Pattern.compile(
            "(?:\\b[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_TENANT_COLUMN = Pattern.compile(
            "insert\\s+into\\s+[^()\\s]+\\s*\\([^)]*\\btenant_id\\b[^)]*\\)", Pattern.CASE_INSENSITIVE);

    private final Map<String, Boolean> tenantScopedNamespaces = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (!isTenantScoped(statement.getId())) {
            return invocation.proceed();
        }

        TenantContext.Identity identity = TenantContext.require();
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = statement.getBoundSql(parameter);
        validateSql(statement.getId(), boundSql.getSql());
        long parameterTenantId = resolveTenantId(boundSql, parameter);
        if (parameterTenantId != identity.tenantId()) {
            throw new TenantIsolationException("Tenant parameter does not match authenticated tenant");
        }
        return invocation.proceed();
    }

    private boolean isTenantScoped(String statementId) {
        String namespace = statementId.substring(0, statementId.lastIndexOf('.'));
        return tenantScopedNamespaces.computeIfAbsent(namespace, this::implementsTenantScope);
    }

    private boolean implementsTenantScope(String namespace) {
        try {
            return TenantScopedMapper.class.isAssignableFrom(Class.forName(namespace));
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private void validateSql(String statementId, String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        boolean safe = normalized.startsWith("insert")
                ? INSERT_TENANT_COLUMN.matcher(normalized).find()
                : containsTenantWherePredicate(normalized);
        if (!safe) {
            throw new TenantIsolationException("Tenant predicate is missing from statement " + statementId);
        }
    }

    private boolean containsTenantWherePredicate(String sql) {
        int where = sql.indexOf(" where ");
        return where >= 0 && TENANT_PREDICATE.matcher(sql.substring(where)).find();
    }

    private long resolveTenantId(BoundSql boundSql, Object parameter) {
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String property = mapping.getProperty();
            if (!property.equals("tenantId") && !property.endsWith(".tenantId")) {
                continue;
            }
            if (parameter instanceof Number number && property.equals("tenantId")) {
                return number.longValue();
            }
            Object value = boundSql.hasAdditionalParameter(property)
                    ? boundSql.getAdditionalParameter(property)
                    : readProperty(parameter, property);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        throw new TenantIsolationException("A bound tenantId parameter is required");
    }

    private Object readProperty(Object parameter, String property) {
        if (parameter == null) {
            return null;
        }
        if (parameter instanceof Map<?, ?> map && map.containsKey(property)) {
            return map.get(property);
        }
        MetaObject metaObject = SystemMetaObject.forObject(parameter);
        return metaObject.hasGetter(property) ? metaObject.getValue(property) : null;
    }
}
