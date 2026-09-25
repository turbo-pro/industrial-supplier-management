package io.github.turbopro.ism.safety;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class SafetyAttendanceModels {
    private SafetyAttendanceModels() {}
    public record CheckIn(@NotBlank String personId, @NotBlank @Size(max = 200) String siteName) {}
    public record CheckOut(@Size(max = 500) String note, @NotNull @PositiveOrZero Integer version) {}
    public record Row(long id, long organizationId, long projectId, long supplierId, long personId,
                      String personCode, String personName, String supplierName, String projectName,
                      String siteName, LocalDateTime checkInAt, LocalDateTime checkOutAt,
                      long checkInBy, Long checkOutBy, String checkOutNote, long createdBy, int version) {}
    public record View(String id, String organizationId, String projectId, String supplierId, String personId,
                       String personCode, String personName, String supplierName, String projectName,
                       String siteName, LocalDateTime checkInAt, LocalDateTime checkOutAt,
                       String checkInBy, String checkOutBy, String checkOutNote, int version) {}
    public record Page(long total, int page, int size, List<View> items) {}
}
