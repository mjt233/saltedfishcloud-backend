package com.xiaotao.saltedfishcloud.controller.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 * 系统资源管理控制器
 */
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
     * 搜索目标用户网盘中的文件或文件夹
     * @param uid 目标UID，非管理员只能搜索公共用户和自己的资源
     * @param page 页码，起始页为1
     */
    @GetMapping("/search/{uid}")
    public JsonResult search(HttpServletRequest request,
                             String key,
                             @PathVariable int uid,
                             @RequestParam(value = "page", defaultValue = "1") Integer page) {
        UIDValidator.validate(uid);
        PageHelper.startPage(page, 10);
        List<FileInfo> res = fileService.search(uid, key);
        PageInfo<FileInfo> pageInfo = new PageInfo<>(res);
        return JsonResult.getInstance(pageInfo);
    }

    /**
     * 仅限管理员：生成公共文件信息记录并写入到数据库中
     */
    @PostMapping("/makePublicRecord")
    @RolesAllowed({"ADMIN"})
    public JsonResult makePublicRecord() throws IOException {
        fileRecordService.makePublicRecord();
        return JsonResult.getInstance();
    }

    /**
     * 解析节点ID，获取节点ID对应的文件夹路径
     * @param uid   用户ID
     * @param node  节点ID
     * @return
     */
    @GetMapping("/getPath")
    public JsonResult getPath(@RequestParam("uid") int uid,
                              @RequestParam("nodeId") String node) {
        UIDValidator.validate(uid);
        return JsonResult.getInstance(nodeService.getPathByNode(uid, node));
    }
}
