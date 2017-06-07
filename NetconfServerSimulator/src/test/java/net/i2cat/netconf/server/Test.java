package net.i2cat.netconf.server;
import java.util.Base64;

public class Test {

    public static void main(String[] args) {


        String s = "fdffddfdaf$PTPID(PTPCLOCK)fdsfdfdsfd$PTPID(PTPCLOC2)fdsfsdfds$PTPID(PTPCLOC3)";

        byte[] encoded = Base64.getEncoder().encode("PTPCLOCK".getBytes());
        System.out.println(new String(encoded));   // Outputs "SGVsbG8="

        byte[] decoded = Base64.getDecoder().decode(encoded);
        System.out.println(new String(decoded));    // Outputs "Hello"

        int idx;
        int protect = 20;
        String tag = "$PTPID(";
        String tag2 = ")";
        while ((idx = s.indexOf(tag)) > 0 && protect-- > 0) {
            int idx2 = s.indexOf(tag2, idx + tag.length());
            String substringCode = s.substring(idx + tag.length(), idx2);
            System.out.println("Substring: "+substringCode);
            String res = Base64.getEncoder().encodeToString(substringCode.getBytes());
            String exchange = tag+substringCode+tag2;
            System.out.println("Exchange: "+exchange+" with "+res);
            s = s.replace(exchange, res);
            System.out.println("New: "+s);
        }
        if (protect <= 0) {
            System.out.println("Problem processing String. TERMINATED");
        }

    }

}
