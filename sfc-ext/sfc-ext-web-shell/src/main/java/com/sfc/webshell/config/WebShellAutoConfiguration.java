package com.sfc.webshell.config;

import com.sfc.webshell.controller.WebShellController;
import com.sfc.webshell.controller.WebShellEndpointHandler;
import com.sfc.webshell.repo.ShellExecuteRecordRepo;
import com.sfc.webshell.service.impl.ShellExecuteRecordServiceImpl;
import com.sfc.webshell.service.impl.ShellExecutorImpl;
import com.sfc.webshell.model.po.ShellExecuteRecord;
import com.sfc.webshell.upgrade.WebShellUpdater;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Import({
        WebShellController.class,
        WebShellUpdater.class,
        ShellExecutorImpl.class,
        ShellExecuteRecordServiceImpl.class,
        WebShellEndpointHandler.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                ShellExecuteRecordRepo.class
        }
)
@EntityScan(basePackageClasses = ShellExecuteRecord.class)
public class WebShellAutoConfiguration {
}
