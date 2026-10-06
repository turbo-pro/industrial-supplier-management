package io.github.turbopro.ism.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class PurchaseCategoryModels {
    private PurchaseCategoryModels() {}
    public enum Status { ACTIVE, INACTIVE }
    public record Save(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String code,@NotBlank @Size(max=100) String name,@NotNull Status status,@NotNull @PositiveOrZero Integer version) {}
    public record Category(String id,String code,String name,Status status,int version,LocalDateTime updatedAt) {}
    public record Row(long id,String categoryCode,String categoryName,String status,int version,LocalDateTime updatedAt) {}
    public record Assign(@NotNull @Size(max=20) List<@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String> categoryIds,@NotNull @PositiveOrZero Integer version) {}
    public record Assignment(String supplierId,List<String> categoryIds,int version) {}
    public record RequiredMaterial(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String type,@NotBlank @Size(max=100) String name) {}
    public record SaveRequiredMaterials(@NotNull @Size(max=20) List<@Valid RequiredMaterial> materials,@NotNull @PositiveOrZero Integer version) {}
    public record MaterialPolicy(String categoryId,int version,List<RequiredMaterial> materials) {}
}
