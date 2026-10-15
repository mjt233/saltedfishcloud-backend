package com.sfc.demo.controller;

import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.remote.model.SfcFileInfo;
import com.sfc.cloud.remote.model.SfcJsonResult;
import com.sfc.cloud.trust.SfcTrustHeaders;
import com.sfc.demo.feign.DemoFeign;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微服务示例控制器
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    /** sfc-core 网盘文件操作统一入口（Feign + 流式上传合并为一个 bean） */
    private final SfcCoreFileClient sfcCoreFileClient;
    private final DemoFeign demoFeign;

    @ResponseBody
    @GetMapping("testInternal")
    public String testInternal() {
        return demoFeign.hello();
    }

    /**
     * 回显当前用户。
     *
     * @param request HTTP 请求
     * @return 当前 uid 及其来源说明
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String rawUid = request.getHeader(SfcTrustHeaders.UID);
        result.put("uid", rawUid);
        result.put("uidSource", SfcTrustHeaders.UID);
        result.put("rawUidHeader", rawUid);
        return result;
    }

    @GetMapping("testList")
    public SfcJsonResult<List<SfcFileInfo>> testList(HttpServletRequest request) {
        String rawUid = request.getHeader(SfcTrustHeaders.UID);
        return sfcCoreFileClient.list(Long.parseLong(rawUid), "/");
    }

    @ResponseBody
    @GetMapping("testDownload")
    public String testDownload() throws IOException {
        Path tmpFile = Path.of("test.exe");
        try (Response response = sfcCoreFileClient.download(0, "/dsh-latest-windows-x64.exe");
             InputStream is = response.body().asInputStream();
             OutputStream os = Files.newOutputStream(tmpFile);
        ) {
            StreamUtils.copy(is, os);
        }
//        Files.delete(tmpFile);
        return "ok";
    }

    /**
     * 上传测试：把工作目录下的 test.exe 以流式方式上传到公共网盘根目录。
     *
     * @return 固定提示
     * @throws IOException 读取文件属性失败
     */
    @ResponseBody
    @GetMapping("testUpload")
    public String testUpload() throws IOException {
        Path tmpFile = Path.of("test.exe");
        sfcCoreFileClient.upload(new FileSystemResource(tmpFile), 0, "/", "test.exe",
                Files.getLastModifiedTime(tmpFile).toMillis()).getDataOrThrow();
        return "upload ok";
    }
}
