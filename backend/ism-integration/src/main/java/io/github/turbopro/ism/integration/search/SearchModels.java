package io.github.turbopro.ism.integration.search;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Set;

public final class SearchModels {
    private SearchModels(){}
    public enum EntityType { USER,ORGANIZATION,FILE,PRINT_TEMPLATE,MESSAGE,TASK }
    public record SearchRequest(@Size(max=100) String keyword,Set<EntityType> types,Set<@Size(max=32) String> statuses,
                                LocalDateTime updatedFrom,LocalDateTime updatedTo,@Min(0) @Max(100) int page,@Min(1) @Max(100) int size){
        public SearchRequest{keyword=keyword==null?"":keyword.trim();types=types==null?Set.of():Set.copyOf(types);statuses=statuses==null?Set.of():Set.copyOf(statuses);size=size==0?20:size;}
    }
    public record SearchRow(long id,String entityType,String title,String subtitle,String status,String route,LocalDateTime updatedAt,Long organizationId,Long ownerId){}
    public record SearchItem(String id,EntityType type,String title,String subtitle,String status,String route,LocalDateTime updatedAt){}
    public record SearchResult(boolean hasMore,int page,int size,Set<EntityType> searchedTypes,java.util.List<SearchItem> items){}
    public record SaveSearch(@NotBlank @Size(max=100) String name,@NotNull @Valid SearchRequest query,boolean defaultSearch,@PositiveOrZero int version){}
    public record SavedRow(long id,String searchName,String queryJson,boolean defaultSearch,int version,LocalDateTime updatedAt){}
    public record SavedView(String id,String name,SearchRequest query,boolean defaultSearch,int version,LocalDateTime updatedAt){}
}
