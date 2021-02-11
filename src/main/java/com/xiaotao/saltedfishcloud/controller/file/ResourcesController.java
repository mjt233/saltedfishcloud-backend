package com.xiaotao.saltedfishcloud.controller.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.SecurityConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/resource")
public class ResourcesController {
    @Resource
    FileService fileService;
    @Resource
    FileRecordService fileRecordService;
    @Resource
    NodeService nodeService;

    /**
     * 公共文件搜索
     */
    @GetMapping("/search/public/**")
    public JsonResult search(HttpServletRequest request, String key,@RequestParam(value = "page", defaultValue = "1") Integer page) {
        PageHelper.startPage(page, 10);
        List<FileInfo> res = fileService.search(0, key);
        PageInfo<FileInfo> pageInfo = new PageInfo<>(res);
        return JsonResult.getInstance(pageInfo);
    }

    /**
     * 生成公共文件信息记录
     */
    @PostMapping("/makePublicRecord")
    @RolesAllowed({"ADMIN"})
    public JsonResult makePublicRecord() {
        fileRecordService.makePublicRecord();
        return JsonResult.getInstance();
    }

    /**
     * 解析节点ID
     * @param uid
     * @param node
     * @return
     */
    @GetMapping("/getPath")
    public JsonResult getPath(@RequestParam("uid") Integer uid,
                              @RequestParam("nodeId") String node) {
        try {
            if (uid !=0 && uid != Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getId()) {
                throw new NullPointerException();
            }
        } catch (NullPointerException e) {
            throw new HasResultException(403, "无权访问");
        }
        return JsonResult.getInstance(nodeService.getPathByNode(uid, node));
    }
}
