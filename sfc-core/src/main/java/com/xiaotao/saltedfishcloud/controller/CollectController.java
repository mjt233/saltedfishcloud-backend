package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.sfc.cloud.constant.SfcCommonError;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import com.sfc.cloud.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.model.dto.CollectionRecordDTO;
import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.sfc.cloud.exception.SfcJsonException;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/collection")
@Validated
@RequiredArgsConstructor
public class CollectController {
    private final CollectionInfoRepo colDao;
    private final CollectionService collectionService;
    private final Sort SORT_BY_EXPIRED_AT_DESC = Sort.by("expiredAt").descending();

    /**
     * 获取文件收集记录
     */
    @GetMapping("/record/{cid}")
    public SfcJsonResult<SfcPageInfo<CollectionRecordDTO>> getRecords(@PathVariable("cid") Long cid,
                                                                      @RequestParam(value = "page", defaultValue = "1") @Min(1) @Valid Integer page,
                                                                      @RequestParam(value = "size", defaultValue = "10") @Min(5) @Valid Integer size) {
        CollectionInfo info = collectionService.getCollection(cid);
        UserPrincipal u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        if (!info.getUid().equals(u.getId())) {
            throw new SfcJsonException(SfcCommonError.FORMAT_ERROR);
        }
        return SfcJsonResult.getInstance(collectionService.getSubmits(cid, page - 1, size));
    }

    /**
     * 删除收集任务
     */
    @DeleteMapping("{cid}")
    public SfcJsonResult<Object> delete(@PathVariable("cid") Long cid) {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        collectionService.deleteCollection(user.getId(), cid);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 修改收集任务状态
     */
    @PutMapping("{cid}/state/{state}")
    public SfcJsonResult<CollectionInfo> setState(@PathVariable("cid") Long cid,
                               @PathVariable("state") CollectionInfo.State state) {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        return SfcJsonResult.getInstance(collectionService.setState(user.getId(), cid, state));
    }

    /**
     * 创建收集任务
     */
    @PostMapping
    public SfcJsonResult<CollectionInfoId> createCollection(@Valid @RequestBody CollectionDTO data) {
        UserPrincipal u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        if (data.getNickname() == null) data.setNickname(u.getUsername());
        return SfcJsonResult.getInstance(collectionService.createCollection(u.getId(), data));
    }

    /**
     * 提交收集文件
     * @param cid           收集id
     * @param verification  收集任务的验证码（verification字段）
     * @param file          接受的文件
     * @param submitFile    提交的文件信息
     */
    @PostMapping("{cid}/{verification}")
    @BreakPoint
    @AllowAnonymous
    public SfcJsonResult<Object> submitCollection(@PathVariable Long cid,
                                       @PathVariable String verification,
                                       @MergeFile @RequestPart("file") MultipartFile file,
                                       @RequestPart("submitInfo") @Valid SubmitFile submitFile,
                                       HttpServletRequest request) throws IOException {
        UserPrincipal u = SecureUtils.getSpringSecurityUser();
        long providerUid = u == null ? 0 : u.getId();
        if (submitFile.getFileParam().getSize() == null) {
            submitFile.getFileParam().setSize(file.getSize());
        }
        FileInfo newFile = new FileInfo(file)
                .setMd5(submitFile.getFileParam().getMd5())
                .setMtime(submitFile.getFileParam().getMtime());
        if (newFile.getMtime() == null) {
            newFile.setMtime(file.getResource().lastModified());
        }
        String ip = request.getRemoteAddr();
        collectionService.collectFile(
                new CollectionInfoId(cid, verification),
                providerUid,
                newFile,
                submitFile,
                ip
        );
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("{cid}/{verification}")
    @AllowAnonymous
    public CollectionInfo getCollection(@PathVariable Long cid,
                                        @PathVariable String verification) {
        CollectionInfo i = collectionService.getCollectionWitchVerification(new CollectionInfoId(cid, verification));
        if (i == null) {
            throw new SfcJsonException(CollectionError.COLLECTION_NOT_FOUND);
        }
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (user == null && !i.getAllowAnonymous()) {
            throw new SfcJsonException(CollectionError.COLLECTION_REQUIRE_LOGIN);
        } else if (user == null || !user.getId().equals(i.getUid())) {
            i.setUid(null);
        }
        return i;
    }

    @GetMapping
    public List<CollectionInfo> getCollection() {
        UserPrincipal u = SecureUtils.getSpringSecurityUser();
        assert u != null;
        return colDao.findByUidEquals(u.getId(), SORT_BY_EXPIRED_AT_DESC);
    }


}
