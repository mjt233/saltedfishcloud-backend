package com.xiaotao.saltedfishcloud.service.desktop;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.DesktopComponent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DesktopComponentServiceImpl implements DesktopComponentService, InitializingBean {
    private final List<DesktopComponent> registerList = new CopyOnWriteArrayList<>();

    @Override
    public void registerComponent(DesktopComponent component) {
        registerList.add(component);
    }

    @Override
    public List<DesktopComponent> listAllComponents() {
        return Collections.unmodifiableList(registerList);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registerComponent(DesktopComponent.builder()
                .config(Collections.emptyList())
                .name("dateTip")
                .title("日期显示")
                .describe("显示问候语和当前时间")
                .icon("mdi-clock-outline")
                .showOrder(0)
                .config(Collections.singletonList(
                        ConfigNode.builder()
                                .name("message")
                                .title("问候语")
                                .inputType("text")
                                .required(false)
                                .defaultValue("")
                                .build()
                ))
                .build());
    }
}
