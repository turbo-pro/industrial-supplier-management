package io.github.turbopro.ism.supplier;

import java.util.List;

/** Business modules expose exit facts without sharing their tables or mappers. */
public interface SupplierExitCheck {
    List<Blocker> blockers(long supplierId);
    default List<Entity> entities(long supplierId){return List.of();}
    record Entity(String code,long sourceId,String route){}
    static List<Entity> entities(String code,List<Long> ids,String route){
        return ids.stream().map(id->new Entity(code,id,route)).toList();
    }
    record Blocker(String code, String label, long count, String route) {}
}
