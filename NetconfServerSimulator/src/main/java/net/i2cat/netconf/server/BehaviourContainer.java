package net.i2cat.netconf.server;

import java.util.List;

/**
 * Netconf behaviour container interface. It allows storing Netconf behaviours and obtain it at any moment
 * 
 * @author Julio Carlos Barrera
 * 
 */
public interface BehaviourContainer {

	/**
	 * Allows defining a Netconf server behaviour
	 * 
	 * @param query
	 *            Netconf query
	 * @param reply
	 *            Expected Netconf reply
	 */
	public void defineBehaviour(Behaviour behaviour);

	/**
	 * 
	 * @return the behaviors list
	 */
	public List<Behaviour> getBehaviours();
}
