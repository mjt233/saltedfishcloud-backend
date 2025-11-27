package com.xiaotao.saltedfishcloud.helper.http;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpMultipartRequestParserTest {
    private final static String BOUNDARY = "----WebKitFormBoundaryomtgUKHzLri7d7ZL";

    private HttpServletRequest getRequest() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("multipart/form-data; boundary=" + BOUNDARY);
        request.setContent(new ClassPathResource("request.txt").getContentAsByteArray());
        return request;
    }

    @Test
    public void testParser() throws IOException {
        HttpServletRequest request = getRequest();
        HttpMultipartRequestParser parser = HttpMultipartRequestParser.create(request, 64 * 1024);
        assertEquals(BOUNDARY, parser.getBoundary());
        Map<String, String> form = new HashMap<>();
        parser.start(item -> {
            try {
                form.put(item.name(), StreamUtils.copyToString(item.inputStream(), StandardCharsets.UTF_8));
                if (item.name().equals("file")) {
                    assertEquals("testFile.txt", item.fileName());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(3, form.size());
        assertEquals("asdasdasdasdasdasdasd\r\n\r\n", form.get("file"));
        assertEquals("1760623440000", form.get("mtime"));
        assertEquals("f98f1f95c4091a0cd90a9b904d27e76f", form.get("md5"));
    }
}