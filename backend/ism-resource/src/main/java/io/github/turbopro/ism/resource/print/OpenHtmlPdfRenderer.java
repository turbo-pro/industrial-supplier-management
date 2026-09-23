package io.github.turbopro.ism.resource.print;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

@Component
public class OpenHtmlPdfRenderer implements PdfRenderer {
    private final PrintProperties properties;
    public OpenHtmlPdfRenderer(PrintProperties properties){this.properties=properties;}

    @Override public byte[] render(String html,String pageSize,String orientation,String documentTitle){
        if(properties.getFontPath()==null||!Files.isRegularFile(properties.getFontPath()))throw new IllegalStateException("未配置可用的中文打印字体 ISM_PRINT_FONT_PATH");
        String size=switch(pageSize==null?"A4":pageSize){case "A5"->"A5";case "LETTER"->"letter";default->"A4";};
        String direction="LANDSCAPE".equals(orientation)?"landscape":"portrait";
        String css="""
            <style>
              @page { size: %s %s; margin: 18mm 14mm 18mm 14mm;
                @top-center { content: '%s'; font-family: 'ISM CJK'; font-size: 9pt; color: #64748b; }
                @bottom-center { content: '第 ' counter(page) ' 页 / 共 ' counter(pages) ' 页'; font-family: 'ISM CJK'; font-size: 9pt; color: #64748b; }
              }
              html,body { font-family: 'ISM CJK'; font-size: 10pt; color: #1e293b; }
              table { border-collapse: collapse; width: 100%%; } th,td { page-break-inside: avoid; }
            </style>
            """.formatted(size,direction,cssText(documentTitle));
        String document=injectCss(html,css);
        try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
            PdfRendererBuilder builder=new PdfRendererBuilder();
            builder.useFastMode();builder.useExternalResourceAccessControl((uri,type)->uri.startsWith("data:"),com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);builder.useFont(properties.getFontPath().toFile(),"ISM CJK");builder.withHtmlContent(document,null);builder.toStream(output);builder.run();return output.toByteArray();
        }catch(Exception e){throw new IllegalStateException("PDF 渲染失败",e);}
    }
    private String injectCss(String html,String css){if(html.matches("(?is).*<head[^>]*>.*"))return html.replaceFirst("(?is)<head([^>]*)>","<head$1>"+css);return "<html><head><meta charset=\"UTF-8\"/>"+css+"</head><body>"+html+"</body></html>";}
    private String cssText(String value){return (value==null?"":value).replace("\\","\\\\").replace("'","\\'").replace("\r"," ").replace("\n"," ");}
}
