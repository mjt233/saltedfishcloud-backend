package emergency.advice;

import emergency.controller.EmergencyController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class ExceptionAdvice {
    @Autowired
    private EmergencyController emergencyController;

    @ExceptionHandler(Exception.class)
    public ModelAndView handle404(HttpServletRequest request, HttpServletResponse response, Exception e) throws Exception {
        return emergencyController.errorView(response);
    }
}
