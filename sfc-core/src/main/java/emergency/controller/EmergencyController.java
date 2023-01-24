package emergency.controller;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import emergency.EmergencyApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
public class EmergencyController {

    @GetMapping("/api/error")
    public void test(HttpServletResponse response) throws IOException {
        response.setHeader("content-type", "text/plain;charset=utf-8");
        if(EmergencyApplication.throwable != null) {
            response.getWriter().println("异常出现时间：" + EmergencyApplication.errorDate);
            EmergencyApplication.throwable.printStackTrace(response.getWriter());
        }
    }

    @GetMapping("/api/admin/sys/restart")
    public JsonResult restart() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(500);
                SpringContextUtils.restart();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return JsonResult.emptySuccess();
    }
}
