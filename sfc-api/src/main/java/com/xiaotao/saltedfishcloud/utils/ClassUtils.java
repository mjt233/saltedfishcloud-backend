package com.xiaotao.saltedfishcloud.utils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ClassUtils {

    public static List<URL> getAllResources(ClassLoader loader, String prefix) throws IOException {
        List<URL> res = new ArrayList<>();
        getAllResources(loader, prefix, res);
        return res;
    }

    private static void getAllResources(ClassLoader loader, String prefix, List<URL> res) throws IOException {
        Enumeration<URL> resources = loader.getResources(prefix);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            res.add(url);
        }
    }
}
