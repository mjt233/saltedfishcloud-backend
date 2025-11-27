package com.xiaotao.saltedfishcloud.helper.http;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;

/**
 * 用于解析contentType为multipart/form-data的http请求
 */
public class HttpMultipartRequestParser {

    @FunctionalInterface
    public interface MultipartItemHandler {
        void process(MultipartItem item) throws IOException;
    }

    /**
     * multipart item
     * @param name  表单key
     * @param fileName 文件名（如果有的话）
     * @param headers   附带的header
     * @param inputStream   该项表单数据内容的输入流
     */
    public record MultipartItem(
            String name,
            String fileName,
            Map<String, String> headers,
            InputStream inputStream
    ) {
    }

    /**
     * 原始的servlet request
     */
    @Getter
    private final HttpServletRequest request;

    private final int bufferSize;

    private HttpMultipartRequestParser(HttpServletRequest request, int bufferSize) {
        this.request = request;
        this.bufferSize = bufferSize;
    }

    /**
     * 读取边界标识boundary
     */
    public String getBoundary() {
        return Optional.ofNullable(request.getContentType())
                .map(contentType -> contentType.split("boundary="))
                .filter(parts -> parts.length > 1)
                .map(parts -> parts[1])
                .orElseThrow(() -> new IllegalArgumentException("boundary header not found or illegal:" + request.getContentType()));
    }

    /**
     * 从一个HttpServletRequest创建解析器
     */
    public static HttpMultipartRequestParser create(HttpServletRequest request, int bufferSize) {
        return new HttpMultipartRequestParser(request, bufferSize);
    }

    /**
     * 开始解析请求，读取servlet的输入流
     * @param handler  解析完一项表单数据的header头定义时会调用一次该函数
     */
    public void start(MultipartItemHandler handler) throws IOException {
        String boundary = this.getBoundary();
        String requiredFirstLine = "--" + boundary;

        // 创建边界读取流
        BoundaryBufferInputStream bufferInputStream = new BoundaryBufferInputStream(
                request.getInputStream(),
                bufferSize,
                ("\r\n--" + boundary + "\r\n").getBytes(),
                ("\r\n--" + boundary + "--").getBytes()
        );
        try(bufferInputStream) {
            // 读取第一行，并验证multipart/form-data的http请求体报文格式是否正确
            String actualFirstLine = bufferInputStream.readLine();
            if (!Objects.equals(requiredFirstLine, actualFirstLine)) {
                throw new IllegalArgumentException(
                        "multipart/form-data request body format error, expect:"
                                + requiredFirstLine
                                + " actual:" + actualFirstLine);
            }

            while (!bufferInputStream.isEof()) {
                Map<String, String> headers = new HashMap<>();
                String line;
                // 读取header，直到遇到空行
                while (!"".equals(line = bufferInputStream.readLine())) {
                    String[] s = line.split(": ", 2);
                    headers.put(s[0], s[1]);
                }
                ContentDisposition contentDisposition = Optional.ofNullable(headers.get("Content-Disposition"))
                        .filter(StringUtils::hasText)
                        .map(this::parseContentDisposition)
                        .orElseGet(ContentDisposition::new);

                handler.process(new MultipartItem(contentDisposition.getName(), contentDisposition.getFileName(), headers, bufferInputStream));
                bufferInputStream.nextBoundary();
            }
        }

    }

    @Data
    private static class ContentDisposition {
        String type;
        String name;
        String fileName;
    }

    private ContentDisposition parseContentDisposition(String contentDisposition) {
        if (!StringUtils.hasText(contentDisposition)) {
            return null;
        }
        ContentDisposition res = new ContentDisposition();
        Map<String, String> attrMap = new HashMap<>();
        for (String s : contentDisposition.split("; ")) {
            String[] kv = s.split("=");
            if (kv.length == 1) {
                res.setType(kv[0]);
            } else {
                attrMap.put(kv[0], kv[1]);
            }
        }
        Optional.ofNullable(attrMap.get("name")).ifPresent(name -> res.setName(name.substring(1, name.length() - 1)));
        Optional.ofNullable(attrMap.get("filename*"))
                .map(encodedFormatFilename -> {
                    String[] s = encodedFormatFilename.split("''", 2);
                    String charset = s[0];
                    String encodedFilename = s[1];
                    return URLDecoder.decode(encodedFilename, Charset.forName(charset));
                })
                .or(() -> Optional.ofNullable(attrMap.get("filename")).map(filename -> filename.substring(1, filename.length() - 1)))
                .ifPresent(res::setFileName);
        return res;
    }
}
