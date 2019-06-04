package net.i2cat.netconf.server.cli;

public interface CommandLineProcessor {

    /**
     * @param cmdLine
     */
    void doExecute(String cmdLine);

    /**
     * @return true if quit command was received
     */
    boolean isQuit();

    /**
     *
     */
    void doWelcome();

}
