package io.github.turbopro.ism.resource.print;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix="ism.print")
public class PrintProperties {
    private Path fontPath;
    private boolean workerEnabled=true;
    private Duration leaseDuration=Duration.ofMinutes(5);
    private long pollInterval=2000;
    private String nodeId="print-worker";
    public Path getFontPath(){return fontPath;}public void setFontPath(Path fontPath){this.fontPath=fontPath;}
    public boolean isWorkerEnabled(){return workerEnabled;}public void setWorkerEnabled(boolean workerEnabled){this.workerEnabled=workerEnabled;}
    public Duration getLeaseDuration(){return leaseDuration;}public void setLeaseDuration(Duration leaseDuration){this.leaseDuration=leaseDuration;}
    public long getPollInterval(){return pollInterval;}public void setPollInterval(long pollInterval){this.pollInterval=pollInterval;}
    public String getNodeId(){return nodeId;}public void setNodeId(String nodeId){this.nodeId=nodeId;}
}
