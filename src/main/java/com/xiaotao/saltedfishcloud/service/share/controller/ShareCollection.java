package com.xiaotao.saltedfishcloud.service.share.controller;

import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.po.JsonResult;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.share.ShareService;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.RequestUtil;
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

    @GetMapping("/{sid}/{verification}")
    @AllowAnonymous
    public JsonResult getShare(@PathVariable("sid") Integer sid,
                               @PathVariable("verification") String verification) {
        return JsonResult.getInstance(shareService.getShare(sid, verification));
    }

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
        return JsonResult.getInstance(files);
    }

    @PostMapping
    public JsonResult createShare(@RequestBody @Valid ShareDTO shareDTO, Principal principal) {
        System.out.println(principal);
        User user = SecureUtils.getSpringSecurityUser();
        assert user != null;
        SharePO share = shareService.createShare(user.getId(), shareDTO);
        return JsonResult.getInstance(share);
    }

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
                throw new JsonException(ErrorInfo.SYSTEM_FORBIDDEN);
            }
            uid = user.getId();
        }
        page--;
        CommonPageInfo<SharePO> userShare = shareService.getUserShare(uid, page, size);
        if (!emptyUid) {
            for (SharePO po : userShare.getContent()) {
                po.setVerification(null);
            }
        }
        return JsonResult.getInstance(userShare);
    }
}
