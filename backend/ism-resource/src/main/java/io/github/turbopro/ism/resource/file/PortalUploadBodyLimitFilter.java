package io.github.turbopro.ism.resource.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

/** Bound chunk bodies before Spring's byte[] converter, including chunked/unknown-length requests. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+50)
public class PortalUploadBodyLimitFilter extends OncePerRequestFilter {
    static final int LIMIT=1024*1024;
    private final ObjectMapper json;private final ApiResponseFactory responses;
    public PortalUploadBodyLimitFilter(ObjectMapper json,ApiResponseFactory responses){this.json=json;this.responses=responses;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getMethod().equals("PUT")||!request.getRequestURI().matches("/api/portal/certificate-uploads/[^/]+/chunks/[^/]+");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(request.getContentLengthLong()>LIMIT){reject(response);return;}
        byte[] bytes=request.getInputStream().readNBytes(LIMIT+1);
        if(bytes.length>LIMIT){reject(response);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){
                @Override public int read(){return input.read();}
                @Override public int read(byte[] buffer,int offset,int length){return input.read(buffer,offset,length);}
                @Override public boolean isFinished(){return input.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Blocking upload endpoint");}
            };}
        },response);
    }
    private void reject(HttpServletResponse response)throws IOException{response.setStatus(413);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");json.writeValue(response.getOutputStream(),responses.failure(FileErrorCode.FILE_TOO_LARGE,"门户单个上传分片不能超过1MiB"));}
}
