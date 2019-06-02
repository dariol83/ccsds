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

import eu.dariolucia.ccsds.inspector.connectors.file.AosFileConnectorFactory;
import eu.dariolucia.ccsds.inspector.connectors.file.TcFileConnectorFactory;
import eu.dariolucia.ccsds.inspector.connectors.file.TmFileConnectorFactory;
import eu.dariolucia.ccsds.inspector.connectors.testing.TmTestConnectorFactory;

module eu.dariolucia.ccsds.inspector {
    uses eu.dariolucia.ccsds.inspector.api.IConnectorFactory;

    exports eu.dariolucia.ccsds.inspector.application to javafx.graphics;
    exports eu.dariolucia.ccsds.inspector.view.controller to javafx.fxml;
    exports eu.dariolucia.ccsds.inspector.view.charts to javafx.fxml;

    opens eu.dariolucia.ccsds.inspector.view.controller to javafx.fxml;

    requires eu.dariolucia.ccsds.tmtc;
    requires javafx.graphics;
    requires javafx.controls;
    requires java.logging;
    requires javafx.fxml;

    provides eu.dariolucia.ccsds.inspector.api.IConnectorFactory with
            TmTestConnectorFactory,
            TmFileConnectorFactory,
            AosFileConnectorFactory,
            TcFileConnectorFactory;
}