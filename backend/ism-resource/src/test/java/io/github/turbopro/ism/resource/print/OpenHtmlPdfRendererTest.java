package io.github.turbopro.ism.resource.print;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlPdfRendererTest {
    @Test void rendersMultiPageChineseDocumentWithPdfSignature() throws Exception {
        Path font=findFont();PrintProperties properties=new PrintProperties();properties.setFontPath(font);
        String rows="<tr><td>合格供应商</td><td>危险化学品包装材料</td></tr>".repeat(90);
        byte[] pdf=new OpenHtmlPdfRenderer(properties).render("""
            <html><head><meta charset="UTF-8"/><style>th,td{border:1px solid #94a3b8;padding:6px;} thead{display:table-header-group;}</style></head>
            <body><h1>供应商准入评审单</h1><table><thead><tr><th>供应商</th><th>供货范围</th></tr></thead><tbody>%s</tbody></table></body></html>
            """.formatted(rows),"A4","PORTRAIT","工业供应商管理系统");
        assertThat(new String(pdf,0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");assertThat(pdf.length).isGreaterThan(10_000);
        String output=System.getProperty("ism.print.test-output");if(output!=null)Files.write(Path.of(output),pdf);
    }
    private Path findFont(){return java.util.stream.Stream.of(System.getenv("ISM_PRINT_FONT_PATH"),"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf","/System/Library/Fonts/Supplemental/Arial.ttf").filter(java.util.Objects::nonNull).map(Path::of).filter(Files::isRegularFile).findFirst().orElseThrow(()->new IllegalStateException("测试环境缺少标准 TrueType 字体"));}
}
