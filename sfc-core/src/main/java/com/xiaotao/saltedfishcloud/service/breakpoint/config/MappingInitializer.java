package com.xiaotao.saltedfishcloud.service.breakpoint.config;

import com.xiaotao.saltedfishcloud.service.breakpoint.BreakPointController;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 初始化断点续传管理API，注册路由
 * @TODO 通过读取外部配置实现动态配置断点续传URL
 */
public class MappingInitializer implements FeatureProvider {
    private static final String PREFIX = "/api/breakpoint";
    private final BreakPointController controller;
    private final RequestMappingHandlerMapping mappingHandler;

    // 通过构造方法获取依赖的Bean，随后执行初始化任务
    MappingInitializer(BreakPointController controller, RequestMappingHandlerMapping mappingHandler) throws NoSuchMethodException {
        this.controller = controller;
        this.mappingHandler = mappingHandler;
    }

    /**
     * 执行初始化，注册创建、查询与删除任务的路由
     */
    public void init() throws NoSuchMethodException {
        Method createMethod = controller.getClass().getMethod("createTask", TaskMetadata.class);
        Method queryMethod = controller.getClass().getMethod("queryTask", String.class);
        Method deleteMethod = controller.getClass().getMethod("clearTask", String.class);
        Method uploadMethod = controller.getClass().getMethod("uploadPart", MultipartFile.class, String.class, String.class);
        registerMapping(createMethod, RequestMethod.POST);
        registerMapping(queryMethod, RequestMethod.GET);
        registerMapping(deleteMethod, RequestMethod.DELETE);
        registerMapping(PREFIX + "/{id}/{part}", uploadMethod);
    }

    private void registerMapping(Method handlerMethod, RequestMethod...method) {
        registerMapping(PREFIX, handlerMethod, method);
    }

    private void registerMapping(String url, Method handlerMethod, RequestMethod...method) {
        RequestMappingInfo info = RequestMappingInfo.paths(url).methods(method).build();
        mappingHandler.registerMapping(info, controller, handlerMethod);
    }

    @Override
    public void registerFeature(HelloService helloService) {
        helloService.setFeature("breakpointUrl", PREFIX);
    }
}
