package com.xiaotao.saltedfishcloud.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FileUploadServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        byte[] b = new byte[8192];
        int l = 0;
        while ( (l = req.getInputStream().readLine(b, 0, b.length)) != -1 ) {
            for (int i = 0; i < l; i++) {
                System.out.print((char) b[i]);
            }
        }
        resp.getWriter().println("ok");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println(123);
        super.doGet(req, resp);
    }
}
