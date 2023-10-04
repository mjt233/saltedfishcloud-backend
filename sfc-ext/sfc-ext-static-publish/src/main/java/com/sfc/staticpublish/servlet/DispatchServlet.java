package com.sfc.staticpublish.servlet;

import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.sfc.staticpublish.model.property.StaticPublishProperty;
import com.sfc.staticpublish.service.StaticPublishRecordService;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

@Slf4j
public class DispatchServlet extends HttpServlet {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private StaticPublishRecordService staticPublishRecordService;

    @Autowired
    private StaticPublishProperty property;

    @Autowired
    private TemplateEngine templateEngine;

    private void doResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 获取基础信息 - uri和请求主机名，并设置通用header
        String uri = URLDecoder.decode(req.getRequestURI(), StandardCharsets.UTF_8);
        String host = req.getHeader("host");
        resp.setCharacterEncoding("utf8");
        resp.setHeader("server", "saltedfish-cloud disk");

        // 根据主机名判断按路径/按主机名，并从uri和主机名中获取站点名称、资源用户名、请求资源路径
        String siteName;
        String username;
        String resourcePath;
        boolean isByHost = host.endsWith(property.getByHostSuffix());
        if (isByHost) {
            siteName = host.substring(0, host.indexOf('.'));
            username = null;
            resourcePath = uri;
        } else {
            username = host.substring(0, host.indexOf('.'));
            siteName = getSiteNameFromUri(uri);
            int siteIndex = uri.indexOf(siteName);
            resourcePath = uri.substring(siteIndex + siteName.length());
        }
        StaticPublishRecord record;
        if (isByHost) {
            record = staticPublishRecordService.getBySiteName(siteName);
        } else {
            record = staticPublishRecordService.getByPath(username, siteName);
        }
        if (record == null) {
            send404Page(resp);
            return;
        }

        // 组装站点发布路径 + 请求资源路径为 网盘文件请求路径，从网盘中获取对应的资源
        String diskPath = StringUtils.appendPath(record.getPath(), resourcePath);
        // 获取文件资源
        Resource fileResource = getFileResource(resp, record, diskPath);
        // 若文件不存在，则尝试响应文件列表（如果开启了该项配置）
        if (fileResource == null && Boolean.TRUE.equals(record.getIsEnableFileList())) {
            sendFileListPage(req, resp, record, diskPath);
            return;
        }

        // 请求的文件不存在且未开启文件列表，则响应404
        if (fileResource == null) {
            send404Page(resp);
            return;
        }



        // 输出文件
        // todo 支持断点续传
        if (resp.getContentType() == null) {
            resp.setContentType(FileUtils.getContentType(PathUtils.getLastNode(uri)));
        }
        resp.setContentLengthLong(fileResource.contentLength());
        try (InputStream inputStream = fileResource.getInputStream()) {
            ServletOutputStream outputStream = resp.getOutputStream();
            byte[] buffer = new byte[8192];
            int cnt;
            while ((cnt = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, cnt);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doResponse(req, resp);
        } catch (Throwable e) {
            send500ErrorPage(req, resp, e);
            log.error("静态资源响应错误: ", e);
        }
    }

    private void send500ErrorPage(HttpServletRequest req, HttpServletResponse resp, Throwable e) throws IOException {
        Context context = new Context();
        resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        context.setVariable("errorMessage", e.getMessage());
        context.setVariable("uri", "/".equals(req.getRequestURI()) ? "" : req.getRequestURI());
        templateEngine.process("staticSiteInternalError", context, resp.getWriter());
    }

    /**
     * 发送目录下的文件列表页面
     * @param resp                  输出响应
     * @param record                站点发布记录
     * @param diskPath              请求的资源所在的网盘路径
     */
    private void sendFileListPage(HttpServletRequest req, HttpServletResponse resp, StaticPublishRecord record, String diskPath) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        if (!fileSystem.exist(record.getUid().intValue(), diskPath)) {
            send404Page(resp);
            return;
        }
        List<FileInfo>[] fileList = fileSystem.getUserFileList(record.getUid().intValue(), diskPath);
        resp.setContentType(FileUtils.getContentType("a.html"));

        List<FileInfo> finalFileList = Stream.concat(
                Optional.ofNullable(fileList[0]).stream().flatMap(Collection::stream),
                Optional.ofNullable(fileList[1]).stream().flatMap(Collection::stream)
        ).collect(Collectors.toList());

        for (FileInfo fileInfo : finalFileList) {
            if (fileInfo.getLastModified() == null) {
                Date fixDate = Optional.ofNullable(fileInfo.getUpdatedAt()).orElseGet(fileInfo::getCreatedAt);
                if (fixDate != null) {
                    fileInfo.setLastModified(fixDate.getTime());
                }
            }
        }

        Context context = new Context();
        context.setVariable("fileList", finalFileList);
        context.setVariable("record", record);
        context.setVariable("request", req);
        context.setVariable("uri", URLDecoder.decode("/".equals(req.getRequestURI()) ? "" : req.getRequestURI(), StandardCharsets.UTF_8));
        String res = templateEngine.process("dirFileList", context);
        resp.getWriter().println(res);
    }

    /**
     * 根据站点发布信息和请求的资源路径，获取对应的文件资源。
     * @param record                站点发布信息
     * @param diskPath              请求的资源路径所在的网盘路径
     */
    private Resource getFileResource(HttpServletResponse resp, StaticPublishRecord record, String diskPath) throws IOException {
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(record.getUid().intValue(), diskPath, null);

        // 文件不存在，如果开启了index.html首页，则尝试加载首页
        if (resource == null && Boolean.TRUE.equals(record.getIsEnableIndex())) {
            resource = diskFileSystemManager.getMainFileSystem().getResource(record.getUid().intValue(), diskPath, "index.html");
            resp.setContentType(FileUtils.getContentType("index.html"));
        }

        return resource;
    }

    /**
     * 发送404页面信息
     */
    private void send404Page(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=utf-8");
        resp.setStatus(SC_NOT_FOUND);
        PrintWriter writer = resp.getWriter();
        writer.print("<html> <body> <h1><center>404 Not Found</center></h1> <hr> <p><center>咸鱼云网盘</center><p> ");
//            writer.print("<p>站点名称: ");      writer.print(siteName);            writer.print("<p>");
//            writer.print("<p>请求资源名称: ");   writer.print(resourcePath);        writer.print("<p>");
//            writer.print("<p>用户名: ");        writer.print(username);            writer.print("<p>");
//            writer.print("<p>uri: ");          writer.print(uri);                 writer.print("<p>");
        writer.print("</body> </html>");
    }

    /**
     * 从URI中匹配站点名称。格式：/站点名称/xxxxxx，即取URI中第一个目录节点的名称
     * @param uri   URI
     * @return      站点名称
     */
    private String getSiteNameFromUri(String uri) {
        int begin = 0;
        int end,len;
        len = uri.length();
        if (uri.charAt(0) == '/') {
            for (int i = 0; i < len; i++) {
                if (uri.charAt(i) != '/') {
                    begin = i;
                    break;
                }
            }
        }
        end = uri.indexOf('/', begin);
        if (end == -1) {
            end = len;
        }
        return uri.substring(begin, end);
    }
}
