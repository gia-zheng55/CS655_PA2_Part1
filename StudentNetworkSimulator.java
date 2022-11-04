import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // A variable
    private int LAR;
    private int LPS;
    private int currentSeq;
    private ArrayList<Packet> bufferA;

    // Sender buffer size
    public static final int bufferASize = 50;

    // B variable
    private int LPA;
    private int NPE;
    private Queue<Packet> bufferB;

    private int PacketToLayer5InA = 0;

    private int AckSentByB = 0;

    private int CorruptedPackets = 0;

    // Output variables
    private int packetNum = 0;
    private int retransmitNum = 0;
    private int ackNum = 0;
    private int firstACKNum = 0;
    private double RTT = 0;
    private double ComTime = 0;

    // record the sending time of packets with retransmission
    private HashMap<Integer, Double> retransmitTime = new HashMap<>();

    // record the sending time of packets
    private HashMap<Integer, Double> normalTime = new HashMap<>();

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // Calculate the difference between to sequence numbers
    protected int difference(int low, int high) {
        int diff = high - low;
        return diff < 0 ? diff + LimitSeqNo : diff;
    }

    // check if the packet is corrupt
    protected boolean checkCS(Packet packet) {
        int getCS = packet.getChecksum();
        int checksum = generateChecksum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload());
        return getCS == checksum;
    }

    // Apply TCP checksum by integrate sequence number and acknowledge number in the checksum
    protected int generateChecksum(int seq, int ack, String payload) {
        int checksum = 0;
        checksum += seq;
        checksum += ack;
        for (int i = 0; i < payload.length(); i++) {
            checksum += (byte) payload.charAt(i);
        }
        return checksum;
    }

    // every time receive a correct ACK, remove the packet from A's buffer
    protected void removeAckedPackets(int index){
        bufferA.subList(0, index).clear();
        if(bufferA.size() == 0){
            stopTimer(A);
        }
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to ensure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // if the buffer is full, drop it
        if (bufferA.size() >= bufferASize) {
            System.out.println("A side: A's buffer is full, drop this packet.");
            return;
        }

        String payload = message.getData();
        int acknum = 0;
        int seqnum = currentSeq;
        currentSeq = (currentSeq + 1) % LimitSeqNo;
        int checksum = generateChecksum(seqnum, acknum, payload);
        Packet packet = new Packet(seqnum, acknum, checksum, payload);
        bufferA.add(packet);

        if (difference(LAR, LPS) < WindowSize) {
            System.out.println("A side: Send packet " + packet.getSeqnum() + " to B");
            stopTimer(A);
            toLayer3(A, packet);
            startTimer(A, RxmtInterval);
            retransmitTime.put(packet.getSeqnum(), getTime());
            normalTime.put(packet.getSeqnum(), getTime());
            LPS = (LPS + 1) % LimitSeqNo;
            packetNum += 1;
        }
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        // check if packet is corrupted
        if (!checkCS(packet)) {
            System.out.println("A side: ACK packet " + packet.getAcknum() + " corrupted");
            CorruptedPackets++;
        }else{
            ackNum++;
            int ack = packet.getAcknum();

            if (ack == LAR) {
                System.out.println("A side: ACK packet " + ack + " duplicated");
                if (bufferA.size() == 0) {
                    return;
                }
                // B records sequence of packet in payload
                if(!packet.getPayload().equals("")){
                    int sentSeq = Integer.parseInt(packet.getPayload());
                    // if this packet is not a resent packet
                    // cumulate RTT
                    if (normalTime.containsKey(sentSeq)) {
                        RTT += getTime() - normalTime.get(sentSeq);
                        normalTime.remove(sentSeq);
                        firstACKNum++;
                    }
                }
                // duplicate ack means the correct packet is received by B
                normalTime.remove(bufferA.get(0).getSeqnum());
                stopTimer(A);
                toLayer3(A, bufferA.get(0));
                startTimer(A, RxmtInterval);
                retransmitNum += 1;
            } else {
                System.out.println("A side: Correct ACK packet: " + ack);
                int dif = difference(LAR, ack);

                // if this packet has not been resended, cumulate RTT
                if (dif == 1 && normalTime.containsKey(ack)) {
                    RTT += getTime() - normalTime.get(ack);
                    normalTime.remove(ack);
                    firstACKNum++;
                }

                // retransmission time may includes normal RTT
                // it is possible that the received ACK is not for (LAR+1)%LimitSeqNo
                for (int i = 1; i <= dif; i++) {
                    System.out.println("ack time: " + getTime() + ", send time" + retransmitTime.get((LAR + i) % LimitSeqNo));
                    if(retransmitTime.containsKey((LAR + i) % LimitSeqNo)){
                        ComTime += getTime() - retransmitTime.get((LAR + i) % LimitSeqNo);
                    }
                    retransmitTime.remove((LAR + i) % LimitSeqNo);
                }

                LAR = ack;
                removeAckedPackets(dif);

                // get the index of LPS packet in sender's buffer
                int index = 0;
                for (int i = 0; i < bufferA.size(); i++) {
                    if (bufferA.get(i).getSeqnum() == LPS) {
                        index = i;
                        break;
                    }
                }

                // send new packets
                // new packets amount equals to removed acknowledge packets if have enough packets
                for (int i = index + 1; i <= Math.min(index + dif, bufferA.size()-1); i++) {
                    Packet p = bufferA.get(i);
                    stopTimer(A);
                    toLayer3(A, p);
                    startTimer(A, RxmtInterval);
                    retransmitTime.put(p.getSeqnum(), getTime());
                    normalTime.put(p.getSeqnum(), getTime());
                    LPS = (LPS + 1) % LimitSeqNo;
                    packetNum += 1;
                }

            }
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("RTO: Packet " + bufferA.get(0).getSeqnum() + " time out, retransmit it");
        stopTimer(A);
        toLayer3(A, bufferA.get(0));
        startTimer(A, RxmtInterval);
        normalTime.remove(bufferA.get(0).getSeqnum());
        retransmitNum += 1;
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        LAR = -1;
        LPS = -1;
        currentSeq = FirstSeqNo;
        bufferA = new ArrayList<>();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    // 1. corrupted packets: drop
    // 2. out of range of RWS: drop and ack
    // 3. NPE packet: ack and send to layer5
    // 4. ooo packet: ack and buffer
    protected void bInput(Packet packet) {
        int seqnum = packet.getSeqnum();
        String payload = packet.getPayload();

        // S1
        if (!checkCS(packet)) {
            System.out.println("B side: Packet " + seqnum + " corrupted");
            CorruptedPackets++;
            return;
        }else{
            // S2
            if (!ifInRange(seqnum)) {
                System.out.println("B side: Packet " + seqnum + " out of range");
                int ack = NPE - 1 < 0 ? LimitSeqNo + (NPE - 1) : NPE - 1; // duplicate ack in A
                // seq does not matter
                toLayer3(B, new Packet(111, ack, generateChecksum(111, ack, ""), ""));
                AckSentByB++;
            } else {
                // S3
                if (seqnum == NPE) {
                    System.out.println("B side: Correct packet:" + seqnum);
                    String backPayload = String.valueOf(seqnum);
                    toLayer5(payload);
                    PacketToLayer5InA++;
                    int ack = seqnum;
                    NPE = updateRW(NPE);
                    LPA = updateRW(LPA);
                    // first check if buffered out of order packets
                    while (!bufferB.isEmpty()) {
                        Packet next = bufferB.peek();
                        if (next.getSeqnum() == NPE) {
                            toLayer5(next.getPayload());
                            PacketToLayer5InA++;
                            ack = NPE;
                            NPE = updateRW(NPE);
                            LPA = updateRW(LPA);
                            bufferB.poll();
                        } else {
                            break;
                        }
                    }
                    Packet ackPacket = new Packet(111, ack, generateChecksum(111, ack, backPayload), backPayload);
                    toLayer3(B, ackPacket);
                    AckSentByB++;
                } else {
                    // S4
                    System.out.println("A side: Packet " + seqnum + " out of order");
                    bufferB.add(packet);
                    int ack = generateACKNum();
                    toLayer3(B, new Packet(111, ack, generateChecksum(111, ack, packet.getSeqnum() + ""), String.valueOf(packet.getSeqnum())));
                    AckSentByB++;
                }
            }
        }
    }

    protected int generateACKNum(){
        return NPE - 1 < 0 ? LimitSeqNo + (NPE - 1) : NPE - 1;
    }

    // check if this packet can be accepted
    protected boolean ifInRange(int seq) {
        if (LPA >= NPE) {
            return NPE <= seq && seq <= LPA;
        } else {
            return NPE <= seq || seq <= LPA;
        }
    }

    // B receive the correct packet, sliding the RW by 1
    protected int updateRW(int num) {
        return (num + 1) % LimitSeqNo;
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        LPA = WindowSize - 1;
        NPE = 0;
        bufferB = new LinkedList<>();
    }

    protected void Simulation_done() {
        double lost_ratio = (double) (retransmitNum-CorruptedPackets) / (packetNum+retransmitNum+AckSentByB);
        double corrupt_ratio = (double) (CorruptedPackets) / (packetNum+AckSentByB+CorruptedPackets);
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + packetNum);
        System.out.println("Number of retransmissions by A:" + retransmitNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + PacketToLayer5InA);
        System.out.println("Number of ACK packets sent by B:" + AckSentByB);
        System.out.println("Number of corrupted packets:" + CorruptedPackets);
        System.out.println("Ratio of lost packets:" + lost_ratio);
        System.out.println("Ratio of corrupted packets:" + corrupt_ratio);
        System.out.println("Average RTT:" + RTT/firstACKNum);
        System.out.println("Average communication time:" + ComTime/packetNum);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        System.out.println("Total RTT time :" + RTT);
        System.out.println("Total communication time :" + ComTime);
        System.out.println("Number of ACK packets received by A :" + ackNum);
    }

}