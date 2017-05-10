/**
 * Creates for one XML leaf an entry with name and namespace and content-value.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server.netconf.types;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NetconfTag {

    private static final Log log  = LogFactory.getLog(NetconfTagList.class);

    private final String namespace;
    private final String name;
    private final List<String> values = new ArrayList<String>();

    public NetconfTag(String namespace, String name) {
        super();
        this.namespace = namespace;
        this.name = name;
    }

    public void addValue( String value) {
        this.values.add(value);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public boolean hasOneValue() {
        return values.size() == 1;
    }

    public String getValue() {
        return values.get(0);
    }

    public String getValuesAsString() {
        StringBuffer sb = new StringBuffer();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(value);
        }
        return sb.toString();
    }


    @Override
    public String toString() {
        return "NetconfTag [namespace=" + namespace + ", name=" + name + ", values=" + values + "]";
    }



}
