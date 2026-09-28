package io.github.turbopro.ism.integration.table;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class TableViewModels {
    private TableViewModels(){}
    public record Column(@NotBlank String key,boolean visible,@Min(80) @Max(600) int width){}
    public record Definition(String key,String label,boolean required,int width){}
    public record Save(@NotBlank @Size(max=60) String name,@NotNull @Size(min=1,max=50) List<@NotNull @Valid Column> columns,
                       boolean defaultView,@PositiveOrZero int version){}
    public record Row(long id,String viewName,String columnsJson,boolean defaultView,int version,LocalDateTime updatedAt){}
    public record View(String id,String name,List<Column> columns,boolean defaultView,int version,LocalDateTime updatedAt){}
    public record Page(String tableKey,List<Definition> catalog,int maxPersonalViews,List<View> views,
                       List<View> sharedViews,boolean canPublish){}
}
