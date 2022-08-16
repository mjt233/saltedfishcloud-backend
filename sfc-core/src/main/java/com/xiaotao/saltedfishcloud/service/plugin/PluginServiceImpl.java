package com.xiaotao.saltedfishcloud.service.plugin;

import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
}
