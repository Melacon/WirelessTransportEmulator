/**
 * Message representation of a user CLI command that is forwarded to the Message Processor for further processing.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server.netconf.types;

import net.i2cat.netconf.rpc.RPCElement;

public class UserCommand extends RPCElement {

    private static final long serialVersionUID = 7469960234549882489L;

    private final String command;

    public UserCommand( String command ) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return "NetconfNotification [command=" + command + "]";
    }

}
