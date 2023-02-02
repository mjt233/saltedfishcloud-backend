package emergency.advice;

import emergency.EmergencyApplication;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;

@ControllerAdvice
public class ExceptionAdvice implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("11");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handle404(HttpServletRequest request, Exception e) throws Exception {
        try(StringWriter stringWriter = new StringWriter();PrintWriter writer = new PrintWriter(stringWriter)) {
            EmergencyApplication.throwable.printStackTrace(writer);
            ModelAndView mav = new ModelAndView();
            mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            mav.setViewName("exception");
            mav.addObject("date", EmergencyApplication.errorDate);
            mav.addObject("message", stringWriter.toString());
            return mav;
        }
    }
}
