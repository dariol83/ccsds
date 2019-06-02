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

import com.beanit.jasn1.ber.types.BerOctetString;
import eu.dariolucia.ccsds.sle.utlfx.application.ApplicationConfiguration;
import eu.dariolucia.ccsds.sle.utlfx.manager.RawDataViewerManager.RawDataEntry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;

import java.util.concurrent.atomic.AtomicInteger;

public class PduInspectorManager {

	private Label pduDescriptionText;
	
	private TableView<SlePdu> pduTableView;

	private TreeTableView<SlePduAttribute> pduDetailsTreeTableView;

	private RawDataViewerManager rawDataViewerManager;

	private final AtomicInteger maxPdusToShow = new AtomicInteger(ApplicationConfiguration.instance().getMaxPdus());
	
	private final Callback<ApplicationConfiguration, Void> updateConfigurationCallback = new Callback<ApplicationConfiguration, Void>() {
		@Override
		public Void call(ApplicationConfiguration param) {
			maxPdusToShow.set(param.getMaxPdus());
			return null;
		}
	};
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PduInspectorManager(TableView<SlePdu> pduTableView, TreeTableView<SlePduAttribute> pduDetailsTreeTableView,
			Label pduDescriptionText, Label rawDataDetailsDescriptionText, TableView<RawDataEntry> rawDataDetailsTableView) {
		this.pduTableView = pduTableView;
		this.pduDescriptionText = pduDescriptionText;
		this.pduDetailsTreeTableView = pduDetailsTreeTableView;
		this.rawDataViewerManager = new RawDataViewerManager(rawDataDetailsDescriptionText, rawDataDetailsTableView);

		//
		EventHandler<? super MouseEvent> pduDblClickHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() == 2) {
					pduSelected(((TableRow<SlePdu>)event.getSource()).getItem());
				}
			}
		};
		EventHandler<? super KeyEvent> pduReturnKeyHandler = new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.ENTER) {
					pduSelected(((TableView<SlePdu>)event.getSource()).getSelectionModel().getSelectedItem());
				}
			}
		};
		this.pduTableView.setRowFactory(pdu -> {
			TableRow<SlePdu> r = new TableRow<>();
			r.setOnMouseClicked(pduDblClickHandler);
			return r;
		});
		this.pduTableView.setOnKeyReleased(pduReturnKeyHandler);
		//
		EventHandler<? super MouseEvent> pduDetailsDblClickHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() == 2) {
					processSlePduAttributeDoubleClick(((TreeTableRow<SlePduAttribute>)event.getSource()).getItem());
				}
			}
		};
		this.pduDetailsTreeTableView.setRowFactory(tv -> {
			TreeTableRow<SlePduAttribute> r = new TreeTableRow<>();
			r.setOnMouseClicked(pduDetailsDblClickHandler);
			return r;
		});

		((TableColumn<SlePdu, String>) pduTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getTime().toString()));
		((TableColumn<SlePdu, String>) pduTableView.getColumns().get(1))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getDirection()));
		((TableColumn<SlePdu, String>) pduTableView.getColumns().get(2))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().isError() ? "X" : ""));
		((TableColumn<SlePdu, String>) pduTableView.getColumns().get(3))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getType()));
		((TableColumn<SlePdu, String>) pduTableView.getColumns().get(4))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getDetails()));

		((TreeTableColumn<SlePduAttribute, String>) pduDetailsTreeTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getValue().getName()));
		((TreeTableColumn<SlePduAttribute, String>) pduDetailsTreeTableView.getColumns().get(1))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getValue().getType()));
		((TreeTableColumn<SlePduAttribute, String>) pduDetailsTreeTableView.getColumns().get(2))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getValue().getValueAsString()));
		
		// Fix column size and autoresize
		pduDetailsTreeTableView.getColumns().get(0).prefWidthProperty().bind(pduDetailsTreeTableView.widthProperty().divide(3)); 
		pduDetailsTreeTableView.getColumns().get(1).prefWidthProperty().bind(pduDetailsTreeTableView.widthProperty().divide(3));
		pduDetailsTreeTableView.getColumns().get(2).prefWidthProperty().bind(pduDetailsTreeTableView.widthProperty().divide(3).subtract(24));
		
		ApplicationConfiguration.instance().register(this.updateConfigurationCallback);
	}
	
	private void processSlePduAttributeDoubleClick(SlePduAttribute item) {
		if(item.getValue() instanceof BerOctetString) {
			this.rawDataViewerManager.setData(item.getName() + " " + item.getType(), ((BerOctetString)item.getValue()).value);
		} else if(item.getValue() instanceof byte[]) {
			this.rawDataViewerManager.setData(item.getName() + " " + item.getType(), ((byte[])item.getValue()));
		}
	}

	protected void pduSelected(SlePdu newValue) {
		if (newValue == null) {
			this.pduDescriptionText.setText("");
			this.rawDataViewerManager.setData("", new byte[0]);
			this.pduDetailsTreeTableView.setRoot(null);
		} else {
			this.pduDescriptionText.setText(newValue.getType() + " @ " + newValue.getTime().toString());
			this.rawDataViewerManager.setData(newValue.getType() + " @ " + newValue.getTime().toString(),
					newValue.getData());
			this.pduDetailsTreeTableView.setRoot(newValue.buildTreeItem());
		}
	}

	public void addPdu(SlePdu slePdu) {
		int toBeRemoved = this.pduTableView.getItems().size() - this.maxPdusToShow.get();
		if (toBeRemoved > 0) {
			this.pduTableView.getItems().remove(0, toBeRemoved);
		}
		this.pduTableView.getItems().add(slePdu);
	}

	public void clear() {
		this.pduTableView.getItems().removeAll(this.pduTableView.getItems());
		this.rawDataViewerManager.setData("", new byte[0]);
		this.pduDetailsTreeTableView.setRoot(null);
	}
	
	public void deactivate() {
		clear();
		ApplicationConfiguration.instance().deregister(this.updateConfigurationCallback);
	}

}
