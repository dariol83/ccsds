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

package eu.dariolucia.ccsds.sle.utlfx.util;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LogUtil {

    public static void saveLogsToFile(Logger logger, Window window, TableView<LogRecord> table) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save logs to...");
        File f = fc.showSaveDialog(window);
        if (f != null) {
            PrintStream ps = null;
            try {
                if (!f.exists()) {
                    f.createNewFile();
                }
                ps = new PrintStream(f);
                for (LogRecord lr : table.getItems()) {
                    ps.println(new Date(lr.getMillis()).toString() + "\t" + lr.getLevel().getName() + "\t"
                            + lr.getMessage());
                }
                ps.flush();
                logger.log(Level.INFO, "Logs exported to " + f.getAbsolutePath() + " successfully");
                DialogUtils.showInfo("File saved", "Logs successfully saved to " + f.getAbsolutePath());
            } catch (IOException e1) {
                logger.log(Level.SEVERE, "Error while saving logs to " + f.getAbsolutePath(), e1);
                DialogUtils.showError("Cannot save file to " + f.getAbsolutePath(), "Error while saving logs to "
                        + f.getAbsolutePath() + ", check the related log entry for the detailed error");
            } finally {
                if(ps != null) {
                    ps.close();
                }
            }
        }
    }
}
