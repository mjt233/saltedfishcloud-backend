package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.entity.po.JsonResult;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.collection.CollectionService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/collection")
@Validated
@RequiredArgsConstructor
public class CollectController {
    private final CollectionInfoRepository colDao;
    private final CollectionService collectionService;

    @PostMapping
    public JsonResult createCollection(@Valid @RequestBody CollectionDTO data) {
        User u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        if (data.getNickname() == null) data.setNickname(u.getUsername());
        return JsonResult.getInstance(collectionService.createCollection(u.getId(), data));
    }

    @PostMapping("{cid}/{verification}")
    @BreakPoint
    @AllowAnonymous
    public JsonResult submitCollection(@PathVariable Long cid,
                                       @PathVariable String verification,
                                       @MergeFile @RequestPart("file") MultipartFile file,
                                       @RequestPart("submitInfo") @Valid SubmitFile submitFile) throws IOException {
        User u = SecureUtils.getSpringSecurityUser();
        int uid = u == null ? 0 : u.getId();
        if (submitFile.getSize() == null) {
            submitFile.setSize(file.getSize());
        }
        collectionService.collectFile(new CollectionInfoId(cid, verification), uid, file.getInputStream(), new FileInfo(file), submitFile);
        return JsonResult.getInstance();
    }

    @GetMapping("{cid}/{verification}")
    @AllowAnonymous
    public CollectionInfo getCollection(@PathVariable Long cid,
                                        @PathVariable String verification) {
        CollectionInfo i = collectionService.getCollection(new CollectionInfoId(cid, verification));
        if (i == null) {
            throw new JsonException(ErrorInfo.COLLECTION_NOT_FOUND);
        }
        User user = SecureUtils.getSpringSecurityUser();
        if (user == null && !i.getAllowAnonymous()) {
            throw new JsonException(ErrorInfo.COLLECTION_REQUIRE_LOGIN);
        }
        return i;
    }

    @GetMapping
    public List<CollectionInfo> getCollection() {
        User u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        return colDao.findByUidEquals(u.getId());
    }


}
