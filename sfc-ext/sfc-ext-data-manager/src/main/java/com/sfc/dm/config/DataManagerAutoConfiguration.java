package com.sfc.dm.config;

import com.sfc.dm.controller.InvalidDataController;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.ClaimService;
import com.sfc.dm.service.InvalidDataService;
import com.sfc.dm.service.identify.FileTypeChecker;
import com.sfc.dm.service.identify.FileTypeCheckerImpl;
import com.sfc.dm.service.identify.VideoCheckProvider;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 数据管理插件自动配置
 */
@Configuration
@EntityScan(basePackageClasses = {InvalidDataRecord.class})
@EnableJpaRepositories(basePackageClasses = {InvalidDataRecordRepo.class})
public class DataManagerAutoConfiguration {

    @Bean
    public InvalidDataController invalidDataController(InvalidDataService invalidDataService,
                                                        ClaimService claimService,
                                                        AsyncTaskManager asyncTaskManager,
                                                        AsyncTaskRecordRepo asyncTaskRecordRepo) {
        return new InvalidDataController(invalidDataService, claimService, asyncTaskManager, asyncTaskRecordRepo);
    }

    @Bean
    public InvalidDataService invalidDataService() {
        return new InvalidDataService();
    }

    @Bean
    public ClaimService claimService() {
        return new ClaimService();
    }

    @Bean
    public FileTypeChecker fileTypeChecker() {
        FileTypeCheckerImpl checker = new FileTypeCheckerImpl();
        checker.addProvider(new VideoCheckProvider());
        return checker;
    }
}
