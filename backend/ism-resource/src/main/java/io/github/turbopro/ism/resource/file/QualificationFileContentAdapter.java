package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.qualification.QualificationFileContent;
import org.springframework.stereotype.Service;

@Service
public class QualificationFileContentAdapter implements QualificationFileContent {
    private final FileService files;
    public QualificationFileContentAdapter(FileService files){this.files=files;}
    @Override public Download open(long fileId){
        var result=files.download(fileId);
        return new Download(result.metadata().originalName(),result.metadata().size(),result.stream());
    }
}
