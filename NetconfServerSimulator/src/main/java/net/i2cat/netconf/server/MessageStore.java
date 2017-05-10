package net.i2cat.netconf.server;

import java.util.List;

import net.i2cat.netconf.rpc.RPCElement;

/**
 * Netconf messages store interface. It allows storing Netconf messages and obtain it at any moment
 * 
 * @author Julio Carlos Barrera
 * 
 */
public interface MessageStore {

	/**
	 * Store a Netconf message
	 * 
	 * @param message
	 *            any Netconf message to store
	 */
	public void storeMessage(RPCElement message);

	/**
	 * Get the full list of stored messages
	 * 
	 * @return a {@link List} of stored messages
	 */
	public List<RPCElement> getStoredMessages();
}
