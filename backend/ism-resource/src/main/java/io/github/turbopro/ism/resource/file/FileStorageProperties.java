package io.github.turbopro.ism.resource.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix="ism.storage")
public class FileStorageProperties {
    private String provider="LOCAL";private Path localRoot=Path.of("./data/storage");private int chunkSize=5*1024*1024;private long maxFileSize=100L*1024*1024;private Duration uploadTtl=Duration.ofHours(24);
    public String getProvider(){return provider;}public void setProvider(String provider){this.provider=provider;}
    public Path getLocalRoot(){return localRoot;}public void setLocalRoot(Path localRoot){this.localRoot=localRoot;}
    public int getChunkSize(){return chunkSize;}public void setChunkSize(int chunkSize){this.chunkSize=chunkSize;}
    public long getMaxFileSize(){return maxFileSize;}public void setMaxFileSize(long maxFileSize){this.maxFileSize=maxFileSize;}
    public Duration getUploadTtl(){return uploadTtl;}public void setUploadTtl(Duration uploadTtl){this.uploadTtl=uploadTtl;}
}
