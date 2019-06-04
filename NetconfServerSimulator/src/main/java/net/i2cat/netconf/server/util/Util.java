package net.i2cat.netconf.server.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Util {

    private static final Log LOG = LogFactory.getLog(Util.class);

    public static void sleepMilliseconds(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            LOG.warn(e.getMessage());
        }
    }


}
