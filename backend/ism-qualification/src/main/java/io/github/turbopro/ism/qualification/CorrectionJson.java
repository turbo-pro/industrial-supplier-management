package io.github.turbopro.ism.qualification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;

final class CorrectionJson {
    private static final ObjectMapper JSON=new ObjectMapper();
    private CorrectionJson(){}
    static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    static List<String> read(String value){try{return value==null?List.of():List.copyOf(JSON.readValue(value,new TypeReference<List<String>>(){}));}catch(Exception e){throw new IllegalStateException("Invalid stored correction list",e);}}
    static String write(List<String> values){try{return JSON.writeValueAsString(values);}catch(Exception e){throw new IllegalStateException(e);}}
}
