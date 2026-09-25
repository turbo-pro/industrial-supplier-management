package io.github.turbopro.ism.supplier;

import java.time.*;

final class RestrictionBusinessDate {
    private RestrictionBusinessDate() {}
    static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
}
