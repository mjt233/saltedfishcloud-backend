package emergency;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;

import java.util.Date;


/**
 * 紧急模式，启动失败时会启动该模式
 */
@SpringBootApplication(
    exclude= {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class,
        DataRedisAutoConfiguration.class
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
