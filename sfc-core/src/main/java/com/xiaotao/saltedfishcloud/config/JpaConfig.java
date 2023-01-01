package com.xiaotao.saltedfishcloud.config;

import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.persistence.EntityManagerFactory;
import java.util.Map;

@Configuration
public class JpaConfig {
    
//    @Bean
//    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder factoryBuilder) {
//        Map<String, Object> vendorProperties = getVendorProperties();
//        customizeVendorProperties(vendorProperties);
//        return factoryBuilder.dataSource(this.dataSource).packages(getPackagesToScan()).properties(vendorProperties)
//                .mappingResources(getMappingResources()).jta(isJta()).build();
//    }
}
