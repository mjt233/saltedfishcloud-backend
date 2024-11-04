package com.sfc.staticpublish.servlet;

import com.sfc.staticpublish.constants.AccessWay;
import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.sfc.staticpublish.model.property.StaticPublishProperty;
import com.sfc.staticpublish.service.StaticPublishRecordService;
import com.xiaotao.saltedfishcloud.common.RedirectableUrl;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.util.StreamUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.servlet.http.HttpServletResponse.*;

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
        String username = null;
        String resourcePath;

        // 尝试从Host和URI中解析站点名称和用户名
        boolean isByHost = host.endsWith(property.getByHostSuffix());
        int dotIndex = host.indexOf('.');
        if (dotIndex > -1) {
            if (isByHost) {
                siteName = host.substring(0, dotIndex);
                resourcePath = uri;
            } else {
                username = host.substring(0, dotIndex);
                siteName = getSiteNameFromUri(uri);
                int siteIndex = uri.indexOf(siteName);
                resourcePath = uri.substring(siteIndex + siteName.length());
            }
        } else {
            // 像直接用localhost这种地址来访问是没有“.”的，只能是根路径站点
            siteName = getSiteNameFromUri(uri);
            int siteIndex = uri.indexOf(siteName);
            resourcePath = uri.substring(siteIndex + siteName.length());
        }

        // 匹配站点
        StaticPublishRecord record = null;
        if (isByHost) {
            record = staticPublishRecordService.getBySiteName(siteName);
        } else if (username != null){
            record = staticPublishRecordService.getByPath(username, siteName);
        }
        if (record == null) {
            if (!property.getIsEnableDirectRootPath()) {
                send404Page(resp);
                return;
            }

            // 匹配根目录挂载站点
            // 此时的siteName是从uri中取的
            siteName = getSiteNameFromUri(uri);
            int siteIndex = uri.indexOf(siteName);
            resourcePath = uri.substring(siteIndex + siteName.length());
            record = staticPublishRecordService.getDirectRootPathBySiteName(siteName);
            if (record == null) {
                send404Page(resp);
                return;
            }
        }

        // 检查身份验证
        if (!checkAuth(record, req, resp)) {
            send401Page(req, resp);
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
        String requestFileName = PathUtils.getLastNode(uri);
        if (resp.getContentType() == null) {
            resp.setContentType(FileUtils.getContentType(requestFileName));
        }
        if (resp.getHeader(HttpHeaders.CONTENT_DISPOSITION) == null && requestFileName.length() > 0 && !requestFileName.endsWith("/")) {
            resp.addHeader(HttpHeaders.CONTENT_DISPOSITION, ResourceUtils.generateContentDisposition(requestFileName));
        }
        sendFile(req, resp, fileResource);
    }

    /**
     * 检查是否通过身份验证
     * @param record    站点发布信息
     * @param req       请求
     * @param resp      响应
     * @return          是否通过，若不通过则应结束流程
     */
    protected boolean checkAuth(StaticPublishRecord record, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 无需登录，跳过
        if (!Boolean.TRUE.equals(record.getIsNeedLogin())) {
            return true;
        }

        String authorizationStr = req.getHeader("Authorization");
        if (authorizationStr == null || authorizationStr.length() < 6) {
            return false;
        }

        String base64Str = authorizationStr.substring(6);
        String[] usernameAndPassword = new String(Base64.getDecoder().decode(base64Str), StandardCharsets.UTF_8).split(":", 2);
        if (usernameAndPassword.length != 2) {
            return false;
        }
        return usernameAndPassword[0].equals(record.getLoginUsername()) && usernameAndPassword[1].equals(record.getLoginPassword());
    }

    /**
     * 发送401需要验证页面
     * @param req   请求
     * @param resp  响应
     */
    protected void send401Page(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(SC_UNAUTHORIZED);
        resp.addHeader("WWW-Authenticate", "Basic realm=\"Site need Login\"");
        resp.setContentType("text/html;charset=utf-8");
        PrintWriter writer = resp.getWriter();
        writer.print("<html> <body> <h1><center>401 Unauthorized</center></h1> <hr> 该站点需要身份验证 <hr> <p><center>咸鱼云网盘</center><p> ");
        writer.print("</body> </html>");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doResponse(req, resp);
        } catch (ClientAbortException ignore) {
        } catch (Throwable e) {
            log.error("静态资源响应错误: ", e);
            send500ErrorPage(req, resp, e);
        }
    }

    /**
     * 响应文件内容
     * @param req           请求
     * @param resp          响应
     * @param fileResource  文件资源
     */
    private void sendFile(HttpServletRequest req, HttpServletResponse resp, Resource fileResource) throws IOException {
        if (fileResource instanceof RedirectableUrl) {
            String redirectUrl = ((RedirectableUrl) fileResource).getRedirectUrl();
            resp.sendRedirect(redirectUrl);
            return;
        }
        long len = fileResource.contentLength();
        resp.addHeader("Accept-Ranges", "bytes");
        String rangeHeader = req.getHeader("Range");

        long start = 0, end = len - 1;
        if (rangeHeader != null) {
            List<HttpRange> rangeList;
            try {
                rangeList = HttpRange.parseRanges(rangeHeader);
                if (rangeList.size() > 1) {
                    resp.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return;
                }
            } catch (Exception ignore) {
                resp.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            HttpRange range = rangeList.get(0);
            start = range.getRangeStart(len);
            end = range.getRangeEnd(len);

            resp.setStatus(SC_PARTIAL_CONTENT);
            resp.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + len);
        }

        resp.setContentLengthLong(end - start + 1);
        try (InputStream inputStream = fileResource.getInputStream()) {
            ServletOutputStream outputStream = resp.getOutputStream();
            StreamUtils.copyRange(inputStream, outputStream, start, end);
        }
    }

    private void send500ErrorPage(HttpServletRequest req, HttpServletResponse resp, Throwable e) throws IOException {
        PrintWriter writer;
        try {
            writer = resp.getWriter();
        } catch (Exception ignore) {
            return;
        }
        Context context = new Context();
        resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        String uri = URLDecoder.decode("/".equals(req.getRequestURI()) ? "" : req.getRequestURI(), StandardCharsets.UTF_8);
        String parentUri = PathUtils.getParentPath(uri);
        context.setVariable("parentUri", parentUri);
        context.setVariable("errorMessage", e.getMessage());
        context.setVariable("uri", "/".equals(req.getRequestURI()) ? "" : req.getRequestURI());
        templateEngine.process("static-publish-templates/staticSiteInternalError", context, writer);
    }

    /**
     * 发送目录下的文件列表页面
     * @param resp                  输出响应
     * @param record                站点发布记录
     * @param diskPath              请求的资源所在的网盘路径
     */
    private void sendFileListPage(HttpServletRequest req, HttpServletResponse resp, StaticPublishRecord record, String diskPath) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        if (!fileSystem.exist(record.getUid(), diskPath)) {
            send404Page(resp);
            return;
        }
        List<FileInfo>[] fileList = fileSystem.getUserFileList(record.getUid(), diskPath);
        resp.setContentType(FileUtils.getContentType("a.html"));

        List<FileInfo> finalFileList = Stream.concat(
                Optional.ofNullable(fileList[0]).stream().flatMap(Collection::stream),
                Optional.ofNullable(fileList[1]).stream().flatMap(Collection::stream)
        ).collect(Collectors.toList());

        for (FileInfo fileInfo : finalFileList) {
            if (fileInfo.getMtime() == null) {
                Date fixDate = Optional.ofNullable(fileInfo.getUpdateAt()).orElseGet(fileInfo::getCreateAt);
                if (fixDate != null) {
                    fileInfo.setMtime(fixDate.getTime());
                }
            }
        }

        Context context = new Context();
        String uri = URLDecoder.decode("/".equals(req.getRequestURI()) ? "" : req.getRequestURI(), StandardCharsets.UTF_8);
        String parentUri = PathUtils.getParentPath(uri);
        context.setVariable("fileList", finalFileList);
        context.setVariable("fileUrlNameMap", finalFileList.stream().collect(Collectors.toMap(
                FileInfo::getName,
                e -> URLEncoder.encode(e.getName(), StandardCharsets.UTF_8)
        )));
        context.setVariable("record", record);
        context.setVariable("request", req);
        context.setVariable("uri", uri);
        context.setVariable("parentUri", parentUri);
        boolean isRoot;
        if (Objects.equals(AccessWay.BY_HOST, record.getAccessWay())) {
            isRoot = uri.length() == 0 || uri.equals("/");
        } else {
            isRoot = uri.length() == 0 || uri.equals("/" + record.getSiteName());
        }
        context.setVariable("isRoot", isRoot);
        String res = templateEngine.process("static-publish-templates/dirFileList", context);
        resp.getWriter().println(res);
    }

    /**
     * 根据站点发布信息和请求的资源路径，获取对应的文件资源。
     * @param record                站点发布信息
     * @param diskPath              请求的资源路径所在的网盘路径
     */
    private Resource getFileResource(HttpServletResponse resp, StaticPublishRecord record, String diskPath) throws IOException {
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(record.getUid(), diskPath, null);

        // 文件不存在，如果开启了index.html首页，则尝试加载首页
        if (resource == null && Boolean.TRUE.equals(record.getIsEnableIndex())) {
            resource = diskFileSystemManager.getMainFileSystem().getResource(record.getUid(), diskPath, "index.html");
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
