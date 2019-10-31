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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import javafx.scene.control.TreeItem;

public class SlePdu {

	private static final Logger LOG = Logger.getLogger(SlePdu.class.getName());
	
	private final Object pdu;
	private final byte[] data;
	private final Date time;
	private final String type;
	private final String details;
	private final String direction;
	private final boolean error;

	private TreeItem<SlePduAttribute> treeItem;

	public SlePdu(Object pdu, byte[] data, String direction, boolean error, String type) {
		this.pdu = pdu;
		this.data = data;
		this.direction = direction;
		this.time = new Date();
		this.error = error;
		this.type = type;
		this.details = FxStringUtil.instance().getPduDetails(pdu);
	}

	public final Object getPdu() {
		return pdu;
	}

	public final byte[] getData() {
		return data;
	}

	public final Date getTime() {
		return time;
	}

	public final String getType() {
		return type;
	}

	public final String getDetails() {
		return details;
	}

	public final String getDirection() {
		return direction;
	}

	public final boolean isError() {
		return error;
	}

	public TreeItem<SlePduAttribute> buildTreeItem() {
		if (this.treeItem == null) {
			this.treeItem = doBuild(this.type, this.pdu);
		}
		return this.treeItem;
	}

	private TreeItem<SlePduAttribute> doBuild(String name, Object o) {
		TreeItem<SlePduAttribute> toReturn = new TreeItem<SlePduAttribute>();
		if(o == null) {
			toReturn.setValue(new SlePduAttribute("NULL", "NULL", null));
			return toReturn;
		}
		String type = o.getClass().getSimpleName();
		Object value = o;
		SlePduAttribute attr = new SlePduAttribute(name, type, value);
		toReturn.setValue(attr);
		// Children
		if (!isFinalElement(o)) {
			// Extract all fields
			List<Field> allFields = getAllFields(o.getClass());
			for (Field f : allFields) {
				if(f.getName().equals("tag")) continue;
				if(f.getName().equals("code")) continue;
				if(f.getName().equals("serialVersionUID")) continue;
				if(f.getName().equals("value")) continue;
				f.setAccessible(true);
				try {
					Object result = f.get(o);
					if(result == null) continue;
					
					if(Collection.class.isAssignableFrom(result.getClass())) {
						Collection<?> childColl = (Collection<?>) result;
						for(Object theObj : childColl) {
							TreeItem<SlePduAttribute> child = doBuild(f.getName(), theObj);
							toReturn.getChildren().add(child);
						}
					} else {
						TreeItem<SlePduAttribute> child = doBuild(f.getName(), result);
						toReturn.getChildren().add(child);
						child.setExpanded(true);
					}
				} catch(Exception e) {
					LOG.log(Level.WARNING, "Error while parsing PDU " + o + " for name " + name, e);
				}
			}
		}
		toReturn.setExpanded(true);
		return toReturn;
	}

	private boolean isFinalElement(Object o) {
		return !o.getClass().isAssignableFrom(Collection.class) && (o.getClass().isArray() || o.getClass().getPackage().getName().startsWith("java.") || o instanceof BerOctetString || o instanceof BerBitString || o instanceof BerBoolean
				|| o instanceof BerEnum || o instanceof BerInteger || o instanceof BerNull
				|| o instanceof BerObjectIdentifier || o instanceof BerReal);
	}

	private List<Field> getAllFields(Class<?> clazz) {
		List<Field> fields = new ArrayList<Field>();
		for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
			fields.addAll(Arrays.asList(c.getDeclaredFields()));
		}
		return fields;
	}
}
