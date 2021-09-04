package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.ProxyInfo;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProxyDaoTest {
    @Resource
    private ProxyDao proxyDao;

    @Test
    public void doTest() {
        ProxyInfo info = new ProxyInfo();
        info.setAddress("127.0.0.1");
        info.setName("local");
        info.setPort(1080);
        info.setType(ProxyInfo.Type.SOCKS);
        // test add
        Assert.assertEquals(1, proxyDao.addProxy(info));

        // test getAll
        Assert.assertTrue(proxyDao.getAllProxy().size() > 0);

        // test modify
        info.setName("local2");
        Assert.assertEquals(1, proxyDao.modifyProxy("local", info));

        // valid modify
        Assert.assertEquals("local2", proxyDao.getAllProxy().get(0).getName());

        // test get by name
        Assert.assertEquals("local2", proxyDao.getProxyByName("local2").getName());

        // test remove
        Assert.assertEquals(1, proxyDao.removeProxy("local2"));

        // valid remove
        Assert.assertEquals(0, proxyDao.getAllProxy().size());
    }
}
