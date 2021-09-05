package com.xiaotao.saltedfishcloud.controller.task;

import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task/download")
public class DownloadController {
    @PostMapping
    public void createTask(DownloadTaskParams info) {
        System.out.println(info);
    }
}
