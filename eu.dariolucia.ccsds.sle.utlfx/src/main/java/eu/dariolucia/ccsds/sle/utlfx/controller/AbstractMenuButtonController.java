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

package eu.dariolucia.ccsds.sle.utlfx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public abstract class AbstractMenuButtonController<T extends AbstractDisplayController> {

	private T controller;
	
	public void setController(T controller) {
		this.controller = controller;
	}
	
	protected T getController() {
		return this.controller;
	}
	
	@FXML
	protected void waitForBindMenuItemSelected(ActionEvent e) {
		this.controller.waitForBindMenuItemSelected(e);
	}
	
	@FXML
	protected void bindMenuItemSelected(ActionEvent e) {
		this.controller.bindMenuItemSelected(e);
	}

	@FXML
	protected void unbindMenuItemSelected(ActionEvent e) {
		this.controller.unbindMenuItemSelected(e);
	}

	@FXML
	protected void peerAbortMenuItemSelected(ActionEvent e) {
		this.controller.peerAbortMenuItemSelected(e);
	}
	
}
