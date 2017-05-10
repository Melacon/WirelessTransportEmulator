/**
 * Netconf session status
 */

package net.i2cat.netconf.server.netconf.types;


public enum NetconfSessionStatus {

    NOTSET(-1),
    INIT(0),
    HELLO_RECEIVED(1),
    CLOSING_SESSION(99),
    SESSION_CLOSED(100);

    private final int index;

    private NetconfSessionStatus( int index ) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

}
