package io.github.turbopro.ism.qualification;

import java.time.*;
import java.util.List;

public final class PortalQualificationModels {
    private PortalQualificationModels(){}
    public enum Status { VALID, EXPIRING, EXPIRED, REVOKED, SUPERSEDED }
    // Deliberately exclude organization/employee ids, file ids, audit and internal review comments.
    public record Certificate(String id,String typeCode,String typeName,String certificateNo,String issuingAuthority,
        LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,Status status,
        Integer daysUntilExpiry,LocalDateTime updatedAt,int version) {}
    public record Page(long total,int page,int size,List<Certificate> items) {}
}
