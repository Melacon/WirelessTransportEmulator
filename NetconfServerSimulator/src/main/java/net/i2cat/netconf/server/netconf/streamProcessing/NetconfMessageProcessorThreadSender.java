package net.i2cat.netconf.server.netconf.streamProcessing;

import java.io.IOException;

public interface NetconfMessageProcessorThreadSender {

    public void send(String xmlMessage) throws IOException;

}
