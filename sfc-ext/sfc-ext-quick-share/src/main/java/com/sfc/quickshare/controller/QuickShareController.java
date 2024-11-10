package com.sfc.quickshare.controller;

import com.sfc.quickshare.model.QuickShare;
import com.sfc.quickshare.service.QuickShareService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/quickShare")
public class QuickShareController {
    @Autowired
    private QuickShareService quickShareService;

    @PostMapping("upload")
    @BreakPoint
    @AllowAnonymous
    public JsonResult upload(@MergeFile @RequestParam("file") MultipartFile file, QuickShare quickShare) throws IOException {
        String code = quickShareService.saveTempFile(file.getResource(), quickShare);
        return JsonResultImpl.getInstance(code);
    }

    @GetMapping("getByCode")
    @AllowAnonymous
    public JsonResult getByCode(@RequestParam("code") String code) {
        return JsonResultImpl.getInstance(quickShareService.getByCode(code));
    }

    @GetMapping("getShareFile")
    @AllowAnonymous
    public ResponseEntity<Resource> getShareFile(@RequestParam("id") Long id) throws IOException {
        QuickShare quickShare = quickShareService.getRepo().findById(id).orElse(null);
        if (quickShare != null) {
            return ResourceUtils.wrapResource(quickShareService.getFileById(id), quickShare.getFileName());
        } else {
            throw new IllegalArgumentException("id无效");
        }

    }

}
