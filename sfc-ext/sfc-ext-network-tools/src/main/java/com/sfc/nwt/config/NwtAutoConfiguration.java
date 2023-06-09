package com.sfc.nwt.config;

import com.sfc.nwt.controller.NwtController;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.repo.WolDeviceRepo;
import com.sfc.nwt.service.WolDeviceService;
import com.sfc.nwt.update.NwtUpdater;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackageClasses = {
        WolDevice.class
})
@EnableJpaRepositories(basePackageClasses = {
        WolDeviceRepo.class
})
public class NwtAutoConfiguration {

    @Bean
    public NwtController nwtController() {
        return new NwtController();
    }

    @Bean
    public WolDeviceService wolDeviceService() {
        return new WolDeviceService();
    }

    @Bean
    public NwtUpdater nwtUpdater() { return new NwtUpdater(); }
}
