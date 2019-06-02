/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.sle.utlfx.manager;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.fx.charts.InstantAxis;
import eu.dariolucia.ccsds.sle.utl.si.RateSample;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import javafx.application.Platform;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

public class DataRateManager {

	private static final Logger LOG = Logger.getLogger(DataRateManager.class.getName());
	
	private static final Timer SCHEDULER = new Timer(true);
	private static final Set<DataRateManager> REGISTRY = Collections.newSetFromMap(new ConcurrentHashMap<DataRateManager, Boolean>());
	
	private static final double ONE_MB = 1024 * 1024;
	private static final double ONE_KB = 1024;
	
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.00");
	
	static {
		SCHEDULER.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					for(DataRateManager drm : REGISTRY) {
						drm.computeSample();
					}
					final Set<DataRateManager> copyRegistry = new HashSet<>(REGISTRY);
					if(!copyRegistry.isEmpty()) {
						Platform.runLater(() -> {
							Instant now = Instant.now();
							Instant start = now.minusSeconds(60);
							for(DataRateManager drm : copyRegistry) {
								drm.updateChart(start, now);
							}
						});
					}
				} catch(Exception e) {
					LOG.log(Level.SEVERE, "Error while computing statistical I/O samples", e);
				}
			}
		}, 0, 1000);
	}
	
	private ServiceInstance serviceInstance;
	
	private AreaChart<Instant, Number> mbitSecChart;
	
	private AreaChart<Instant, Number> pduSecChart;
	
	private RateSample lastSample;
	
	private Label txBitrateLabel;
	
	private Label rxBitrateLabel;
	
	private Label txDataLabel;
	
	private Label rxDataLabel;
	
	private XYChart.Series<Instant, Number> inPduSeries;
	private XYChart.Series<Instant, Number> outPduSeries;
	private XYChart.Series<Instant, Number> inBytesSeries;
	private XYChart.Series<Instant, Number> outBytesSeries;
	
	public DataRateManager(ServiceInstance serviceInstance, AreaChart<Instant, Number> mbitSecChart,
			AreaChart<Instant, Number> pduSecChart, Label txBitrateLabel, Label rxBitrateLabel, Label txDataLabel, Label rxDataLabel) {
		this.serviceInstance = serviceInstance;
		
		mbitSecChart.setAnimated(false);
		mbitSecChart.getXAxis().setTickLabelsVisible(true);

		((InstantAxis) mbitSecChart.getXAxis()).setAutoRanging(false);
		((InstantAxis) mbitSecChart.getXAxis()).setLowerBound(Instant.now().minusSeconds(60));
		((InstantAxis) mbitSecChart.getXAxis()).setUpperBound(Instant.now());
		
		this.mbitSecChart = mbitSecChart;
		
		pduSecChart.setAnimated(false);
		pduSecChart.getXAxis().setTickLabelsVisible(true);

		((InstantAxis) pduSecChart.getXAxis()).setAutoRanging(false);
		((InstantAxis) pduSecChart.getXAxis()).setLowerBound(Instant.now().minusSeconds(60));
		((InstantAxis) pduSecChart.getXAxis()).setUpperBound(Instant.now());
		
		this.pduSecChart = pduSecChart;
		
		this.txBitrateLabel = txBitrateLabel;
		this.txDataLabel = txDataLabel;
		this.rxBitrateLabel = rxBitrateLabel;
		this.rxDataLabel = rxDataLabel;
		
		this.inPduSeries = new XYChart.Series<>();
		this.inPduSeries.setName("PDU/s In");
		
		this.outPduSeries = new XYChart.Series<>();
		this.outPduSeries.setName("PDU/s Out");
		
		this.inBytesSeries = new XYChart.Series<>();
		this.inBytesSeries.setName("Mbps In");
		
		this.outBytesSeries = new XYChart.Series<>();
		this.outBytesSeries.setName("Mbps Out");
		
		this.mbitSecChart.getData().add(this.inBytesSeries);
		this.mbitSecChart.getData().add(this.outBytesSeries);
		
		this.pduSecChart.getData().add(this.inPduSeries);
		this.pduSecChart.getData().add(this.outPduSeries);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void updateChart(Instant min, Instant max) {
		((InstantAxis) this.mbitSecChart.getXAxis()).setLowerBound(min);
		((InstantAxis) this.mbitSecChart.getXAxis()).setUpperBound(max);
		((InstantAxis) this.pduSecChart.getXAxis()).setLowerBound(min);
		((InstantAxis) this.pduSecChart.getXAxis()).setUpperBound(max);
		
		if(this.lastSample == null || this.lastSample.getByteSample() == null || lastSample.getPduSample() == null) {
			return;
		}
		
		XYChart.Data in1 = new XYChart.Data(lastSample.getInstant(), (lastSample.getByteSample().getInRate() / ONE_MB) * 8);
		this.inBytesSeries.getData().add(in1);
		if(this.inBytesSeries.getData().size() > 70) {
			this.inBytesSeries.getData().remove(0);
		}
		// Tooltip.install(in1.getNode(), new Tooltip(String.valueOf(in1.getYValue())));
		
		XYChart.Data out1 = new XYChart.Data(lastSample.getInstant(), (lastSample.getByteSample().getOutRate() / ONE_MB) * 8);
		this.outBytesSeries.getData().add(out1);
		if(this.outBytesSeries.getData().size() > 70) {
			this.outBytesSeries.getData().remove(0);
		}
		// Tooltip.install(out1.getNode(), new Tooltip(String.valueOf(out1.getYValue())));
				
		XYChart.Data in2 = new XYChart.Data(lastSample.getInstant(), lastSample.getPduSample().getInRate());
		this.inPduSeries.getData().add(in2);
		if(this.inPduSeries.getData().size() > 70) {
			this.inPduSeries.getData().remove(0);
		}
		// Tooltip.install(in2.getNode(), new Tooltip(String.valueOf(in2.getYValue())));
		
		XYChart.Data out2 = new XYChart.Data(lastSample.getInstant(), lastSample.getPduSample().getOutRate());
		this.outPduSeries.getData().add(out2);
		if(this.outPduSeries.getData().size() > 70) {
			this.outPduSeries.getData().remove(0);
		}
		// Tooltip.install(out2.getNode(), new Tooltip(String.valueOf(out2.getYValue())));

		// Use last sample to update current values
		this.txBitrateLabel.setText(buildBitrateString(this.lastSample.getByteSample().getOutRate()));
		this.rxBitrateLabel.setText(buildBitrateString(this.lastSample.getByteSample().getInRate()));
		this.txDataLabel.setText(buildDataString(this.lastSample.getByteSample().getTotalOutUnits()));
		this.rxDataLabel.setText(buildDataString(this.lastSample.getByteSample().getTotalInUnits()));

		// Force redraw
		mbitSecChart.getParent().layout();

	}

	private String buildDataString(long bytes) {
		if(bytes > ONE_MB) {
			// Use Mbps
			return DECIMAL_FORMAT.format((bytes / ONE_MB))  + " MB";
		} else if(bytes > ONE_KB) {
			// Use Kbps
			return DECIMAL_FORMAT.format((bytes / ONE_KB))  + " KB";
		} else {
			// Use bps
			return Long.toString(bytes) + " B";
		}
	}

	private String buildBitrateString(double bytespersecond) {
		if(bytespersecond > ONE_MB) {
			// Use Mbps
			return DECIMAL_FORMAT.format((bytespersecond / ONE_MB) * 8)  + " Mbps";
		} else if(bytespersecond > ONE_KB) {
			// Use Kbps
			return DECIMAL_FORMAT.format((bytespersecond / ONE_KB) * 8)  + " Kbps";
		} else {
			// Use bps
			return DECIMAL_FORMAT.format(bytespersecond * 8) + " bps";
		}
	}

	protected void computeSample() {
		this.lastSample = this.serviceInstance.getCurrentRate();
	}

	public void activate() {
		REGISTRY.add(this);
	}
	
	public void deactivate() {
		REGISTRY.remove(this);
	}

}
