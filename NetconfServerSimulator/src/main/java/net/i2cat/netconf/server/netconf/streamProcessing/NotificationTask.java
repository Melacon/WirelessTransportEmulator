package net.i2cat.netconf.server.netconf.streamProcessing;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.i2cat.netconf.server.Console;
import net.i2cat.netconf.server.netconf.types.NetworkElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Add additional commands to automate notification sending:
 *
 *  'tmmm n sec to send notification mmm for n times every s seconds'<br>
 *  't         provide status'<br>
 *  'tx    stop execution'<br>
 *
 */
public class NotificationTask {

    private static final Log LOG  = LogFactory.getLog(NotificationTask.class);

    private static Pattern PATTERN_CLI = Pattern.compile("(\\d+) (\\d*) (\\d*)");

    private final NetworkElement theNe;
    private final NetconfMessageProcessorThreadSender sender;
    private final ScheduledExecutorService notificationExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Console console;

    private NotificationRunnable notificationTask = null;


    /**
     * Send out notifications
     * @param theNe document
     * @param sender thread
     * @param console for output
     */
    public NotificationTask(NetworkElement theNe, NetconfMessageProcessorThreadSender sender, Console console) {
        this.theNe = theNe;
        this.sender = sender;
        this.console = console;
    }

    private void console(String msg) {
        this.console.cliOutput(msg);
    }

    /**
     * Send notifications in a thread
     * @param command
     *   'tm n s To send n times every s seconds'<br>
     *   't  Provide status'<br>
     *   'tx Stop execution'<br>
     */
    public void doProcessUserAction(String command) {
        console("User command (n): '"+command+"'");
        if (command.startsWith("x")) {
            console("Stop notification task");
            if (isTaskRunning()) {
                notificationTask.cancel();
            } else {
                console("Status: Nothing to stop");
            }
        } else {
            boolean randomize = false;
            if (command.startsWith("r")) {
                command = command.substring(1);
                randomize = true;
            }

            Matcher matcher = PATTERN_CLI.matcher(command);
            if (matcher.matches()) {
                Integer idx = Integer.parseInt(matcher.group(1));
                String xmlSubTree = theNe.getEvent(idx);
                if (xmlSubTree != null) {
                    int number = 1;
                    if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                        number = Integer.parseInt(matcher.group(2));
                    }
                    long seconds = 1L;
                    if (matcher.group(3) != null && !matcher.group(3).isEmpty()) {
                        seconds = Long.parseLong(matcher.group(3));
                    }
                    if (!isTaskRunning()) {
                        notificationTask = new NotificationRunnable(xmlSubTree, number, seconds, randomize);
                        notificationTask.setExecutor(notificationExecutor.scheduleAtFixedRate(notificationTask, 0L, seconds, TimeUnit.SECONDS));
                    }
                }
            }
            if (isTaskRunning()) {
                console("Status: Notification task running. Status: "+notificationTask.getStatus());
            } else {
                console("Status: No notification task running");
            }
        }
    }

    private boolean isTaskRunning() {
        return notificationTask != null && notificationTask.isRunning();
    }

    private class NotificationRunnable implements Runnable {
        private final String xmlMessage;

        private int numberToSend;
        private volatile ScheduledFuture<?> scheduleAtFixedRate = null;

        private final long seconds;

        private final boolean randomize;

        private NotificationRunnable(String xmlMessage, int number, long seconds, boolean randomize) {
            this.xmlMessage = xmlMessage;
            this.numberToSend = number;
            this.seconds = seconds;
            this.randomize = randomize;
        }

        /**
         * @return status string for class
         */
        public String getStatus() {
            StringBuffer statusMsg = new StringBuffer();
            statusMsg.append("[NotificationsSender-numberToSend: ");
            statusMsg.append(numberToSend);
            statusMsg.append("Randomize: ");
            statusMsg.append(randomize);
            return statusMsg.toString();
        }

        public boolean isRunning() {
            if (scheduleAtFixedRate != null) {
                return !scheduleAtFixedRate.isDone();
            }
            return false;
        }

       /**
         * Cancel tread
         */
        public void cancel() {
            if (scheduleAtFixedRate != null) {
                scheduleAtFixedRate.cancel(false);
            } else {
                LOG.info("Can not cancel notification thread");
            }
        }

        /**
         * @param scheduleAtFixedRate for stopping the task
         */
        public void setExecutor(ScheduledFuture<?> scheduleAtFixedRate) {
            this.scheduleAtFixedRate = scheduleAtFixedRate;
        }

        @Override
        public void run() {
            try {
                if (randomize) {
                    long randomSeconds = ThreadLocalRandom.current().nextLong(0, seconds);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(randomSeconds));
                }
                sender.send(xmlMessage);
                console("Tasks notification: "+xmlMessage);
            } catch (IOException | InterruptedException e) {
                LOG.info("Can not send notification "+e.getMessage());
            }
            if (numberToSend-- <= 0) {
                cancel();
            }
        }
    }


}
