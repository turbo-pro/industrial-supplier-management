package io.github.turbopro.ism.resource.file;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    public LocalObjectStorage(FileStorageProperties properties){this.root=properties.getLocalRoot().toAbsolutePath().normalize();}
    public String provider(){return "LOCAL";}
    public void writeChunk(long tenantId,long uploadId,int index,byte[] content){try{Path file=chunk(tenantId,uploadId,index);Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"chunk-",".tmp");Files.write(temporary,content);move(temporary,file);}catch(IOException e){throw new FileStorageException("分片写入失败",e);}}
    public StoredObject complete(long tenantId,long uploadId,int totalChunks,String expectedSha256,long expectedSize){
        Path temporary=null;try{Path objectDirectory=root.resolve("objects").resolve(Long.toString(tenantId)).resolve(expectedSha256.substring(0,2));Files.createDirectories(objectDirectory);temporary=Files.createTempFile(objectDirectory,"assemble-",".tmp");MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;try(OutputStream output=Files.newOutputStream(temporary)){for(int i=0;i<totalChunks;i++){Path chunk=chunk(tenantId,uploadId,i);if(!Files.isRegularFile(chunk))throw new FileStorageException("上传分片不完整");try(InputStream input=Files.newInputStream(chunk)){byte[] buffer=new byte[64*1024];int read;while((read=input.read(buffer))>=0){if(read==0)continue;output.write(buffer,0,read);digest.update(buffer,0,read);size+=read;}}}}String actual=HexFormat.of().formatHex(digest.digest());if(size!=expectedSize||!actual.equals(expectedSha256)){Files.deleteIfExists(temporary);throw new FileStorageException("文件大小或 SHA-256 校验失败");}String key="objects/"+tenantId+"/"+expectedSha256.substring(0,2)+"/"+expectedSha256;Path target=safe(key);move(temporary,target);discardUpload(tenantId,uploadId);return new StoredObject(key,size,actual);}catch(FileStorageException e){throw e;}catch(Exception e){if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}throw new FileStorageException("文件合并失败",e);}}
    public StoredObject storeGenerated(long tenantId,String sha256,byte[] content){
        try{String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));if(!actual.equals(sha256))throw new FileStorageException("生成文件 SHA-256 校验失败");String key="objects/"+tenantId+"/"+sha256.substring(0,2)+"/"+sha256;Path target=safe(key);Files.createDirectories(target.getParent());Path temporary=Files.createTempFile(target.getParent(),"generated-",".tmp");try{Files.write(temporary,content);move(temporary,target);}catch(Exception e){Files.deleteIfExists(temporary);throw e;}return new StoredObject(key,content.length,actual);}catch(FileStorageException e){throw e;}catch(Exception e){throw new FileStorageException("生成文件写入失败",e);}
    }
    public InputStream open(String objectKey){try{return Files.newInputStream(safe(objectKey));}catch(IOException e){throw new FileStorageException("文件内容不存在",e);}}
    public void discardUpload(long tenantId,long uploadId){Path directory=root.resolve("uploads").resolve(Long.toString(tenantId)).resolve(Long.toString(uploadId));if(!Files.exists(directory))return;try(var paths=Files.walk(directory)){paths.sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private Path chunk(long tenantId,long uploadId,int index){return safe("uploads/"+tenantId+"/"+uploadId+"/"+index+".part");}
    private Path safe(String key){Path path=root.resolve(key).normalize();if(!path.startsWith(root))throw new FileStorageException("非法存储路径");return path;}
    private void move(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}}
}
