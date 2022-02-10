package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.FileTransferInfo;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.share.ShareService;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RequestMapping("/api/share")
@RestController
@RequiredArgsConstructor
public class ShareCollection {
    private final ShareService shareService;

    @DeleteMapping("{sid}")
    public JsonResult deleteShare(@PathVariable("sid") Integer sid) {
        User user = SecureUtils.getSpringSecurityUser();

        assert user != null;
        shareService.deleteShare(sid, user.getId());

        return JsonResult.emptySuccess();
    }

    @PostMapping("/wrap/{sid}/{verification}")
    @AllowAnonymous
    public JsonResult createWarp(@PathVariable("sid") Integer sid,
                                 @PathVariable("verification") String verification,
                                 @RequestParam(value = "code", required = false) String code,
                                 @RequestBody FileTransferInfo fileTransferInfo) {
        String wid = shareService.createwrap(sid, verification, code, fileTransferInfo);
        return JsonResultImpl.getInstance(wid);
    }

    /**
     * 获取分享的资源文件内容
     */
    @GetMapping("/resource")
    @AllowAnonymous
    public HttpEntity<Resource> getShareFile(@Valid ShareExtractorDTO extractor) throws IOException {
        return ResourceUtils.wrapResource(shareService.getFileResource(extractor));
    }

    /**
     * 获取一个分享信息
     */
    @GetMapping("/{sid}/{verification}")
    @AllowAnonymous
    public JsonResult getShare(@PathVariable("sid") Integer sid,
                               @PathVariable("verification") String verification,
                               @RequestParam(value = "code", required = false) String extractCode) {
        SharePO share = shareService.getShare(sid, verification);
        if(share.validateExtractCode(extractCode)) {
            share.setValidateSuccess(true);
        }
        share.hideKeyAttr();
        return JsonResultImpl.getInstance(share);
    }

    /**
     * 浏览分享的目录内容
     */
    @GetMapping({
            "/view/{sid}/{verification}/**",
            "/view/{sid}/{verification}",
    })
    @AllowAnonymous
    public JsonResult getDirContent(@PathVariable("sid") Integer sid,
                                    @PathVariable("verification") String verification,
                                    @RequestParam(value = "code", required = false) String extractCode,
                                    HttpServletRequest request) throws IOException {
        String path = URLUtils.getRequestFilePath("/api/share/view/" + sid + "/" + verification, request);
        List<FileInfo>[] files = shareService.browse(sid, verification, path, extractCode);
        return JsonResultImpl.getInstance(files);
    }

    /**
     * 创建分享
     */
    @PostMapping
    public JsonResult createShare(@RequestBody @Valid ShareDTO shareDTO, Principal principal) {
        System.out.println(principal);
        User user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        SharePO share = shareService.createShare(user.getId(), shareDTO);
        return JsonResultImpl.getInstance(share);
    }

    /**
     * 获取用户创建的分享列表
     */
    @GetMapping({
            "/user/{uid}",
            "/user"
    })
    @AllowAnonymous
    public JsonResult getUserShare(@PathVariable(value = "uid", required = false) Integer uid,
                                   @RequestParam(value = "page", defaultValue = "1") @Min(1) @Valid Integer page,
                                   @RequestParam(value = "size", defaultValue = "5") @Min(5) @Valid Integer size) {
        boolean emptyUid = false;
        // 缺少uid路径参数时使用当前登录的用户ID
        if (uid == null) {
            emptyUid = true;
            User user = SecureUtils.getSpringSecurityUser();
            if (user == null) {
                throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
            }
            uid = user.getId();
        }
        page--;
        CommonPageInfo<SharePO> userShare = shareService.getUserShare(uid, page, size, !emptyUid);
        if (emptyUid) {
            for (SharePO share : userShare.getContent()) {
                share.setValidateSuccess(true);
            }
        }
        return JsonResultImpl.getInstance(userShare);
    }
}
