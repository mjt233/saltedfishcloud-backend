package emergency.controller;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import emergency.EmergencyApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

@RestController
public class EmergencyController {

    @GetMapping("/api/error")
    public void error(HttpServletResponse response) throws IOException {
        response.setHeader("content-type", "text/plain;charset=utf-8");
        if(EmergencyApplication.throwable != null) {
            response.getWriter().println("异常出现时间：" + EmergencyApplication.errorDate);
            EmergencyApplication.throwable.printStackTrace(response.getWriter());
        }
    }


    @GetMapping("/api/errorView")
    public ModelAndView errorView(HttpServletResponse response) throws IOException {
        try(StringWriter stringWriter = new StringWriter(); PrintWriter writer = new PrintWriter(stringWriter)) {
            response.setHeader("is-emergency", "1");
            EmergencyApplication.throwable.printStackTrace(writer);
            ModelAndView mav = new ModelAndView();
            mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            mav.setViewName("exception");
            mav.addObject("date", EmergencyApplication.errorDate);
            mav.addObject("message", stringWriter.toString());
            return mav;
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
