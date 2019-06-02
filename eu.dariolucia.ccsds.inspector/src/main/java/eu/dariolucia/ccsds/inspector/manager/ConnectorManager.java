/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.inspector.manager;

import eu.dariolucia.ccsds.inspector.api.*;
import eu.dariolucia.ccsds.inspector.view.charts.BitrateSample;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ConnectorManager implements IConnectorObserver {

    public static final String ANNOTATION_SCID_KEY = "Spacecraft ID";
    public static final String ANNOTATION_VCID_KEY = "Virtual Channel ID";

    public static final String CONFIGURATION_EXTRACT_PACKET_KEY = "extractPacketId";

    private final String name;
    // For the construction of the connector
    private final IConnectorFactory factory;
    private final ConnectorConfiguration configuration;

    // For notifications
    private final ExecutorService executor;
    private final List<IConnectorManagerObserver> observers = new CopyOnWriteArrayList<>();

    // The connector managed by this manager
    private IConnector connector;

    // For state derivation
    private volatile ConnectorState currentConnectorState = ConnectorState.IDLE;
    private volatile ConnectorManagerState currentState = ConnectorManagerState.IDLE;
    private volatile boolean flowing = false;

    // For rate sampling
    private volatile Date lastSamplingTime = null;
    private volatile long tempInUnits = 0;

    private volatile boolean disposed = false;

    private final SpacePacketExtractor packetExtractor;

    // To export data
    private volatile boolean exportData;
    private final long exportTime;

    private PrintStream aosFramesFile = null;
    private PrintStream tmFramesFile = null;
    private PrintStream tcFramesFile = null;
    private PrintStream spacePacketFile = null;

    public ConnectorManager(String name, IConnectorFactory factory, ConnectorConfiguration configuration) {
        this.name = name;
        this.factory = factory;
        this.configuration = configuration;
        this.executor = Executors.newSingleThreadExecutor((r) -> {
            Thread t = new Thread(r);
            t.setName(factory.getName() + " Manager Thread");
            t.setDaemon(true);
            return t;
        });
        if(configuration.isPropertySet(CONFIGURATION_EXTRACT_PACKET_KEY)) {
            Boolean b = configuration.getBooleanProperty(CONFIGURATION_EXTRACT_PACKET_KEY);
            if(b != null && b) {
                this.packetExtractor = new SpacePacketExtractor(this::notifyPacket, this::notifyInfo);
            }
            else {
                this.packetExtractor = null;
            }
        } else {
            this.packetExtractor = null;
        }
        this.exportTime = System.currentTimeMillis();
    }

    public void register(IConnectorManagerObserver obs) {
        this.observers.add(obs);
    }

    public void deregister(IConnectorManagerObserver obs) {
        this.observers.remove(obs);
    }

    public void initialise() {
        if (disposed) {
            throw new IllegalStateException("Connector manager disposed: " + getConnectorFactory().getName());
        }
        this.connector = this.factory.createConnector(this, this.configuration);
    }

    public void start() {
        if (disposed) {
            throw new IllegalStateException("Connector manager disposed: " + getConnectorFactory().getName());
        }
        this.connector.start();
    }

    public void stop() {
        if (disposed) {
            throw new IllegalStateException("Connector manager disposed: " + getConnectorFactory().getName());
        }
        this.connector.stop();
    }

    public void dispose() {
        if (disposed) {
            throw new IllegalStateException("Connector manager disposed: " + getConnectorFactory().getName());
        }
        this.connector.dispose();
        if(this.packetExtractor != null) {
            this.packetExtractor.dispose();
        }
        this.disposed = true;
        this.executor.submit(() -> this.observers.forEach(o -> o.disposedReported(this)));
        this.connector = null;
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted();
            e.printStackTrace();
        }
        this.observers.clear();
        //
        if(tmFramesFile != null) {
            tmFramesFile.close();
        }
        if(aosFramesFile != null) {
            aosFramesFile.close();
        }
        if(tcFramesFile != null) {
            tcFramesFile.close();
        }
        if(spacePacketFile != null) {
            spacePacketFile.close();
        }
    }

    @Override
    public void errorReported(IConnector connector, Exception e) {
        if (disposed) {
            return;
        }
        final Instant when = Instant.now();
        this.executor.submit(() -> this.observers.forEach(o -> o.errorReported(this, when, e)));
    }

    @Override
    public void dataReported(IConnector connector, AnnotatedObject obj) {
        if (disposed) {
            return;
        }
        addReceivedBits(obj.getLength() * 8);
        if(!obj.isAnnotationPresent(IConnector.ANNOTATION_TIME_KEY)) {
            obj.setAnnotationValue(IConnector.ANNOTATION_TIME_KEY, Instant.now());
        }
        this.executor.submit(() -> this.observers.forEach(o -> o.dataReported(this, obj)));
        if(exportData) {
            exportToFile(obj);
        }
        // Extract packets if configured to do so
        if(this.packetExtractor != null && obj instanceof AbstractTransferFrame) {
            this.packetExtractor.process((AbstractTransferFrame) obj);
        }
    }

    private void notifyPacket(SpacePacket packet) {
        this.executor.submit(() -> this.observers.forEach(o -> o.dataReported(this, packet)));
        if(exportData) {
            exportToFile(packet);
        }
    }

    private void exportToFile(AnnotatedObject data) {
        try {
            if (data instanceof TmTransferFrame) {
                byte[] bytes = ((TmTransferFrame) data).getFrame();
                if (tmFramesFile == null) {
                    tmFramesFile = createStream("TM");
                }
                if (tmFramesFile != null) {
                    tmFramesFile.println(StringUtil.toHexDump(bytes));
                }
            } else if (data instanceof AosTransferFrame) {
                byte[] bytes = ((AosTransferFrame) data).getFrame();
                if (aosFramesFile == null) {
                    aosFramesFile = createStream("AOS");
                }
                if (aosFramesFile != null) {
                    aosFramesFile.println(StringUtil.toHexDump(bytes));
                }
            } else if (data instanceof TcTransferFrame) {
                byte[] bytes = ((TcTransferFrame) data).getFrame();
                if (tcFramesFile == null) {
                    tcFramesFile = createStream("TC");
                }
                if (tcFramesFile != null) {
                    tcFramesFile.println(StringUtil.toHexDump(bytes));
                }
            } else if (data instanceof SpacePacket) {
                byte[] bytes = ((SpacePacket) data).getPacket();
                if (spacePacketFile == null) {
                    spacePacketFile = createStream("SPP");
                }
                if (spacePacketFile != null) {
                    spacePacketFile.println(StringUtil.toHexDump(bytes));
                }
            }
        } catch (Exception e) {
            notifyInfo(SeverityEnum.ALARM, "Cannot record data to dump file: " + e.getMessage());
        }
    }

    private PrintStream createStream(String prefix) {
        File toExport = new File(System.getProperty("user.home")
                + File.separator
                + prefix + "-" + getName().replace(' ', '_')
                + "_" + exportTime + ".hexdump");
        try {
            if (!toExport.exists()) {
                if(!toExport.createNewFile()) {
                    throw new IOException("file creation failed");
                }
            }
            return new PrintStream(new FileOutputStream(toExport, true));
        } catch (Exception e) {
            notifyInfo(SeverityEnum.ALARM, "Cannot create dump file at " + toExport.getAbsolutePath() + (e.getMessage() != null ? ": " + e.getMessage() : e.getClass().getSimpleName()));
            return null;
        }
    }

    @Override
    public void stateReported(IConnector connector, ConnectorState state) {
        if (disposed) {
            return;
        }
        this.currentConnectorState = state;
        deriveState();
    }


    @Override
    public void infoReported(IConnector connector, SeverityEnum severity, String message) {
        if (disposed) {
            return;
        }
        notifyInfo(severity, message);
    }

    private void notifyInfo(SeverityEnum severity, String message) {
        final Instant when = Instant.now();
        this.executor.submit(() -> this.observers.forEach(o -> o.infoReported(this, when, severity, message)));
    }

    private synchronized void deriveState() {
        ConnectorManagerState newState;
        switch (this.currentConnectorState) {
            case IDLE:
                newState = ConnectorManagerState.IDLE;
                break;
            case ERROR:
                newState = ConnectorManagerState.ERROR;
                break;
            case STARTING:
                newState = ConnectorManagerState.STARTING;
                break;
            case STOPPING:
                newState = ConnectorManagerState.STOPPING;
                break;
            case RUNNING:
                newState = this.flowing ? ConnectorManagerState.RUNNING : ConnectorManagerState.NO_FLOW;
                break;
            default:
                throw new IllegalStateException("Cannot derive state from " + this.currentConnectorState);
        }
        setCurrentState(newState);
    }

    private void setCurrentState(ConnectorManagerState newState) {
        if (this.currentState != newState) {
            this.currentState = newState;
            notifyState(this.currentState);
        }
    }

    private void notifyState(final ConnectorManagerState currentState) {
        if (disposed) {
            return;
        }
        this.executor.submit(() -> this.observers.forEach(o -> o.stateReported(this, currentState)));
    }

    private synchronized void setFlowDetected(boolean flow) {
        if (disposed) {
            return;
        }
        this.flowing = flow;
        deriveState();
    }

    private synchronized void addReceivedBits(long bits) {
        if (disposed) {
            return;
        }
        this.tempInUnits += bits;
    }

    public BitrateSample computeCurrentRate() {
        if (disposed) {
            throw new IllegalStateException("Connector manager disposed: " + getConnectorFactory().getName());
        }
        Date now = new Date();
        long inRate = 0;
        synchronized (this) {
            if (this.lastSamplingTime == null) {
                this.lastSamplingTime = now;
                this.tempInUnits = 0;
                setFlowDetected(false);
            } else {
                double intervalSecs = (now.getTime() - this.lastSamplingTime.getTime()) / 1000.0;
                inRate = (long) (this.tempInUnits / intervalSecs);
                this.tempInUnits = 0;
                this.lastSamplingTime = now;
                setFlowDetected(inRate > 0);
            }
        }
        return new BitrateSample(Instant.ofEpochMilli(now.getTime()), inRate);
    }

    public IConnectorFactory getConnectorFactory() {
        return factory;
    }

    public ConnectorConfiguration getConfiguration() {
        return configuration;
    }

    public ConnectorManagerState getState() {
        return this.currentState;
    }

    public String getName() {
        return name;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

	public void setStorageEnabled(boolean b) {
        this.exportData = b;
	}
}
