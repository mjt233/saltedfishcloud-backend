package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

@RestController
@RequestMapping(PluginController.PREFIX)
public class PluginController {
    public final static String PREFIX = "/api/plugin";

    @Autowired
    private PluginService pluginService;

    @GetMapping("/getAllPlugins")
    @RolesAllowed({"ADMIN"})
    public JsonResult getAllPlugins() {
        return JsonResultImpl.getInstance(pluginService.listPlugins());
    }

    /**
     * 获取插件开放的静态资源文件
     */
    @GetMapping("/{pluginName}/resource/**")
    @AllowAnonymous
    public ResponseEntity<Resource> getPluginResource(@PathVariable("pluginName") String pluginName, HttpServletRequest request) throws PluginNotFoundException, UnsupportedEncodingException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + "/" + pluginName +"/resource", request);
        if (requestPath.length() == 0 || "/".equals(requestPath)) {
            requestPath = "index.html";
        }
        Resource resource = pluginService.getPluginStaticResource(pluginName, requestPath);
        if (resource == null || !resource.exists()) {
            throw new JsonException(404, "资源不存在");
        }
        return ResourceUtils.wrapResource(resource);
    }
}
