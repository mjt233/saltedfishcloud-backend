package emergency;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

import java.util.Date;


/**
 * 紧急模式，启动失败时会启动该模式
 */
@SpringBootApplication(
    exclude= {
        DataSourceAutoConfiguration.class,
        GsonAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class,
        RedisAutoConfiguration.class
    },
    scanBasePackages = {
        "emergency"
    }
)
public class EmergencyApplication {
    /**
     * 造成启动失败的原因
     */
    public static Throwable throwable;

    /**
     * 失败发生时间
     */
    public static Date errorDate;
}
