package net.i2cat.netconf.server.exceptions;

/**
 * Generic Server exception to encapsulate runtime errors
 * 
 * @author Julio Carlos Barrera
 * 
 */
public class ServerException extends RuntimeException {

	private static final long	serialVersionUID	= 3512250195612785575L;

	public ServerException() {
		super();
	}

	public ServerException(String message) {
		super(message);
	}

	public ServerException(Throwable cause) {
		super(cause);
	}

	public ServerException(String message, Throwable cause) {
		super(message, cause);
	}
}
