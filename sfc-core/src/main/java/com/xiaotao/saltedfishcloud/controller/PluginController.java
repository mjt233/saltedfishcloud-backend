package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.vo.PluginInfoVo;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(PluginController.PREFIX)
public class PluginController {
    public final static String PREFIX = "/api/plugin";

    @Autowired
    private PluginService pluginService;

    @GetMapping("/listAvailablePlugins")
    @RolesAllowed({"ADMIN"})
    public JsonResult getAllPlugins() throws IOException {
        return JsonResultImpl.getInstance(pluginService.listAvailablePlugins());
    }

    @PostMapping("/deletePlugin")
    @RolesAllowed({"ADMIN"})
    public JsonResult deletePlugin(@RequestParam("name") String name) throws IOException {
        pluginService.deletePlugin(name);
        return JsonResult.emptySuccess();
    }

    @GetMapping("getPlugin")
    @RolesAllowed({"ADMIN"})
    public HttpEntity<Resource> getPlugin(@RequestParam("name") String name) throws IOException {
        Resource pluginFile = pluginService.getPluginFile(name);
        return ResourceUtils.wrapResource(pluginFile, StringUtils.getURLLastName(pluginFile.getURL()));
    }

    @AllowAnonymous
    @GetMapping("/autoLoad.js")
    public ResponseEntity<Resource> autoLoadJs() throws UnsupportedEncodingException {
        Resource resource = pluginService.getMergeAutoLoadResource("js");
        return ResourceUtils.wrapResourceWithCache(resource, resource.getFilename());
    }

    @AllowAnonymous
    @GetMapping("/autoLoad.css")
    public ResponseEntity<Resource> autoLoadCss() throws UnsupportedEncodingException {
        Resource resource = pluginService.getMergeAutoLoadResource("css");
        return ResourceUtils.wrapResourceWithCache(resource, resource.getFilename());
    }

    /**
     * 获取需要前端自动加载的插件的js和css资源的插件
     */
    @GetMapping("/listPluginAutoLoadList")
    @AllowAnonymous
    public JsonResult listPluginAutoLoadList() {
        List<PluginInfo> pluginInfos = pluginService.listPlugins().stream().filter(e -> e.getAutoLoad() != null && !e.getAutoLoad().isEmpty()).collect(Collectors.toList());
        return JsonResultImpl.getInstance(pluginInfos);
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

    @PostMapping("/uploadPlugin")
    @RolesAllowed({"ADMIN"})
    public JsonResult uploadPlugin(@RequestParam("file") MultipartFile file) throws IOException {
        PluginInfoVo pluginInfoVo = pluginService.uploadPlugin(file.getResource());
        return JsonResultImpl.getInstance(pluginInfoVo);
    }

    @PostMapping("/installPlugin")
    @RolesAllowed({"ADMIN"})
    public JsonResult installPlugin(@RequestParam("tempId") Long tempId, @RequestParam("fileName") String fileName) throws IOException {
        pluginService.installPlugin(tempId, fileName);
        return JsonResult.emptySuccess();
    }
}
