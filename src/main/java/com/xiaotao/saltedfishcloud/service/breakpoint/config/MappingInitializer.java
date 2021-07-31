package com.xiaotao.saltedfishcloud.service.breakpoint.config;

import com.xiaotao.saltedfishcloud.service.breakpoint.BreakPointController;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import lombok.var;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 初始化断点续传管理API，注册路由
 */
@Component
public class MappingInitializer {
    private final BreakPointController controller;
    private final RequestMappingHandlerMapping mappingHandler;

    // 通过构造方法获取依赖的Bean，随后执行初始化任务
    MappingInitializer(BreakPointController controller, RequestMappingHandlerMapping mappingHandler) throws NoSuchMethodException {
        this.controller = controller;
        this.mappingHandler = mappingHandler;
        init();
    }

    /**
     * 执行初始化，注册创建、查询与删除任务的路由
     */
    private void init() throws NoSuchMethodException {
        var createMethod = controller.getClass().getMethod("createTask", TaskMetadata.class);
        var queryMethod = controller.getClass().getMethod("queryTask", String.class);
        var deleteMethod = controller.getClass().getMethod("clearTask", String.class);
        registerMapping(createMethod, RequestMethod.POST);
        registerMapping(queryMethod, RequestMethod.GET);
        registerMapping(deleteMethod, RequestMethod.DELETE);
    }

    private void registerMapping(Method handlerMethod, RequestMethod...method) {
        var info = RequestMappingInfo.paths("/api/breakpoint").methods(method).build();
        mappingHandler.registerMapping(info, controller, handlerMethod);
    }
}
