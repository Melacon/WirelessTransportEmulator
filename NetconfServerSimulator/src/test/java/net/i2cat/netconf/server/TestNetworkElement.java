package net.i2cat.netconf.server;
import net.i2cat.netconf.server.netconf.types.NetconfTagList;
import net.i2cat.netconf.server.netconf.types.NetworkElement;

public class TestNetworkElement {


    public static void main(String[] args) throws Exception {

        NetworkElement ne = new NetworkElement("src/main/resources/ne_model/DVM_MicrowaveModelCore10.xml","yang/yangNeModel");
        String xml;

        System.out.println("Test-1 Start");
        xml = ne.getXmlSubTreeAsString("//NetworkElement");
        xml = xml.replaceFirst("NetworkElement", "NetworkElement xmlns=\"uri:onf:CoreModel-CoreNetworkModule-ObjectClasses\"");
        System.out.println(xml);
        System.out.println("Test-1 End");

        System.out.println("Test-2 Start");
        xml = ne.getXmlSubTreeAsString("//NetworkElement");
        xml = xml.replaceFirst("NetworkElement", "NetworkElement xmlns=\"uri:onf:CoreModel-CoreNetworkModule-ObjectClasses\"");
        System.out.println(xml);
        System.out.println("Test-2 End");

        System.out.println("Test-3");
        NetconfTagList tags = new NetconfTagList();
        tags.pushTag("MW_EthernetContainer_Pac", "ns1");
        tags.pushTag("layerProtocol", "ns1");
        tags.addTagsValue("LP-ETH-CTP-ifIndex1");
        xml = ne.assembleRpcReplyFromFilterMessage("m-45", tags);
        System.out.println(xml);

        System.out.println("Test-4");
        tags = new NetconfTagList();


        tags.pushTag("MW_AirInterface_Pac", "ns1");
        tags.pushTag("layerProtocol", "ns1");
        tags.addTagsValue("LP-MWPS-ifIndex1");
        tags.pushTag("airInterfaceCurrentProblems", "ns1");
        xml = ne.assembleRpcReplyFromFilterMessage("m-46", tags);
        System.out.println(xml);


    }
}
