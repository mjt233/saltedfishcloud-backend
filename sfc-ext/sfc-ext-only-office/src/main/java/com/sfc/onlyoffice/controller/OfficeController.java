package com.sfc.onlyoffice.controller;

import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.Config;
import com.sfc.onlyoffice.model.OfficeConfigProperty;
import com.sfc.onlyoffice.service.OfficeService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Controller
@RequestMapping("/api/office")
@Slf4j
public class OfficeController {
    @Autowired
    private OfficeConfigProperty officeConfigProperty;

    @Autowired
    private OfficeService officeService;

    /**
     * 读取所有请求参数，设置到params中
     * @param resourceRequest   通用资源请求参数
     * @param request           http请求对象
     */
    private void mergeParams(ResourceRequest resourceRequest, HttpServletRequest request) {
        Map<String, String> paramsMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            paramsMap.put(entry.getKey(), entry.getValue()[0]);
        }
        resourceRequest.setParams(paramsMap);
    }


    @RequestMapping("editor")
    @AllowAnonymous
    public ModelAndView editor(@RequestParam(value = "isView", defaultValue = "false") boolean isView, @Validated ResourceRequest resourceRequest, HttpServletRequest request) throws MalformedURLException {

        try {
            mergeParams(resourceRequest, request);
            Config config = officeService.getResourceConfig(resourceRequest, new URL(request.getRequestURL().toString() + "?" + request.getQueryString()), isView);
            ModelAndView modelAndView = new ModelAndView();
            modelAndView.addObject("property", officeConfigProperty);
            modelAndView.addObject("config", config);
            modelAndView.setViewName("office-editor");
            return modelAndView;
        } catch (Throwable throwable) {
            ModelAndView modelAndView = new ModelAndView();
            modelAndView.addObject("errMsg", throwable.getMessage());
            modelAndView.setViewName("office-error");
            if (!(throwable instanceof JsonException)) {
                log.error("编辑器加载失败", throwable);
            }
            return modelAndView;
        }
    }

    /**
     * ONLYOFFICE回调处理程序
     * @param resourceRequest 咸鱼云统一资源请求参数
     */
    @RequestMapping("saveCallback")
    @AllowAnonymous
    @ResponseBody
    public String saveCallback(@Validated ResourceRequest resourceRequest,
                               HttpServletRequest request) throws IOException {

        Scanner scanner = new Scanner(request.getInputStream()).useDelimiter("\\A");
        String body = scanner.hasNext() ? scanner.next() : "";
        Callback callback = MapperHolder.parseJson(body, Callback.class);
        officeService.handleCallback(resourceRequest, callback);
        return "{\"error\":0}";
    }


}
