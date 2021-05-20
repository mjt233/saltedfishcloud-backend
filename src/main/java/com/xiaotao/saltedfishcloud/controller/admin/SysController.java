package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.annotations.BlockWhileSwitching;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private ConfigService configService;
    @Resource
    private FileAnalyseDao fileAnalyseDao;

    @PutMapping("store/type")
    @BlockWhileSwitching
    public JsonResult setStoreType(@RequestParam("type") String type) throws IOException {
        try {
            StoreType storeType = StoreType.valueOf(type.toUpperCase());
            if (configService.setStoreType(storeType)) {
                return JsonResult.getInstance();
            } else {
                return JsonResult.getInstance(0, DiskConfig.STORE_TYPE.toString(), "请求被忽略，模式无变化");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的类型，可选RAW或UNIQUE");
        }
    }

    @GetMapping("store/state")
    public JsonResult getStoreState() {
        LinkedHashMap<String, Object> data = JsonResult.getDataMap();
        File storeRoot = new File(DiskConfig.STORE_ROOT);
        File publicRoot = new File(DiskConfig.PUBLIC_ROOT);
        long realTotalUserSize = fileAnalyseDao.getRealTotalUserSize();
        long userTotalSize = fileAnalyseDao.getUserTotalSize();
        long publicTotalSize = fileAnalyseDao.getPublicTotalSize();
        data.put("store_type", DiskConfig.STORE_TYPE);
        data.put("file_count", fileAnalyseDao.getFileCount());
        data.put("dir_count", fileAnalyseDao.getDirCount());
        data.put("real_user_size", realTotalUserSize);
        data.put("total_user_size", userTotalSize);
        data.put("total_public_size", publicTotalSize);
        data.put("store_total_space", storeRoot.getTotalSpace());
        data.put("store_free_space", storeRoot.getFreeSpace());
        data.put("public_total_space", publicRoot.getTotalSpace());
        data.put("public_free_space", publicRoot.getFreeSpace());
        data.put("store_root", storeRoot.getPath());
        data.put("public_root", publicRoot.getPath());
        data.put("store_type_switching", DiskConfig.isStoreSwitching());
        return JsonResult.getInstance(data);
    }
}
