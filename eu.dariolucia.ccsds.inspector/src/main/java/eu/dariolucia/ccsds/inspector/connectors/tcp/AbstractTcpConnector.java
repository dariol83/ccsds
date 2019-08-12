/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.inspector.connectors.tcp;

import eu.dariolucia.ccsds.inspector.api.*;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.time.Instant;

public abstract class AbstractTcpConnector extends AbstractConnector {

    public static final String FECF_PRESENT_ID = "fecf";
    public static final String HOST_ID = "host";
    public static final String PORT_ID = "port";

    private final String host;
    private final Integer port;

    private volatile boolean running;
    private volatile Thread worker;

    private volatile Socket sock;

    // Readible by subclasses
    protected final boolean fecfPresent;

    public AbstractTcpConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
        super(name, description, version, configuration, observer);
        this.fecfPresent = configuration.getBooleanProperty(AbstractTcpConnector.FECF_PRESENT_ID);
        this.host = configuration.getStringProperty(HOST_ID);
        this.port = configuration.getIntProperty(PORT_ID);
    }

    @Override
    protected void doStart() {
        if (running) {
            notifyInfo(SeverityEnum.WARNING, "Connector already started");
            return;
        }
        running = true;
        try {
            this.sock = new Socket(this.host, this.port);
            worker = new Thread(this::readFromSocket);
            worker.setDaemon(true);
            worker.start();

            notifyInfo(SeverityEnum.INFO, getName() + " started");
        } catch(Exception e) {
            notifyError(e, true);
            stop();
        }
    }

    protected void readFromSocket() {
        try {
            do {
                // Open the stream
                InputStream is = this.sock.getInputStream();
                // Read the frame
                AnnotatedObject ttf = getData(is);
                ttf.setAnnotationValue(AbstractConnector.ANNOTATION_TIME_KEY, Instant.now());
                notifyData(ttf);
            } while(this.running);
        } catch (IOException e) {
            notifyInfo(SeverityEnum.ALARM, "IO Error reading stream: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            notifyInfo(SeverityEnum.ALARM, "Error reading stream: " + e.getMessage());
        }
    }

    protected abstract AnnotatedObject getData(InputStream is) throws IOException;

    @Override
    protected void doStop() {
        if (!running) {
            return;
        }
        running = false;
        if(this.sock != null) {
            try {
                this.sock.close();
            } catch (IOException e) {
                //
            }
        }
        this.sock = null;
        if (worker != null) {
            try {
                worker.interrupt();
                worker.join(2000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.interrupted();
            }
        }
        worker = null;
        notifyInfo(SeverityEnum.INFO, getName() + " stopped");
    }

    @Override
    protected void doDispose() {
        if(getState() != ConnectorState.IDLE) {
            stop();
        }
    }
}
