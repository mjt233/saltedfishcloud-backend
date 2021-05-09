package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@RunWith(SpringRunner.class)
class SecureUtilsTest {
    @Resource
    private SecureUtils secureUtils;

    @Test
    void getAnonymousUrls() {
        String[] anonymousUrls = secureUtils.getAnonymousUrls();
        for (String anonymousUrl : anonymousUrls) {
            System.out.println(anonymousUrl);
        }
    }
}
