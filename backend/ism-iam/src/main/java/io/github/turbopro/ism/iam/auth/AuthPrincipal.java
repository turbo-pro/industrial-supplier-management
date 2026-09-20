package io.github.turbopro.ism.iam.auth;

public record AuthPrincipal(long userId, long tenantId, String username, int tokenVersion,
                            boolean passwordChangeRequired) {
}
