package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import com.xiaotao.saltedfishcloud.entity.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.entity.JsonResult;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.collection.CollectionService;
import com.xiaotao.saltedfishcloud.utils.PageUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/collection")
@Validated
@RequiredArgsConstructor
public class CollectController {
    private final CollectionInfoRepository colDao;
    private final CollectionService collectionService;
    private final Sort SORT_BY_EXPIRED_AT_DESC = Sort.by("expiredAt").descending();

    @GetMapping("/record/{cid}")
    public JsonResult getRecords(@PathVariable("cid") Long cid,
                                 @RequestParam(value = "page", defaultValue = "1") @Min(1) @Valid Integer page,
                                 @RequestParam(value = "size", defaultValue = "10") @Min(5) @Valid Integer size) {
        CollectionInfo info = collectionService.getCollection(cid);
        User u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        if (!info.getUid().equals(u.getId())) {
            throw new JsonException(CommonError.FORMAT_ERROR);
        }
        return PageUtils.getInstanceWithPage(collectionService.getSubmits(cid, page - 1, size));
    }

    @DeleteMapping("{cid}")
    public JsonResult delete(@PathVariable("cid") Long cid) {
        User user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        collectionService.deleteCollection(user.getId(), cid);
        return JsonResultImpl.getInstance();
    }

    @PutMapping("{cid}/state/{state}")
    public JsonResult setState(@PathVariable("cid") Long cid,
                               @PathVariable("state") CollectionInfo.State state) {
        User user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        return JsonResultImpl.getInstance(collectionService.setState(user.getId(), cid, state));
    }

    @PostMapping
    public JsonResult createCollection(@Valid @RequestBody CollectionDTO data) {
        User u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        if (data.getNickname() == null) data.setNickname(u.getUsername());
        return JsonResultImpl.getInstance(collectionService.createCollection(u.getId(), data));
    }

    @PostMapping("{cid}/{verification}")
    @BreakPoint
    @AllowAnonymous
    public JsonResult submitCollection(@PathVariable Long cid,
                                       @PathVariable String verification,
                                       @MergeFile @RequestPart("file") MultipartFile file,
                                       @RequestPart("submitInfo") @Valid SubmitFile submitFile,
                                       HttpServletRequest request) throws IOException {
        User u = SecureUtils.getSpringSecurityUser();
        int uid = u == null ? 0 : u.getId();
        if (submitFile.getSize() == null) {
            submitFile.setSize(file.getSize());
        }
        String ip = request.getRemoteAddr();
        collectionService.collectFile(
                new CollectionInfoId(cid, verification),
                uid,
                file.getInputStream(),
                new FileInfo(file),
                submitFile,
                ip
        );
        return JsonResultImpl.getInstance();
    }

    @GetMapping("{cid}/{verification}")
    @AllowAnonymous
    public CollectionInfo getCollection(@PathVariable Long cid,
                                        @PathVariable String verification) {
        CollectionInfo i = collectionService.getCollectionWitchVerification(new CollectionInfoId(cid, verification));
        if (i == null) {
            throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
        }
        User user = SecureUtils.getSpringSecurityUser();
        if (user == null && !i.getAllowAnonymous()) {
            throw new JsonException(CollectionError.COLLECTION_REQUIRE_LOGIN);
        } else if (user == null || !user.getId().equals(i.getUid())) {
            i.setUid(null);
        }
        return i;
    }

    @GetMapping
    public List<CollectionInfo> getCollection() {
        User u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        return colDao.findByUidEquals(u.getId(), SORT_BY_EXPIRED_AT_DESC);
    }


}
