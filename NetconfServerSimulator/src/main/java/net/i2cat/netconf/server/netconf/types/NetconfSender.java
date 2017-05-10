/**
 * Interface for a class that can send out Strings with Netconf/XML content.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server.netconf.types;

import java.io.IOException;

public interface NetconfSender {

    public void send(String xmlMessage) throws IOException;

}
