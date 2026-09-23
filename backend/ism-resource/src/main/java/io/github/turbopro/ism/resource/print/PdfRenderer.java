package io.github.turbopro.ism.resource.print;

public interface PdfRenderer {
    byte[] render(String html,String pageSize,String orientation,String documentTitle);
}
