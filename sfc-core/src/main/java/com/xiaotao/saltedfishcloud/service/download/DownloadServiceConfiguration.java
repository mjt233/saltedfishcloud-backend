package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DownloadServiceConfiguration {
    private final TaskManager taskManager;

    @Bean
    public DownloadService downloadService() {
        return new DownloadServiceImpl(taskManager);
    }


}
