package net.i2cat.netconf.server;

import net.i2cat.netconf.rpc.Query;
import net.i2cat.netconf.rpc.Reply;

/**
 * Netconf behaviour defined by a {@link Query} and a {@link Reply}
 *
 * @author Julio Carlos Barrera
 *
 */
public class Behaviour {

    private final Query    query;
    private final Reply    reply;

    private final boolean    consume;

    /**
     * Creates a Behaviour that NOT consumes itself
     *
     * @param query
     * @param reply
     */
    public Behaviour(Query query, Reply reply) {
        this.query = query;
        this.reply = reply;
        this.consume = false;
    }

    /**
     * Creates a Behaviour
     *
     * @param query
     * @param reply
     * @param consume
     *            if true, the behaviour will be consumed when Query matched; otherwise not
     */
    public Behaviour(Query query, Reply reply, boolean consume) {
        this.query = query;
        this.reply = reply;
        this.consume = consume;
    }

    public Query getQuery() {
        return query;
    }

    public Reply getReply() {
        return reply;
    }

    public boolean isConsume() {
        return consume;
    }

}
