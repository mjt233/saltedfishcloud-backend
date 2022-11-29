package test.sfc;

import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.model.UpdateContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Updater("ext-demo")
public class DemoUpdater {
    private final static String LOG_PREFIX = "[Ext-Demo测试更新器]";

    @UpdateAction("2.3.3")
    public void doUpdate(UpdateContext context) throws Exception {
        log.info("{}测试更新，从{}更新到{}", LOG_PREFIX, context.getFrom(), context.getTo());
    }

    @UpdateAction("2.3.4")
    public void doUpdate2(UpdateContext context) throws Exception {
        log.info("{}测试更新，从{}更新到{}", LOG_PREFIX, context.getFrom(), context.getTo());
    }

    @UpdateAction("2.3.5")
    public void doUpdate2() throws Exception {
        log.info("{}测试更新，我没有参数", LOG_PREFIX);
    }
}
