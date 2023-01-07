package com.xiaotao.saltedfishcloud.service.desktop;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.model.DesktopComponent;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Component
@Slf4j
public class DesktopComponentServiceImpl implements DesktopComponentService, InitializingBean {
    private final static String LOG_PREFIX = "[桌面小组件]";
    private final static String PLUGIN_DESKTOP_COMPONENT_DEFINE_FILE_PATH = "desktop-component.json";
    private final List<DesktopComponent> registerList = new CopyOnWriteArrayList<>();

    @Autowired
    private PluginManager pluginManager;

    @Override
    public void registerComponent(DesktopComponent component) {
        registerList.add(component);
        log.info("{}注册桌面小组件：{}", LOG_PREFIX, component.getName());
    }

    @Override
    public List<DesktopComponent> listAllComponents() {
        return Collections.unmodifiableList(registerList);
    }

    private void registerAllPluginComponent() {
        pluginManager.listAllPlugin()
                .stream()
                .flatMap(plugin -> {
                    try {
                        Resource resource = pluginManager.getPluginResource(plugin.getName(), PLUGIN_DESKTOP_COMPONENT_DEFINE_FILE_PATH);
                        if (resource == null) {
                            return Stream.empty();
                        }
                        try (InputStream is = resource.getInputStream()) {
                            String json = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                            return MapperHolder.parseJsonToList(json, DesktopComponent.class).stream();
                        } catch (FileNotFoundException ignore) {
                            return Stream.empty();
                        } catch (IOException ioException) {
                            log.error("{}读取插件[{}]的桌面小组件出错",LOG_PREFIX, plugin.getName(), ioException);
                            return Stream.empty();
                        }
                    }catch (PluginNotFoundException ex) {
                        log.error("{}读取插件[{}]的桌面小组件出错",LOG_PREFIX,plugin.getName(), ex);
                        return Stream.empty();
                    }
                })
                .filter(Objects::nonNull)
                .forEach(this::registerComponent);

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.registerAllPluginComponent();
//        registerComponent(DesktopComponent.builder()
//                .name("dateTip")
//                .title("日期显示")
//                .describe("显示问候语和当前时间")
//                .icon("mdi-clock-outline")
//                .showOrder(0)
//                .config(Collections.singletonList(
//                        ConfigNode.builder()
//                                .name("message")
//                                .title("问候语")
//                                .inputType("text")
//                                .required(false)
//                                .defaultValue("")
//                                .build()
//                ))
//                .build());
    }
}
