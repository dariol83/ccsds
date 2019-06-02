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

package eu.dariolucia.ccsds.inspector.view.util;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.function.Function;

public class FancyListCell<T> extends ListCell<T> {

    private HBox content;
    private Text title;
    private Text update;
    private ImageView imageView = new ImageView();

    private Function<T, String> titleProvider;
    private Function<T, String> descriptionProvider;
    private Function<T, Image> imageProvider;

    /**
     *
     */
    public FancyListCell(Function<T, String> titleProvider, Function<T, String> descriptionProvider, Function<T, Image> imageProvider) {
        super();
        this.title = new Text();
        this.title.setSmooth(true);
        this.title.setFont(Font.font("System", FontWeight.BOLD, 12));
        this.update = new Text();
        this.update.setSmooth(true);
        VBox vBox = new VBox(this.title, this.update);
        this.content = new HBox(this.imageView, vBox);
        this.content.setSpacing(10);

        this.titleProvider = titleProvider;
        this.descriptionProvider = descriptionProvider;
        this.imageProvider = imageProvider;

        setTooltip(null);
    }

    /*
     * (non-Javadoc)
     *
     * @see javafx.scene.control.Cell#updateItem(java.lang.Object, boolean)
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || (item == null)) {
            setGraphic(null);
            setTooltip(null);
        } else {
            this.imageView.setImage(this.imageProvider.apply(item));
            this.title.setText(this.titleProvider.apply(item));
            this.update.setText(this.descriptionProvider.apply(item));
            this.update.setWrappingWidth(this.getListView().getWidth() - this.content.getSpacing() * 3 - this.imageView.getImage().getWidth());
            setGraphic(this.content);
            if(getTooltip() == null) {
                setTooltip(new Tooltip());
            }
            getTooltip().setText(this.descriptionProvider.apply(item));
        }
    }
}
