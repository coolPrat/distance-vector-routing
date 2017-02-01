import java.net.*;
import java.util.*;


/*
* Server.java
* This class will run two threads.
*   1. For receiving and processing messages from other systems.
*   2. Sending it's routing table to others.
*/
public class Server extends Thread{

    private int readPort = 35000;
    private int writePort = 35001;
    static HashMap<String, String[]> myTab = new HashMap<>();
    private DatagramSocket forSending;
    private DatagramSocket forReceiving;
    private DatagramPacket input;
    private DatagramPacket output;
    static HashSet<String> nbrs = new HashSet<>();
    private int noOfNbrs = 0;
    private String myAddr;
    private byte[] mask= {(byte)255, (byte)255, (byte)255, (byte)0};
    private HashMap<String, Long> timeTable = new HashMap<>();


    /**
     * Constructor for Server class. We start a thread for receiving updates and take nbr router's information.
     * @throws Exception
     */
    Server() throws Exception {
        this.myAddr = InetAddress.getLocalHost().getHostAddress();
        try{
            forSending = new DatagramSocket(writePort);
            forReceiving = new DatagramSocket(readPort);
            this.start();
        } catch(Exception e) {
            System.out.println("Can't create socket " + e.getMessage());
        }
        Scanner inScan = new Scanner(System.in);
        System.out.println("Enter no of nbrs (0, 1 or 2) : ");
        noOfNbrs = inScan.nextInt();
        if (noOfNbrs > 2 || noOfNbrs < 0) {
            System.out.println("Invalid no of nbrs");
            System.exit(1);
        }
        String nbr;
        int cost;
        for (int i = 0; i < noOfNbrs; i++) {
            System.out.println("Enter nbr no " + (i + 1) + ": ");
            nbr = inScan.next();
            nbrs.add(nbr);
            System.out.println("Enter cost for nbr no " + (i + 1) + ": ");
            cost = inScan.nextInt();
            this.send("connection", nbr, ""+cost);
        }
    }


    /**
     * Run method from Thread class.
     * Here the thread will receive the message from other servers and will process them according to
     * the message type specified.
     */
    public void run() {
        byte[] buff = new byte[1024];
        input = new DatagramPacket(buff, 1024);
        try {
            while (true) {
                this.forReceiving.receive(input);
                String mess = new String(input.getData());
                if (get("Type", mess).equals("connection")) {
                    System.out.println("connection request from " + input
                            .getAddress().getHostAddress());
                    if(this.myTab.containsKey(input.getAddress()
                            .getHostAddress())) {
                        System.out.println("connection denied");
                        this.send("denied", input.getAddress().getHostAddress
                                (), "");
                    } else {
                        System.out.println("connection accept");
                        this.nbrs.add(input.getAddress().getHostAddress());
                        this.addInTable(input.getAddress().getHostAddress(), input.getAddress().getHostAddress(), ""+Integer.parseInt(get("Value", mess).trim()));
                        this.send("accept", input.getAddress().getHostAddress
                                (), get("Value", mess).trim());
                    }
                } else if(get("Type", mess).equals("denied")) {
                    System.out.println("denied");
                } else if(get("Type", mess).equals("accept")) {
                    if(!this.myTab.containsKey(input.getAddress()
                            .getHostAddress())) {
                        this.nbrs.add(input.getAddress().getHostAddress());
                        this.addInTable(input.getAddress().getHostAddress(), input.getAddress().getHostAddress(), ""+Integer.parseInt(get("Value", mess).trim()));
                    }
                } else if(get("Type", mess).equals("update")) {
                    this.timeTable.put(input.getAddress().getHostAddress(), System.currentTimeMillis());
                    updateTable(get("Value", mess), input.getAddress().getHostAddress());
                } else if(get("Type", mess).equals("poisoned")) {
                    this.addInTable(get("Value", mess), "", ""+Integer.MAX_VALUE);
                }
                buff = new byte[1024];
                input = new DatagramPacket(buff, 1024);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds given destination in routing table. While adding it will first get the network prefix and add it
     * as destination.
     * @param dest    IP address of destination
     * @param nxtHp   IP address of next hope
     * @param cost    cost of the link
     */
    public void addInTable(String dest, String nxtHp, String cost) {
        this.myTab.put(this.getNetPre(dest), new String[] {nxtHp, cost});
    }

    /**
     * Checks for a dead node in neighboring nodes.
     * If a node doesn't send update message for more than 5 secs then
     * it's said to be dead.
     * @throws Exception
     */
    public void checkDeadNode() throws Exception {
        long t = System.currentTimeMillis();
        for (String key: this.timeTable.keySet()) {
            if (t - this.timeTable.get(key) > 5000) {
                this.addInTable(key, "", "" + Integer.MAX_VALUE);
                Iterator itr = this.nbrs.iterator();
                while (itr.hasNext() && this.myTab.size() > 0) {
                    String a = (String)itr.next();
                    this.send("poisoned", a, key);
                }
            }
        }
    }


    /**
     * This method returns network prefix for given IP address.
     * @param dest    IP address of the destiation.
     * @return        network prefix for given IP address
     */
    public String getNetPre(String dest) {
        String netPrefix = "";
        String parts[] = dest.split("\\.");
        for (int i = 0; i < parts.length; i ++) {
            netPrefix += String.valueOf(Integer.parseInt(parts[i]) & this.mask[i]);
            if (i < parts.length - 1) {
                netPrefix += ".";
            }
        }
        return netPrefix;
    }


    /**
     * This function will update the routing table if new route is found based on update message sent
     * by neighboring node.
     * @param value    String representation of the routing table received from neighboring node
     * @param sender    IP address of the neighboring node
     */
    public void updateTable(String value, String sender) {
        HashMap<String, String[]> newTab = formTab(value);
        for (String key: newTab.keySet()) {
            if (!key.equals(this.getNetPre(myAddr))) {
                if (Server.myTab.containsKey(key)) {
                    if (Integer.parseInt(Server.myTab.get(key)[1].trim()) > Integer.parseInt(Server.myTab.get(this.getNetPre(sender))[1].trim()) + Integer.parseInt(newTab.get(key)[1].trim())) {
                        this.addInTable(key, sender, String.valueOf(Integer.parseInt(Server.myTab.get(this.getNetPre(sender))[1].trim()) + Integer.parseInt(newTab.get(key)[1].trim())));
                    }
                } else {
                    this.addInTable(key, sender,String.valueOf(Integer.parseInt(Server.myTab.get(this.getNetPre(sender))[1].trim()) + Integer.parseInt(newTab.get(key)[1].trim())));
                }
            }
        }
    }

    /**
     * This function will generate a hashmap from the string representation of
     * a routing table.
     * @param input    String representation of a routing table
     * @return    Hashmap corresponding to input string
     */
    public HashMap<String, String[]> formTab(String input) {
        HashMap<String, String[]> tab = new HashMap<>();
        String[] rows = input.split("\\|");
        String key;
        String[] value;
        for (String entry : rows) {
            String[] data = entry.split("=");
            key = data[0];
            value = data[1].split(",");
            tab.put(key, new String[] {value[0].substring(1), value[1].substring(0, value[1].length() - 1)});
        }
        return tab;
    }

    /**
     * This meethod is used to send a message to neighboring nodes.
     * Message type can be one of following:
     *      1. connection
     *      2. accept
     *      3. update
     *
     * @param type    Message type
     * @param dest    IP address of the destination
     * @param value   The message to send
     * @throws Exception
     */
    public void send(String type, String dest, Object value) throws Exception {
        Message m = new Message(type, value);
        this.output = new DatagramPacket(m.toString().getBytes(), m.toString
                ().getBytes().length);
        this.output.setAddress(InetAddress.getByName(dest));
        this.output.setPort(readPort);
        this.forSending.send(this.output);
    }


    /**
     * This function will extract value for given key from the message.
     * @param key    The to look-up
     * @param m      The message
     * @return       value for given key from the message.
     */
    public String get(String key, String m) {
        String[] mAsArray = m.split(";");
        for(int i = 0; i < 2; i++){
            if(mAsArray[i].startsWith(key+":")) {
                return mAsArray[i].split(":")[1].trim();
            }
        }
        return null;
    }

    /**
     * This methid will breadcast the routing table to all neighboring nodes.
     * @throws Exception
     */
    public void broadcastTable() throws Exception {
        Iterator itr = this.nbrs.iterator();
        while (itr.hasNext() && this.myTab.size() > 0) {
            String a = (String)itr.next();
            this.send("update", a, Server.myTab);
        }
    }

    /**
     * The main program.
     * @param args    Command line arguments (ignored)
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
            Server s = new Server();
            String table;
            String a = "";
            Iterator<String> itr;
            int count = 0;
            while(true) {
                s.checkDeadNode();
                s.broadcastTable();
                if (count % 3 == 0) {
                    table = "\n\n-DESTINATION-\t -SUBNET MASK-\t--NEXT HOP--\t--- COST ---\n";
                    for (String key : s.myTab.keySet()) {
                        table += key + " \t 255.255.255.0 \t" + s.myTab.get(key)[0] + "\t" +
                                s.myTab.get(key)[1] + "\n";
                    }
                    System.out.println(table);
                    table = "";
                }
                Thread.sleep(1000);
                count++;
        }
    }
}

/*
* The Message class.
* This is used to pass information between neighboring servers.
*/
class Message {
    private String type;
    private Object value;

    Message(String type, Object value) {
        this.value = value;
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("Type:"+type+";Value:");
        if (this.type.equals("update")) {
            HashMap<String, String[]> hm = (HashMap<String, String[]>)value;
            for(String key: hm.keySet()) {
                ret.append(key+"=");
                ret.append(java.util.Arrays.toString(hm.get(key)) + "|");
            }
        } else {
            ret.append(value.toString());
        }
        return ret.toString();
    }
}