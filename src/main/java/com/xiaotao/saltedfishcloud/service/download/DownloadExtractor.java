package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.context.EmptyCallback;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class HttpResourceFile extends File {
    @Getter
    private String resourceName;

    public HttpResourceFile(String nativePath, String resourceName) {
        super(nativePath);
        this.resourceName = resourceName;
    }

    public HttpResourceFile(String pathname) {
        super(pathname);
    }
}

@Slf4j
public class DownloadExtractor implements ResponseExtractor<HttpResourceFile>, ProgressExtractor {
    private long total;
    private long loaded;
    private long speed;
    private final Path savePath;
    @Getter
    private boolean interrupted = false;
    @Setter
    private AsyncTackCallback readyCallback;
    @Setter
    private AsyncTackCallback progressCallback = EmptyCallback.get();
    @Getter
    private String resourceName;
    public DownloadExtractor(Path savePath) {
        this.savePath = savePath;
    }
    public DownloadExtractor(String savePath) {
        this.savePath = Paths.get(savePath);
    }
    public DownloadExtractor(File saveFile) {
        this.savePath = saveFile.toPath();
    }

    public void interrupt() {
        interrupted = true;
    }

    @Override
    public HttpResourceFile extractData(ClientHttpResponse response) throws IOException {
        var parent = savePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        // 文件名探测
        resourceName = response.getHeaders().getContentDisposition().getFilename();
        if (resourceName != null && FileNameValidator.valid(resourceName)) {
            log.debug("从响应头探测到文件名：" + resourceName);
        } else {
            log.debug("响应头无文件名");
        }

        // 检测本地文件目录是否正常可写
        if (Files.exists(savePath) && Files.isDirectory(savePath)) {
            throw new IOException(savePath + "为已存在的目录");
        }

        // 开始下载
        InputStream body = response.getBody();
        total = response.getHeaders().getContentLength();
        log.debug("开始下载，文件保存到:{}", savePath);
        OutputStream localFileStream = Files.newOutputStream(savePath);
        byte[] buffer = new byte[8192];
        int cnt;
        int lastProc = 0;
        long lastLoad = 0;
        long lastRecordTime = System.currentTimeMillis();

        readyCallback.action();
        try {
            while ( (cnt = body.read(buffer)) != -1 ) {
                // 中断信号检测
                if (interrupted) {
                    body.close();
                    localFileStream.close();
                    if (Files.exists(savePath)) {
                        Files.delete(savePath);
                    }
                    log.debug("下载被中断");
                    return null;
                }

                // 调试模式下每1s输出下载进度，并计算下载速度和触发ProgressCallback
                long curTime = System.currentTimeMillis();
                loaded += cnt;
                if (curTime - lastRecordTime > 1000) {
                    log.debug("已下载：{}({}) 总量：{}({}) 进度：{}%",
                            loaded, StringUtils.getFormatSize(loaded),
                            total, StringUtils.getFormatSize(total), (int)(loaded * 100/ total));

                    // 计算速度
                    speed = (loaded - lastLoad)/( (curTime - lastRecordTime)/1000 );

                    // 触发回调
                    try {
                        progressCallback.action();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }

                    // 更新记录
                    lastRecordTime = curTime;
                    lastLoad = loaded;
                }
                // 写入小块文件
                localFileStream.write(buffer, 0, cnt);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            body.close();
            localFileStream.close();
        }

        // 下载完毕，构造本地文件信息
        var res = new HttpResourceFile(savePath.toString(), resourceName);
        if (res.length() != total) {
            total = res.length();
        }
        return res;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getLoaded() {
        return loaded;
    }

    @Override
    public long getSpeed() {
        return speed;
    }
}
