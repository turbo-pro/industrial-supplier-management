package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalQualificationServiceTest {
    final PortalQualificationMapper mapper=mock(PortalQualificationMapper.class);
    final PortalModuleAccessGate modules=mock(PortalModuleAccessGate.class);
    final PortalWriteGuard guard=mock(PortalWriteGuard.class);
    final QualificationFileContent files=mock(QualificationFileContent.class);
    final AuditService audit=mock(AuditService.class);
    final PortalQualificationService service=new PortalQualificationService(mapper,modules,guard,files,audit);
    @Test void noIdentityCannotReadOrEnumerate(){
        try(var tenant=TenantContext.open(1,2)){
            assertThatThrownBy(()->service.list("","",0,10)).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.detail(10)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper,modules,files);
    }
    @Test void moduleDenialAndMissingBusinessRecordNeverOpenStorage(){
        try(var tenant=TenantContext.open(1,2);var portal=PortalIdentityContext.open(1,2,3,4)){
            doThrow(new ApiException(CommonErrorCode.FORBIDDEN)).when(modules).requireAvailable("QUALIFICATION");
            assertThatThrownBy(()->service.download(10)).isInstanceOf(ApiException.class);
            verifyNoInteractions(mapper,files);
            reset(modules);
            when(mapper.fileId(1,3,10)).thenReturn(null);
            assertThatThrownBy(()->service.download(10)).isInstanceOf(ApiException.class);
            verify(mapper).fileId(1,3,10);verifyNoInteractions(files,audit);
        }
    }
    @Test void auditFailureClosesAlreadyOpenedStream()throws Exception{
        var stream=mock(InputStream.class);when(mapper.fileId(1,3,10)).thenReturn(90L);
        when(files.open(90)).thenReturn(new QualificationFileContent.Download("certificate.pdf",5,stream));
        doThrow(new IllegalStateException("audit failed")).when(audit).append(any());
        try(var tenant=TenantContext.open(1,2);var portal=PortalIdentityContext.open(1,2,3,4)){
            assertThatThrownBy(()->service.download(10)).isInstanceOf(IllegalStateException.class).hasMessage("audit failed");
        }
        verify(stream).close();
    }
    @Test void downloadRevalidatesBeforeStorageAndLeavesSuccessfulStreamForHttpWriter()throws Exception{
        var stream=mock(InputStream.class);when(mapper.fileId(1,3,10)).thenReturn(90L);
        when(files.open(90)).thenReturn(new QualificationFileContent.Download("certificate.pdf",5,stream));
        try(var tenant=TenantContext.open(1,2);var portal=PortalIdentityContext.open(1,2,3,4)){
            assertThat(service.download(10).stream()).isSameAs(stream);
        }
        var order=inOrder(guard,modules,mapper,files,audit);order.verify(guard).lockCurrent();order.verify(modules).requireAvailable("QUALIFICATION");order.verify(mapper).fileId(1,3,10);order.verify(files).open(90);order.verify(audit).append(any());
        verify(stream,never()).close();
    }
}
