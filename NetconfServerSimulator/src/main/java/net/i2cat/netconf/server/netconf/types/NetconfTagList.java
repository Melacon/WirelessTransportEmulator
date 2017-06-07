/**
 * Creates a list of XML Tags to identify an incoming XML Message an a simple base.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server.netconf.types;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NetconfTagList {

    private static final Log log  = LogFactory.getLog(NetconfTagList.class);

    private final List<NetconfTag> tags = new ArrayList<NetconfTag>();

    public void pushTag(String tagName, String namespace) {
        if (!tagName.isEmpty()) {
            int size = tags.size();
            if (size > 0) {
                NetconfTag lastTag = tags.get(size-1);
                if (! lastTag.getName().equals(tagName)) {
                    tags.add(new NetconfTag(namespace, tagName));
                }
            } else {
                tags.add(new NetconfTag(namespace, tagName));
            }
        }
    }

    public int size() {
        return tags.size();
    }

    public List<NetconfTag> getList() {
        return tags;
    }

    public boolean checkTags(String ...tagList) {
        if (tags.size() >= tagList.length) {
            for (int idx=0; idx < tagList.length; idx++) {
                if (! tagList[idx].equals(tags.get(idx).getName())) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public int getTagPosition(String tag) {
        for (int idx=0; idx < tags.size(); idx++) {
            if (tags.get(idx).getName().equals(tag)) {
                return idx;
            }
        }
        return -1;
    }

    public void addTagsValue(String value) {
        if (tags.size() > 0) {
            tags.get(tags.size()-1).addValue(value);
        } else {
            log.debug("Tag value without tag entry.");
        }

    }

    public String getTagsPath() {
        StringBuffer name = new StringBuffer();
        for (NetconfTag tag : tags) {
            if (name.length() > 0) {
                name.append(".");
            }
            name.append(tag.getName());
        }
        return name.toString();
    }




    /**
     * Create a sublist starting at fromIdx
     * @param fromIdx first element in the new list (Example: List with 0:get 1:filter 2:root => 2 to start with root
     * @return new created list
     */
    public NetconfTagList getSubsetFromIndex(int fromIdx) {
        NetconfTagList res = new NetconfTagList();
        for (int idx = fromIdx; idx < tags.size(); idx++) {
            res.tags.add(tags.get(idx));
        }
        return res;
    }

    public boolean isEmtpy() {
        return tags.isEmpty();
    }

    public NetconfTag getRoot() {
        return tags.get(0);
    }

    public boolean endsWithLeave() {
        if (tags.size() > 0) {
            return ! tags.get(tags.size()-1).hasOneValue();
        } else {
            return false;
        }
    }

    public String getSubTreePath() {
        StringBuilder res = new StringBuilder();
        for (NetconfTag tag : tags) {
            if (tag.hasOneValue()) {
                res.append("[");
                res.append(tag.getName());
                res.append("=\"");
                res.append(tag.getValue());
                res.append("\"]");
            } else {
                if (res.length() != 0) {
                    res.append("/");
                }
                res.append(tag.getName());
            }
        }
        return res.toString();
    }

    /**
     * Return Index-tag with given index.
     * @param i starting with 0
     * @return tag or null
     */
    public NetconfTag getIdx(int i) {
        int idx = 0;
        for (NetconfTag tag : tags) {
            if (tag.hasOneValue()) {
                if (idx == i) {
                    return tag;
                }
                idx++;
            }
        }
        return null;
    }

    public String asCompactString() {
        StringBuffer sb = new StringBuffer();
        for (NetconfTag tag : tags) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(tag.getName());
            sb.append(":");
            sb.append(tag.getValuesAsString());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "NetconfTagList [tags=" + tags + "]";
    }


}
