package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.qualification.QualificationFileReference;
import org.springframework.stereotype.Service;

@Service
public class QualificationFileReferenceAdapter implements QualificationFileReference {
    private final FileReferenceService files;
    public QualificationFileReferenceAdapter(FileReferenceService files){this.files=files;}
    @Override public boolean active(long fileId){return files.active(fileId);}
}
