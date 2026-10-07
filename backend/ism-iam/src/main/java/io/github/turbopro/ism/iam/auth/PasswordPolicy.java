package io.github.turbopro.ism.iam.auth;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static boolean isStrong(String password) {
        return password != null && password.length() >= 12 && password.length() <= 128
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }
}
