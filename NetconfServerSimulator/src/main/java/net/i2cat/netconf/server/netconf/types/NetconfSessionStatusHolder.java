package net.i2cat.netconf.server.netconf.types;

public class NetconfSessionStatusHolder {

    private NetconfSessionStatus actual = NetconfSessionStatus.NOTSET;

    public void change( final NetconfSessionStatus newActual ) {
        this.actual = newActual;
    }

    public boolean equals( final NetconfSessionStatus status ) {
        return actual.equals(status);
    }

    public boolean less( final NetconfSessionStatus status ) {
    	return actual.getIndex() < status.getIndex();
    }

}
