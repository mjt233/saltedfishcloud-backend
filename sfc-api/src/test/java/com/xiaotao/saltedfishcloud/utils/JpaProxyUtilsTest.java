package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JpaProxyUtils} 单元测试。
 */
@DisplayName("JpaProxyUtils 单元测试")
class JpaProxyUtilsTest {

    /**
     * 验证普通对象不会被误判为代理。
     */
    @Test
    void unwrapProxy_shouldReturnOriginalObjectWhenSourceIsNotProxy() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setId(1L);
        app.setName("plain-app");

        assertFalse(JpaProxyUtils.isProxy(app));
        assertSame(app, JpaProxyUtils.unwrapProxy(app));
        assertEquals(ThirdPartyApp.class, JpaProxyUtils.getEntityClass(app));
    }

    /**
     * 验证 Hibernate 代理对象可以被正确解包为真实实体。
     */
    @Test
    void unwrapProxy_shouldReturnImplementationWhenSourceIsProxy() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setId(2L);
        app.setName("proxy-app");

        LazyInitializer lazyInitializer = mock(LazyInitializer.class);
        when(lazyInitializer.getImplementation()).thenReturn(app);

        HibernateProxy proxy = mock(HibernateProxy.class);
        when(proxy.getHibernateLazyInitializer()).thenReturn(lazyInitializer);

        assertTrue(JpaProxyUtils.isProxy(proxy));
        assertSame(app, JpaProxyUtils.unwrapProxy(proxy));
        assertEquals(ThirdPartyApp.class, JpaProxyUtils.getEntityClass(proxy));
    }

    /**
     * 验证代理对象可被复制为新的普通对象实例。
     */
    @Test
    void toPlainObject_shouldCopyValuesToNewInstance() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setId(3L);
        app.setUid(100L);
        app.setName("copied-app");
        app.setIsEnabled(true);

        LazyInitializer lazyInitializer = mock(LazyInitializer.class);
        when(lazyInitializer.getImplementation()).thenReturn(app);

        HibernateProxy proxy = mock(HibernateProxy.class);
        when(proxy.getHibernateLazyInitializer()).thenReturn(lazyInitializer);

        ThirdPartyApp plainObject = JpaProxyUtils.toPlainObject(proxy, ThirdPartyApp::new);
        assertNotNull(plainObject);
        assertEquals(app.getId(), plainObject.getId());
        assertEquals(app.getUid(), plainObject.getUid());
        assertEquals(app.getName(), plainObject.getName());
        assertEquals(app.getIsEnabled(), plainObject.getIsEnabled());
        assertNotSame(app, plainObject);
    }

    /**
     * 验证空值输入时返回空结果。
     */
    @Test
    void methods_shouldReturnNullWhenSourceIsNull() {
        assertNull(JpaProxyUtils.unwrapProxy(null));
        assertNull(JpaProxyUtils.getEntityClass(null));
        assertNull(JpaProxyUtils.toPlainObject(null, ThirdPartyApp::new));
    }
}


