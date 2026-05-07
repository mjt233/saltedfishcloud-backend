package com.saltedfishcloud.ext.ve.model;

import lombok.Data;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Data
public class ProcessWrap {
    private Process process;
    private List<String> args;
    private String extraMessage;

    /**
     * 采集进程输出并等待进程结束
     * @return  正常退出则返回null，非正常退出则返回进程的输出消息
     */
    public String waitProcess() {
        try(InputStream is = getProcess().getInputStream()) {
            String output = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? null : output;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
