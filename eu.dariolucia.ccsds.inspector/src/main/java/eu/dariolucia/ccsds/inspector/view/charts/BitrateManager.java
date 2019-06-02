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

package eu.dariolucia.ccsds.inspector.view.charts;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import eu.dariolucia.ccsds.inspector.manager.ConnectorManager;
import javafx.application.Platform;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;

public class BitrateManager {

    private static final Timer SCHEDULER = new Timer(true);
    private static final Set<BitrateManager> REGISTRY = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        SCHEDULER.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    for (BitrateManager drm : REGISTRY) {
                        drm.computeSample();
                    }
                    final Set<BitrateManager> copyRegistry = new HashSet<>(REGISTRY);
                    if (!copyRegistry.isEmpty()) {
                        Platform.runLater(() -> {
                            Instant now = Instant.now();
                            Instant start = now.minusSeconds(60);
                            for (BitrateManager drm : copyRegistry) {
                                drm.updateChart(start, now);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 2000);
    }

    private ConnectorManager manager;

    private AreaChart<Instant, Number> rxChart;

    private volatile BitrateSample lastSample;

    private XYChart.Series<Instant, Number> series;

    public BitrateManager(ConnectorManager manager, AreaChart<Instant, Number> rxChart) {
        this.manager = manager;

        rxChart.setAnimated(false);
        rxChart.getXAxis().setTickLabelsVisible(true);

        rxChart.getXAxis().setAutoRanging(false);
        ((InstantAxis) rxChart.getXAxis()).setLowerBound(Instant.now().minusSeconds(60));
        ((InstantAxis) rxChart.getXAxis()).setUpperBound(Instant.now());

        this.rxChart = rxChart;

        this.series = new XYChart.Series<>();
        this.series.setName("");

        this.rxChart.getData().add(this.series);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void updateChart(Instant min, Instant max) {
        if(!REGISTRY.contains(this)) {
            // Bitrate to be shut down
            return;
        }

        ((InstantAxis) this.rxChart.getXAxis()).setLowerBound(min);
        ((InstantAxis) this.rxChart.getXAxis()).setUpperBound(max);

        if (this.lastSample == null) {
            return;
        }

        XYChart.Data in1 = new XYChart.Data(lastSample.getTime(), lastSample.getBps());
        this.series.getData().add(in1);
        if (this.series.getData().size() > 70) {
            this.series.getData().remove(0);
        }
        // Tooltip.install(in1.getNode(), new Tooltip(String.valueOf(in1.getYValue())));
    }

    protected void computeSample() {
        try {
            if(!this.manager.isDisposed()) {
                this.lastSample = this.manager.computeCurrentRate();
            } else {
                deactivate();
            }
        } catch(IllegalArgumentException e) {
            deactivate();
        }
    }

    public void activate() {
        REGISTRY.add(this);
    }

    public void deactivate() {
        REGISTRY.remove(this);
    }
}
