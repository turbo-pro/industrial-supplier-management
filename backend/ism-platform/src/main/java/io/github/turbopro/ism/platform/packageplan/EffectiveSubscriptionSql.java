package io.github.turbopro.ism.platform.packageplan;

/** Platform-owned subscription resolution, using UTC and a half-open validity window. Alias: t. */
public final class EffectiveSubscriptionSql {
    private EffectiveSubscriptionSql() {}

    public static final String VERSION = """
        COALESCE((SELECT CASE WHEN sv.id IS NOT NULL AND (s.effective_to IS NULL OR s.effective_to > UTC_TIMESTAMP(3))
            THEN s.package_version_id ELSE NULL END FROM plt_subscription s
          LEFT JOIN plt_package_version sv ON sv.id=s.package_version_id AND sv.status='PUBLISHED'
          WHERE s.tenant_id=t.id AND s.status='ACTIVE'
            AND s.effective_from <= UTC_TIMESTAMP(3)
          ORDER BY s.effective_from DESC,s.assignment_sequence DESC,s.created_at DESC,s.id DESC LIMIT 1),
          CASE WHEN NOT EXISTS (SELECT 1 FROM plt_subscription history WHERE history.tenant_id=t.id)
            THEN (SELECT legacy.id FROM plt_package_version legacy
              WHERE legacy.id=t.package_version_id AND legacy.status='PUBLISHED') ELSE NULL END)
        """;
}
