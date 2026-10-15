package com.sfc.demo.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "sfc-demo-service", path = "/api/internal")
public interface DemoFeign {
    @GetMapping("hello")
    String hello();
}
