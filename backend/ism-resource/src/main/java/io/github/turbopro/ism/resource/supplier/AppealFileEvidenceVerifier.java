package io.github.turbopro.ism.resource.supplier;

import io.github.turbopro.ism.supplier.AppealEvidenceVerifier;
import io.github.turbopro.ism.resource.file.FileReferenceService;
import org.springframework.stereotype.Service;

@Service
public class AppealFileEvidenceVerifier implements AppealEvidenceVerifier {
    private final FileReferenceService files;
    public AppealFileEvidenceVerifier(FileReferenceService files){this.files=files;}
    @Override public boolean available(long fileId){return files.active(fileId);}
}
