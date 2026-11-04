package io.github.turbopro.ism.iam.authorization;

import io.github.turbopro.ism.common.api.authorization.ModuleCapabilityCatalog;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class IamModuleCapabilityCatalog implements ModuleCapabilityCatalog {
    private static final int LIMIT=200;
    private final ModuleCapabilityMapper mapper;
    public IamModuleCapabilityCatalog(ModuleCapabilityMapper mapper){this.mapper=mapper;}
    public Impact compare(Set<String> currentModules,Set<String> targetModules){
        Set<String> before=GrantEntitlements.withCoreModules(currentModules);
        Set<String> after=GrantEntitlements.withCoreModules(targetModules);
        var removed=new TreeSet<>(before);removed.removeAll(after);
        var added=new TreeSet<>(after);added.removeAll(before);
        Counts lost=count(removed), gained=count(added);
        long total=lost.menus()+lost.actions()+lost.fields()+gained.menus()+gained.actions()+gained.fields();
        List<Capability> items=new ArrayList<>();
        if(!removed.isEmpty())items.addAll(mapper.items(removed,"REMOVED",LIMIT));
        if(!added.isEmpty()&&items.size()<LIMIT)items.addAll(mapper.items(added,"ADDED",LIMIT-items.size()));
        return new Impact(lost,gained,total,LIMIT,total>items.size(),List.copyOf(items));
    }
    private Counts count(Set<String> modules){
        if(modules.isEmpty())return new Counts(0,0,0);
        Map<String,Long> result=new HashMap<>();mapper.counts(modules).forEach(r->result.put(r.kind(),r.total()));
        return new Counts(result.getOrDefault("MENU",0L),result.getOrDefault("ACTION",0L),result.getOrDefault("FIELD",0L));
    }
}
