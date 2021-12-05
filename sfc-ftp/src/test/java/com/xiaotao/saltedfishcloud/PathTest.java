package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.ftp.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.junit.Test;


public class PathTest {
    @Test
    public void testPathUtils() {
        String[] allNode = PathUtils.getAllNode("/233/1/2/3");
        for (String s : allNode) {
            System.out.println(s);
        }
    }

    @Test
    public void testFtpPath() {
        FtpPathInfo pathInfo = new FtpPathInfo("/私人网盘/a");
        System.out.println(pathInfo.getResourceParent());
    }
}
