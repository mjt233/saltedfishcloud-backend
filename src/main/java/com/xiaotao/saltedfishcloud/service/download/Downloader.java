package com.xiaotao.saltedfishcloud.service.download;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class Downloader {
    private final RestTemplate restTemplate = new RestTemplate();
    public ResponseEntity<Resource> createTask(String url) {
        return restTemplate.getForEntity(url, Resource.class);
    }
}
