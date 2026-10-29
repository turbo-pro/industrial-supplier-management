package io.github.turbopro.ism.bootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class SiteInitializationBrowserFixture extends SiteInitializationSupport {
    @LocalServerPort int port;
    @Test void serveBrowserAndAssertStructures()throws Exception{
        Path control=Path.of(System.getProperty("ism.browser.fixture-dir")).toRealPath();
        consoleUsers();long single=pending(),multi=pending(), untouched=pending();
        Files.writeString(control.resolve("fixture.json"),"{\"single\":\""+single+"\",\"multi\":\""+multi+"\"}");
        Files.writeString(control.resolve("ready"),"http://127.0.0.1:"+port);
        long deadline=System.nanoTime()+Duration.ofMinutes(6).toNanos();
        while(!Files.exists(control.resolve("finish"))&&System.nanoTime()<deadline){assertThat(Files.exists(control.resolve("abort"))).isFalse();Thread.sleep(250);}
        assertThat(Files.exists(control.resolve("finish"))).isTrue();assertStructure(single,true);assertStructure(multi,false);assertThat(count("iam_organization",untouched)).isZero();
    }
}
