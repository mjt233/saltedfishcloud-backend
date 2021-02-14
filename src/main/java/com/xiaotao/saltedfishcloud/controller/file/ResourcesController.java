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
import java.util.List;

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
     * 文件搜索
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
    public JsonResult getPath(@RequestParam("uid") int uid,
                              @RequestParam("nodeId") String node) {
        UIDValidator.validate(uid);
        return JsonResult.getInstance(nodeService.getPathByNode(uid, node));
    }
}
