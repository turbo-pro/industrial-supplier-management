package io.github.turbopro.ism.iam.console;

public record ConsolePrincipal(long userId, String username, int tokenVersion,
                               boolean passwordChangeRequired) {
}
