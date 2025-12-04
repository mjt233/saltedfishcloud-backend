package com.xiaotao.saltedfishcloud.servlet;

import com.xiaotao.saltedfishcloud.helper.http.HttpMultipartRequestParser;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

import static com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils.FILE_BUFFER_SIZE;
import static com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils.saveResourceFileStream;

/**
 * "/api/file/upload"
 */
@Component
@Slf4j
public class FileUploadServlet extends HttpServlet {

    @Autowired
    private ResourceService resourceService;

    // 注册路由
    @Bean
    public ServletRegistrationBean<FileUploadServlet> fileUploadServletServletRegistrationBean() {
        return new ServletRegistrationBean<>(this, "/api/file/upload");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            ResourceRequest resourceRequest = Optional.ofNullable(req.getParameter("p"))
                    .filter(StringUtils::hasText)
                    .map(p -> {
                        try {
                            return MapperHolder.parseJson(p, ResourceRequest.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .orElseThrow(() -> new IllegalArgumentException("parameter 'p' is require"));
            HttpMultipartRequestParser.create(req, FILE_BUFFER_SIZE)
                    .start(item -> {
                        if ("file".equals(item.name()) && StringUtils.hasText(item.fileName())) {
                            resourceService.writeResource(resourceRequest, os -> saveResourceFileStream(item.inputStream(), resourceRequest, os));
                        }
                    });
            writeSuccess(resp);
        } catch (Throwable e) {
            log.error("servlet接收文件异常", e);
            int code = HttpStatus.INTERNAL_SERVER_ERROR.value();
            writeError(code, resp, e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int code = HttpStatus.INTERNAL_SERVER_ERROR.value();
        writeError(code, resp, "仅支持POST请求");
    }

    private void writeError(int code, HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json;charset=utf8");
        String body = JsonResultImpl.getInstance(code, code, null, msg).toString();
        resp.getWriter().print(body);
        resp.flushBuffer();
    }

    private void writeSuccess(HttpServletResponse resp) throws IOException {
        int code = HttpStatus.OK.value();
        resp.setStatus(code);
        resp.setContentType("application/json;charset=utf8");
        String body = JsonResult.emptySuccess().getJsonStr();
        resp.getWriter().print(body);
        resp.flushBuffer();
    }
}
