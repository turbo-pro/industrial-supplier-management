package io.github.turbopro.ism.iam.site;
/** Explicit opt-in routes. Extending this list requires a real consumer and authorization tests. */
public final class SiteContextRoutes {
    private SiteContextRoutes(){}
    public static boolean supports(String method,String path){return "/api/site-context".equals(path)&&("GET".equals(method)||"HEAD".equals(method));}
}
