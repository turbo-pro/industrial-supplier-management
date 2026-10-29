package io.github.turbopro.ism.iam.site;
/** Explicit opt-in routes. Extending this list requires a real consumer and authorization tests. */
public final class SiteContextRoutes {
    private SiteContextRoutes(){}
    public static boolean supports(String method,String path){if("/api/site-context".equals(path))return "GET".equals(method)||"HEAD".equals(method);
        if("/api/site-projects/access".equals(path))return "GET".equals(method)||"HEAD".equals(method);
        if("/api/site-projects".equals(path))return java.util.Set.of("GET","HEAD","POST").contains(method);
        if(path.matches("/api/site-projects/[1-9][0-9]{0,18}"))return java.util.Set.of("GET","HEAD","PUT").contains(method);
        return path.matches("/api/site-projects/[1-9][0-9]{0,18}/status")&&"POST".equals(method);}
}
