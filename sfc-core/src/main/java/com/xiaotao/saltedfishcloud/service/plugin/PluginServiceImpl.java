package com.xiaotao.saltedfishcloud.service.plugin;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PluginServiceImpl implements PluginService {
    private final PluginManager pluginManager;

    @Override
    public List<PluginInfo> listPlugins() {
        return pluginManager.getAllPlugin().values().stream().map(e -> {
            PluginInfo newObj = new PluginInfo();
            BeanUtils.copyProperties(e, newObj);
            return newObj;
        }).collect(Collectors.toList());
    }

    @Override
    public Resource getPluginStaticResource(String name, String path) throws PluginNotFoundException {
        return pluginManager.getPluginResource(name, StringUtils.appendPath("static", path));
    }
}
