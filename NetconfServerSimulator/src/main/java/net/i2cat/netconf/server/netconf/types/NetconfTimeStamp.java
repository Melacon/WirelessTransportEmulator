/**
 * Class handling time stamps in NETCONF format.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server.netconf.types;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTimeStamp {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTimeStamp.class);
    private static TimeZone TIMEZONEUTC = TimeZone.getTimeZone("GMT");
    private static SimpleDateFormat dateFormatResult;

    /**
     * Static initialization
     * 2017-03-28T15:11:12Z
     */
    private static void doInit() {
        LOG.debug("Init begin");
        //dateFormatResult =new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormatResult =new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'"); //Netconf
        dateFormatResult.setTimeZone(TIMEZONEUTC);
        LOG.debug("Init end");
    }
    //------------------------------------
    //No public constructor
    private NetconfTimeStamp() {
    }
    /**
     * Get actual timestamp in
     * @return DateAndTime Date in this YANG Format
     */
    public static String getTimeStamp() {
        if (dateFormatResult == null) {
            doInit();
        }
        //Time in GMT
        return getRightFormattedDate(new Date().getTime());
    }

     /**
     * Deliver format in a way that milliseconds are correct.
     * @param dateMillis Date as milliseconds in Java definition
     * @return String
     */
    private static String getRightFormattedDate( long dateMillis ) {
        long tenthOfSeconds = dateMillis % 1000/100L; //Extract 100 milliseconds
        long base = dateMillis / 1000L * 1000L; //Cut milliseconds to 000
        Date newDate = new Date( base + tenthOfSeconds);
        return dateFormatResult.format(newDate);
    }
}
