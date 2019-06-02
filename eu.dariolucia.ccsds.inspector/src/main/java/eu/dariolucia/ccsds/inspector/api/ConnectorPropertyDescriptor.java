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

package eu.dariolucia.ccsds.inspector.api;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ConnectorPropertyDescriptor<T> {

	public static final String DATE_FORMAT_PATTERN = "dd-MM-yyyy HH:mm:ss";
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN);

	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public static ConnectorPropertyDescriptor<String> stringDescriptor(String id, String name, String description, String defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, String.class, null, null, null, null, null);
	}

	public static <T extends Enum<T>> ConnectorPropertyDescriptor<T> enumDescriptor(String id, String name, String description, Class<T> enumClass, T defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, enumClass, new ArrayList<>(Arrays.asList(enumClass.getEnumConstants())), null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<Integer> integerDescriptor(String id, String name, String description, Integer defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, Integer.class, null, null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<Boolean> booleanDescriptor(String id, String name, String description, boolean defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, Boolean.class, null, null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<Long> longDescriptor(String id, String name, String description, Long defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, Long.class, null, null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<Double> doubleDescriptor(String id, String name, String description, Double defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, Double.class, null, null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<Date> dateDescriptor(String id, String name, String description, Date defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, Date.class, null, null, null, null, null);
	}

	public static ConnectorPropertyDescriptor<File> fileDescriptor(String id, String name, String description, File defaultValue) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, true, File.class, null, null, null, null, null);
	}

	public static <T> ConnectorPropertyDescriptor<T> descriptor(String id, String name, String description, T defaultValue, boolean mandatory, Class<T> type, List<T> allowedValues, T[] minMaxValues) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, mandatory, type, allowedValues, minMaxValues, null, null, null);
	}

	public static <T> ConnectorPropertyDescriptor<T> descriptor(String id, String name, String description, T defaultValue, boolean mandatory, Class<T> type, List<T> allowedValues, T[] minMaxValues, Function<String, String> validator, Function<T, String> stringConverter, Function<String, T> valueConverter) {
		return new ConnectorPropertyDescriptor<>(id, name, description, defaultValue, mandatory, type, allowedValues, minMaxValues, validator, stringConverter, valueConverter);
	}

	private final String id;

	private final String name;

	private final String description;

	private final T defaultValue;

	private final boolean mandatory;

	private final Class<T> type;

	private final List<T> allowedValues;

	private final T[] minMaxValues;

	private final Function<String, String> validator;

	private final Function<T, String> stringConverter;

	private final Function<String, T> valueConverter;

	private ConnectorPropertyDescriptor(String id, String name, String description, T defaultValue, boolean mandatory, Class<T> type, List<T> allowedValues, T[] minMaxValues,
	                                   Function<String, String> validator, Function<T, String> stringConverter, Function<String, T> valueConverter) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.mandatory = mandatory;
		this.type = type;
		if (allowedValues != null) {
			this.allowedValues = List.copyOf(new ArrayList<>(allowedValues));
		} else {
			this.allowedValues = null;
		}
		if (minMaxValues != null) {
			if(minMaxValues.length != 2) {
				throw new IllegalArgumentException("");
			}
			if (Comparable.class.isAssignableFrom(type)) {
				this.minMaxValues = minMaxValues.clone();
			} else {
				throw new IllegalArgumentException("Type " + type.getName() + " is not a Comparable type, min/max not supported");
			}
		} else {
			this.minMaxValues = null;
		}
		if (validator == null) {
			this.validator = inferValidator(type);
		} else {
			this.validator = validator;
		}
		if (stringConverter == null) {
			this.stringConverter = inferStringConverter(type);
		} else {
			this.stringConverter = stringConverter;
		}
		if (valueConverter == null) {
			this.valueConverter = inferValueConverter(type);
		} else {
			this.valueConverter = valueConverter;
		}
		validateDescriptor();
	}

	private Function<String, T> inferValueConverter(Class<T> type) {
		if (type == null) {
			throw new IllegalArgumentException("Null type");
		}
		if (type.equals(Integer.class)) {
			return (o) -> (T) (Integer.valueOf(o));
		}
		if (type.equals(Long.class)) {
			return (o) -> (T) (Long.valueOf(o));
		}
		if (type.equals(String.class)) {
			return (o) -> (T) (o);
		}
		if (type.equals(Double.class)) {
			return (o) -> (T) (Double.valueOf(o));
		}
		if (type.equals(Boolean.class)) {
			return (o) -> (T) (Boolean.valueOf(o));
		}
		if (Enum.class.isAssignableFrom(type)) {
			return (o) -> {
				for (T t : type.getEnumConstants()) {
					Enum<?> theEnum = (Enum) t;
					if (theEnum.name().equals(o)) {
						return (T) theEnum;
					}
				}
				throw new IllegalArgumentException("Cannot convert string to enumeration: '" + o + "' is not an enum literal of " + type.getSimpleName());
			};
		}
		if (type.equals(File.class)) {
			return (o) -> (T) new File(o);
		}
		if (type.equals(Date.class)) {
			return (o) -> {
				try {
					return (T) DATE_FORMAT.parse(o);
				} catch (ParseException e) {
					throw new IllegalArgumentException("Cannot convert string to date: '" + o + "' has invalid pattern: " + DATE_FORMAT_PATTERN, e);
				}
			};
		}
		throw new IllegalArgumentException("Cannot infer built-in string converter for type " + type.getName());
	}

	private Function<T, String> inferStringConverter(Class<T> type) {
		if (type == null) {
			throw new IllegalArgumentException("Null type");
		}
		if (type.equals(Integer.class)) {
			return (o) -> String.valueOf(((Integer)o).intValue());
		}
		if (type.equals(Long.class)) {
			return (o) -> String.valueOf(((Long)o).longValue());
		}
		if (type.equals(String.class)) {
			return Object::toString;
		}
		if (type.equals(Double.class)) {
			return (o) -> String.valueOf(((Double)o).doubleValue());
		}
		if (type.equals(Boolean.class)) {
			return (o) -> String.valueOf(((Boolean)o).booleanValue());
		}
		if (Enum.class.isAssignableFrom(type)) {
			return (o) -> ((Enum<?>)o).name();
		}
		if (type.equals(File.class)) {
			return (o) -> ((File)o).getAbsolutePath();
		}
		if (type.equals(Date.class)) {
			return (o) -> DATE_FORMAT.format((Date)o);
		}
		throw new IllegalArgumentException("Cannot infer built-in string converter for type " + type.getName());
	}

	private Function<String, String> inferValidator(Class<T> type) {
		if (type == null) {
			throw new IllegalArgumentException("Null type");
		}
		if (type.equals(Integer.class)) {
			return (o) -> {
				try {
					Integer.parseInt(o);
					return null;
				} catch (Exception e) {
					return "Cannot parse string as integer: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (type.equals(Long.class)) {
			return (o) -> {
				try {
					Long.parseLong(o);
					return null;
				} catch (Exception e) {
					return "Cannot parse string as long: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (type.equals(Boolean.class)) {
			return (o) -> {
				try {
					Boolean.parseBoolean(o);
					return null;
				} catch (Exception e) {
					return "Cannot parse string as boolean: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (type.equals(String.class)) {
			return (o) -> null;
		}
		if (type.equals(Double.class)) {
			return (o) -> {
				try {
					Double.parseDouble(o);
					return null;
				} catch (Exception e) {
					return "Cannot parse string as double: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (Enum.class.isAssignableFrom(type)) {
			return (o) -> {
				try {
					for (T t : type.getEnumConstants()) {
						Enum<?> theEnum = (Enum) t;
						if (theEnum.name().equals(o)) {
							return null;
						}
					}
					return "Cannot parse string as enum of type " + type.getSimpleName() + ": value '" + o + "' is not recognized";
				} catch (Exception e) {
					return "Cannot parse string as enum: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (type.equals(File.class)) {
			return (o) -> {
				try {
					File f = new File(o);
					if (!f.exists()) {
						return "Provided file does not exist: " + f.getAbsolutePath();
					}
					if (!f.canRead()) {
						return "Cannot access file " + f.getAbsolutePath();
					}
					return null;
				} catch (Exception e) {
					return "Cannot parse string as file: '" + o + "' (" + e.getMessage() + ")";
				}
			};
		}
		if (type.equals(Date.class)) {
			return (o) -> {
				try {
					Date d = DATE_FORMAT.parse(o);
					return null;
				} catch (ParseException e) {
					return "Cannot parse string as date: '" + o + "', pattern is " + DATE_FORMAT_PATTERN;
				}
			};
		}
		throw new IllegalArgumentException("Cannot infer built-in validator for type " + type.getName());
	}

	private void validateDescriptor() {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Empty/Null ID");
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Empty/Null name");
		}
		if (description == null || description.isEmpty()) {
			throw new IllegalArgumentException("Empty/Null description");
		}
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public String getDefaultValueAsString() {
		if(defaultValue != null) {
			return this.stringConverter.apply(defaultValue);
		} else {
			return "";
		}
	}

	public boolean isMandatory() {
		return mandatory;
	}

	public Class<?> getType() {
		return type;
	}

	public List<String> getAllowedValuesAsString() {
		return allowedValues.stream()
				.map(this::convertToString)
				.collect(Collectors.toList());
	}

	public String[] getMinMaxValuesAsString() {
		if (minMaxValues != null) {
			return Arrays.stream(this.minMaxValues)
					.map(this::convertToString)
					.collect(Collectors.toList())
					.toArray(new String[2]);
		} else {
			return null;
		}
	}

	public T convertFromString(String value) {
		if(value == null || value.isEmpty()) {
			return null;
		} else {
			return this.valueConverter.apply(value);
		}
	}

	public String convertToString(T value) {
		if(value == null) {
			return "";
		} else {
			return this.stringConverter.apply(value);
		}
	}

	public String isValid(String value) {
		if (isMandatory() && (value == null || value.isEmpty())) {
			return "Missing mandatory property";
		}
		String result = this.validator.apply(value);
		if(result == null) {
			if(this.allowedValues != null && !this.allowedValues.contains(convertFromString(value))) {
				return "Provided value '" + value + "' is not among the allowed ones: " + this.allowedValues.toString();
			}
			T theValue = convertFromString(value);
			if(this.minMaxValues != null) {
				if(this.minMaxValues[0] != null) {
					if(((Comparable<T>) theValue).compareTo(this.minMaxValues[0]) < 0) {
						return "Provided value '" + value + "' is less than minimum value " + getMinMaxValuesAsString()[0];
					}
				}
				if(this.minMaxValues[1] != null) {
					if(((Comparable<T>) theValue).compareTo(this.minMaxValues[1]) > 0) {
						return "Provided value '" + value + "' is greater than maximum value " + getMinMaxValuesAsString()[1];
					}
				}
			}
		}
		// If here, all fine
		return result;
	}

	public ConnectorProperty<T> build(String value) {
		if (isMandatory() && (value == null || value.isEmpty())) {
			// Override with default
			value = convertToString(this.defaultValue);
		}
		String result = isValid(value);
		if(result != null) {
			throw new IllegalArgumentException("Invalid value for property " + id + ": " + result);
		}
		// If here, all fine
		return new ConnectorProperty<>(this, this.convertFromString(value));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConnectorPropertyDescriptor<?> that = (ConnectorPropertyDescriptor<?>) o;
		return mandatory == that.mandatory &&
				id.equals(that.id) &&
				name.equals(that.name) &&
				description.equals(that.description) &&
				type.equals(that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, name, description, mandatory, type);
	}
}
