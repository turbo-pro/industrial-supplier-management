package io.github.turbopro.ism.resource.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.infrastructure.web.ApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.http.HttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class PortalUploadBodyLimitFilterTest {
    final PortalUploadBodyLimitFilter filter=new PortalUploadBodyLimitFilter(new ObjectMapper().findAndRegisterModules(),new ApiResponseFactory());
    MockHttpServletRequest request(byte[] bytes){var request=new MockHttpServletRequest("PUT","/api/portal/certificate-uploads/123/chunks/0");request.setContent(bytes);return request;}
    @Test void advertisedOversizeIsRejectedBeforeConversion()throws Exception{
        var response=new MockHttpServletResponse();filter.doFilter(request(new byte[1024*1024+1]),response,(a,b)->{throw new AssertionError("oversize reached handler");});assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test void unknownLengthCannotBypassBodyBound()throws Exception{
        var request=new MockHttpServletRequest("PUT","/api/portal/certificate-uploads/123/chunks/0"){@Override public long getContentLengthLong(){return -1;}@Override public int getContentLength(){return -1;}};request.setContent(new byte[1024*1024+1]);
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(a,b)->{throw new AssertionError("chunked oversize reached handler");});assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test void exactLimitIsPreservedForTheHttpHandler()throws Exception{
        byte[] bytes=new byte[1024*1024];bytes[0]=71;var response=new MockHttpServletResponse();
        filter.doFilter(request(bytes),response,(a,b)->assertThat(((HttpServletRequest)a).getInputStream().readAllBytes()).isEqualTo(bytes));assertThat(response.getStatus()).isEqualTo(200);
    }
    @Test void otherEndpointsAreNotReadOrLimited()throws Exception{
        byte[] bytes=new byte[1024*1024+1];var request=request(bytes);request.setRequestURI("/api/files/uploads/123/chunks/0");
        filter.doFilter(request,new MockHttpServletResponse(),(a,b)->assertThat(((HttpServletRequest)a).getInputStream().readAllBytes()).isEqualTo(bytes));
    }
}
