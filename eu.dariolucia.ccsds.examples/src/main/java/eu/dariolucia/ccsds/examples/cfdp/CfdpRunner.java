/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.examples.cfdp;

import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntitySubscriber;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.ReportIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.*;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.ut.impl.TcpLayer;
import eu.dariolucia.ccsds.cfdp.ut.impl.UdpLayer;

import java.io.*;
import java.util.Set;

public class CfdpRunner implements ICfdpEntitySubscriber {

    private static final String MIB_FLAG = "--mib";
    private static final String UDP_PORT_FLAG = "--udp";
    private static final String TCP_PORT_FLAG = "--tcp";
    private static final String FILESTORE_FLAG = "--filestore";
    private static final String HELP_FLAG = "--help";

    private static final String DEFAULT_MIB = "mib.xml";
    private static final int DEFAULT_UDP_PORT = 34555;
    private static final int DEFAULT_TCP_PORT = 34555;
    private static final String DEFAULT_FILESTORE = "filestore";

    public static void main(String[] args) throws Exception {
        // Check arguments
        if(args.length == 1 && args[0].equals(HELP_FLAG)) {
            printHelp();
            System.exit(1);
        }

        int udpPort = DEFAULT_UDP_PORT;
        int tcpPort = DEFAULT_TCP_PORT;
        String mibPath = DEFAULT_MIB;
        String filestorePath = DEFAULT_FILESTORE;
        for(int i = 0; i < args.length; ++i) {
            if(args[i].equals(MIB_FLAG)) {
                mibPath = args[++i];
            } else if(args[i].equals(UDP_PORT_FLAG)) {
                udpPort = Integer.parseInt(args[++i]);
            } else if(args[i].equals(TCP_PORT_FLAG)) {
                tcpPort = Integer.parseInt(args[++i]);
            } else if(args[i].equals(FILESTORE_FLAG)) {
                filestorePath = args[++i];
            } else {
                System.err.println("Cannot recognise argument: " + args[i]); 
                printHelp();
                System.exit(1);
            }
        }

        CfdpRunner runner = new CfdpRunner(udpPort, tcpPort, mibPath, filestorePath);
        runner.start();
    }

    private static void printHelp() {
        System.out.println("Usage: CfdpRunner " +       
                "[--udp <port: default 34555>] " +
                "[--tcp <port: default 34555>] " +
                "[--mib <path to mib file: default 'mib.xml'>] " +
                "[--filestore <path to filestore folder: default 'filestore'>]");
    }

    private final int udpPort;
    private final int tcpPort;
    private final String mibPath;
    private final String filestorePath;

    private ICfdpEntity entity;

    public CfdpRunner(int udpPort, int tcpPort, String mibPath, String filestorePath)  {
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.mibPath = mibPath;
        this.filestorePath = filestorePath;
    }

    private void start() throws IOException, UtLayerException {
        Mib conf1File = Mib.load(new FileInputStream(this.mibPath));
        File fs1Folder = new File(this.filestorePath);
        if(!fs1Folder.exists()) {
            fs1Folder.mkdirs();
        }
        FilesystemBasedFilestore fs1 = new FilesystemBasedFilestore(fs1Folder);
        TcpLayer tcpLayer = new TcpLayer(conf1File, tcpPort);
        tcpLayer.activate();
        UdpLayer udpLayer = new UdpLayer(conf1File, udpPort);
        udpLayer.activate();
        this.entity = ICfdpEntity.create(conf1File, fs1, tcpLayer, udpLayer);
        this.entity.register(this);
        // RX/TX availability
        for(RemoteEntityConfigurationInformation r : conf1File.getRemoteEntities()) {
            tcpLayer.setRxAvailability(true, r.getRemoteEntityId());
            tcpLayer.setTxAvailability(true, r.getRemoteEntityId());
            udpLayer.setRxAvailability(true, r.getRemoteEntityId());
            udpLayer.setTxAvailability(true, r.getRemoteEntityId());
        }
        // Active polling on reading from console
        System.out.println("CFDP entity " + conf1File.getLocalEntity().getLocalEntityId() + " - Type 'help' for the list of commands");       
        System.out.println("-----------------------------------------------------------------------");       
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        boolean exit = false;
        while(!exit) {
            String read = br.readLine();
            exit = processInput(read);
        }
        this.entity.dispose();
    }

    private boolean processInput(String read) {
        try {
            if(read.isBlank()) {
                return false;
            } else if (read.equals("exit")) {
                return true;
            } else if (read.startsWith("put")) {
                String[] split = read.split(" ", -1);
                String file = split[1];
                int destEntityId = Integer.parseInt(split[2]);
                String ackMode = split.length == 4 ? split[3] : null;
                PutRequest pr = new PutRequest(destEntityId, file, file, false, null,
                        ackMode == null ? null : ackMode.equals("ACK"),
                        ackMode != null ? ackMode.contains("CLOSURE") : null,
                        null, null, null);
                this.entity.request(pr);
            } else if (read.equals("list")) {
                Set<Long> transactionIds = entity.getTransactionIds();
                for (Long transactionId : transactionIds) {
                    entity.request(new ReportRequest(transactionId));
                }
            } else if (read.startsWith("suspend")) {
                String[] split = read.split(" ", -1);
                int transactionId = Integer.parseInt(split[1]);
                entity.request(new SuspendRequest(transactionId));
            } else if (read.startsWith("resume")) {
                String[] split = read.split(" ", -1);
                int transactionId = Integer.parseInt(split[1]);
                entity.request(new ResumeRequest(transactionId));
            } else if (read.startsWith("cancel")) {
                String[] split = read.split(" ", -1);
                int transactionId = Integer.parseInt(split[1]);
                entity.request(new CancelRequest(transactionId));
            } else if (read.startsWith("prompt")) {
                String[] split = read.split(" ", -1);
                int transactionId = Integer.parseInt(split[1]);
                entity.request(new PromptNakRequest(transactionId));
            } else if (read.startsWith("keepalive")) {
                String[] split = read.split(" ", -1);
                int transactionId = Integer.parseInt(split[1]);
                entity.request(new KeepAliveRequest(transactionId));
            } else if (read.equals("help")) {
                System.out.println("put        <file path> <dest. entity ID> [ACK|UNACK|UNACK_CLOSURE]"); 
                System.out.println("list"); 
                System.out.println("suspend    <transaction ID>"); 
                System.out.println("resume     <transaction ID>"); 
                System.out.println("cancel     <transaction ID>"); 
                System.out.println("prompt     <transaction ID>"); 
                System.out.println("keepalive  <transaction ID>"); 
                System.out.println("help"); 
                System.out.println("exit"); 
            } else {
                System.out.println("Command not recognized. Type 'help' for the list of commands."); 
            }
            return false;
        } catch (Exception e) {
            System.out.println("Exception caught when running command " + read + ". Check logs for additional information."); 
            e.printStackTrace(); 
            return false;
        }
    }

    @Override
    public void indication(ICfdpEntity emitter, ICfdpIndication indication) {
        if(indication instanceof ReportIndication) {
            System.out.printf("TR:[%04d] RE:[%05d] PR: [%3d%%] STATE:[%s]%n",
                    ((ReportIndication) indication).getTransactionId(),
                    ((ReportIndication) indication).getStatusReport().getDestinationEntityId(),
                    computeProgress((ReportIndication) indication),
                    ((ReportIndication) indication).getStatusReport().getCfdpTransactionState());
        } else {
            System.out.println(indication);
        }
    }

    private int computeProgress(ReportIndication indication) {
        return (int) Math.floor(((double) indication.getStatusReport().getProgress() / (double) indication.getStatusReport().getTotalFileSize()) * 100.0);
    }
}
