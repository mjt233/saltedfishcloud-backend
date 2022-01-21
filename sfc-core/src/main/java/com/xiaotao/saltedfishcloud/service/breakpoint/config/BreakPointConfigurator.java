package com.xiaotao.saltedfishcloud.service.breakpoint.config;

import com.xiaotao.saltedfishcloud.service.breakpoint.BreakPointController;
import com.xiaotao.saltedfishcloud.service.breakpoint.BreakPointControllerImpl;
import com.xiaotao.saltedfishcloud.service.breakpoint.ProxyProcessor;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.DefaultTaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeBreakpointFileProvider;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeBreakpointFileProviderImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;

/**
 * 断点续传相关Bean自动配置类
 */
@Configuration
public class BreakPointConfigurator {
    @Resource
    private RequestMappingHandlerMapping mapping;

    /**
     * 控制路由注册
     */
    @Bean
    public MappingInitializer mappingInitializer() throws NoSuchMethodException {
        return new MappingInitializer(controller(), mapping);
    }

    /**
     * 任务管理API控制器
     */
    @Bean
    public BreakPointController controller() {
        return new BreakPointControllerImpl(taskManager());
    }

    /**
     * 任务管理器
     */
    @Bean
    @ConditionalOnMissingBean(TaskManager.class)
    public TaskManager taskManager() {
        return new DefaultTaskManager();
    }

    /**
     * 控制器代理增强
     */
    @Bean
    public ProxyProcessor proxyProcessor() {
        return new ProxyProcessor(taskManager(), mergeBreakpointMultipartFileProvider());
    }

    @Bean
    @ConditionalOnMissingBean(MergeBreakpointFileProvider.class)
    public MergeBreakpointFileProvider mergeBreakpointMultipartFileProvider() {
        return new MergeBreakpointFileProviderImpl(taskManager());
    }
}
