package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import java.util.*;

/** All local exit capabilities must be present; absent checks cannot imply clearance. */
final class ExitReadinessEvaluator {
    private static final List<String> REQUIRED=List.of("OPEN_CONTRACT","OPEN_PROJECT","OPEN_PERSON","OPEN_ASSET",
        "OPEN_SAFETY","OPEN_ATTENDANCE","OPEN_QUALITY","OPEN_IMPROVEMENT");
    private ExitReadinessEvaluator(){}
    static SupplierModels.ExitReadiness evaluate(List<SupplierExitCheck> checks,long supplierId){
        var facts=new LinkedHashMap<String,SupplierExitCheck.Blocker>();
        for(var check:checks){
            var supplied=check.blockers(supplierId);
            if(supplied==null)throw invalid();
            for(var fact:supplied){
                if(fact==null||fact.code()==null||!fact.code().matches("[A-Z][A-Z0-9_]{0,63}")
                    ||fact.label()==null||fact.label().length()>200||fact.route()==null||fact.route().length()>200
                    ||fact.count()<0||facts.putIfAbsent(fact.code(),fact)!=null)throw invalid();
            }
        }
        for(var code:REQUIRED)if(!facts.containsKey(code))facts.put("MISSING_"+code,
            new SupplierExitCheck.Blocker("MISSING_"+code,"退出核验能力未配置："+code,1,"/suppliers/master"));
        boolean financial=facts.containsKey("OPEN_FINANCIAL"),unknown=facts.containsKey("FINANCIAL_UNKNOWN");
        if(financial&&unknown)throw invalid();
        if(unknown&&facts.get("FINANCIAL_UNKNOWN").count()==0)throw invalid();
        if(!financial&&!unknown)facts.put("FINANCIAL_UNKNOWN",
            new SupplierExitCheck.Blocker("FINANCIAL_UNKNOWN","财务核验能力未配置",1,"/suppliers/master"));
        var blockers=List.copyOf(facts.values());
        return new SupplierModels.ExitReadiness(blockers.stream().allMatch(f->f.count()==0),blockers);
    }
    private static ApiException invalid(){return new ApiException(CommonErrorCode.VALIDATION_FAILED,"退出核验能力配置冲突或结果无效");}
}
