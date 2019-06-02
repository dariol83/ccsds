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

package eu.dariolucia.fx.charts;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.chart.Axis;
import javafx.util.StringConverter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

/**
 * An axis that displays date and time values using the Java Instant object.
 * 
 * Derivative work from Christian Schudt and Diego Cirujano "DateAxis", with contributions of Pedro Duque Vieira:
 * - http://myjavafx.blogspot.com/2013/09/javafx-charts-display-date-values-on.html
 * - https://www.pixelduke.com/2013/09/06/dateaxis-for-javafx/
 * - https://github.com/dukke/FXCharts
 *
 * The authors do not explicitly mention any license (besides mentioning the open source characteristics of
 * their code), it is fair that they get credits for what they did, and a big thank you.
 */
public final class InstantAxis extends Axis<Instant> {

    private final LongProperty currentLowerBound = new SimpleLongProperty(this, "currentLowerBound");

    private final LongProperty currentUpperBound = new SimpleLongProperty(this, "currentUpperBound");

    private final ObjectProperty<StringConverter<Instant>> tickLabelFormatter = new ObjectPropertyBase<StringConverter<Instant>>() {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public Object getBean() {
            return InstantAxis.this;
        }

        @Override
        public String getName() {
            return "tickLabelFormatter";
        }
    };

    private Instant minDate, maxDate;

    private ObjectProperty<Instant> lowerBound = new ObjectPropertyBase<Instant>() {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public Object getBean() {
            return InstantAxis.this;
        }

        @Override
        public String getName() {
            return "lowerBound";
        }
    };

    private ObjectProperty<Instant> upperBound = new ObjectPropertyBase<Instant>() {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public Object getBean() {
            return InstantAxis.this;
        }

        @Override
        public String getName() {
            return "upperBound";
        }
    };

    private Interval actualInterval = Interval.DECADE;

    /**
     * Default constructor. By default the lower and upper bound are calculated by the data.
     */
    public InstantAxis() {
    }

    /**
     * Constructs a date axis with fix lower and upper bounds.
     *
     * @param lowerBound The lower bound.
     * @param upperBound The upper bound.
     */
    public InstantAxis(Instant lowerBound, Instant upperBound) {
        this();
        setAutoRanging(false);
        setLowerBound(lowerBound);
        setUpperBound(upperBound);
    }

    /**
     * Constructs a date axis with a label and fix lower and upper bounds.
     *
     * @param axisLabel  The label for the axis.
     * @param lowerBound The lower bound.
     * @param upperBound The upper bound.
     */
    public InstantAxis(String axisLabel, Instant lowerBound, Instant upperBound) {
        this(lowerBound, upperBound);
        setLabel(axisLabel);
    }

    @Override
    public void invalidateRange(List<Instant> list) {
        super.invalidateRange(list);

        Collections.sort(list);
        if (list.isEmpty()) {
            minDate = maxDate = Instant.now();
        } else if (list.size() == 1) {
            minDate = maxDate = list.get(0);
        } else if (list.size() > 1) {
            minDate = list.get(0);
            maxDate = list.get(list.size() - 1);
        }
    }

    @Override
    protected Object autoRange(double length) {
        if (isAutoRanging()) {
            return new Object[]{minDate, maxDate};
        } else {
            if (getLowerBound() == null || getUpperBound() == null) {
                throw new IllegalArgumentException("If autoRanging is false, a lower and upper bound must be set.");
            }
            return getRange();
        }
    }

    @Override
    protected void setRange(Object range, boolean animating) {
        Object[] r = (Object[]) range;
        Instant lower = (Instant) r[0];
        Instant upper = (Instant) r[1];
        setLowerBound(lower);
        setUpperBound(upper);
        currentLowerBound.set(getLowerBound().toEpochMilli());
        currentUpperBound.set(getUpperBound().toEpochMilli());
    }

    @Override
    protected Object getRange() {
        return new Object[]{getLowerBound(), getUpperBound()};
    }

    @Override
    public double getZeroPosition() {
        return 0;
    }

    @Override
    public double getDisplayPosition(Instant date) {
        final double length = getSide().isHorizontal() ? getWidth() : getHeight();

        // Get the difference between the max and min date.
        double diff = currentUpperBound.get() - currentLowerBound.get();

        // Get the actual range of the visible area.
        // The minimal date should start at the zero position, that's why we subtract it.
        double range = length - getZeroPosition();

        // Then get the difference from the actual date to the min date and divide it by the total difference.
        // We get a value between 0 and 1, if the date is within the min and max date.
        double d = (date.toEpochMilli() - currentLowerBound.get()) / diff;

        // Multiply this percent value with the range and add the zero offset.
        if (getSide().isVertical()) {
            return getHeight() - d * range + getZeroPosition();
        } else {
            return d * range + getZeroPosition();
        }
    }

    @Override
    public Instant getValueForDisplay(double displayPosition) {
        final double length = getSide().isHorizontal() ? getWidth() : getHeight();

        // Get the difference between the max and min date.
        double diff = currentUpperBound.get() - currentLowerBound.get();

        // Get the actual range of the visible area.
        // The minimal date should start at the zero position, that's why we subtract it.
        double range = length - getZeroPosition();

        if (getSide().isVertical()) {
            // displayPosition = getHeight() - ((date - lowerBound) / diff) * range + getZero
            // date = displayPosition - getZero - getHeight())/range * diff + lowerBound
            return Instant.ofEpochMilli(((long) ((displayPosition - getZeroPosition() - getHeight()) / -range * diff + currentLowerBound.get())));
        } else {
            // displayPosition = ((date - lowerBound) / diff) * range + getZero
            // date = displayPosition - getZero)/range * diff + lowerBound
            return Instant.ofEpochMilli(((long) ((displayPosition - getZeroPosition()) / range * diff + currentLowerBound.get())));
        }
    }

    @Override
    public boolean isValueOnAxis(Instant date) {
        return date.toEpochMilli() > currentLowerBound.get() && date.toEpochMilli() < currentUpperBound.get();
    }

    @Override
    public double toNumericValue(Instant date) {
        return date.toEpochMilli();
    }

    @Override
    public Instant toRealValue(double v) {
        return Instant.ofEpochMilli((long) v);
    }

    @Override
    protected List<Instant> calculateTickValues(double v, Object range) {
        Object[] r = (Object[]) range;
        Instant lower = (Instant) r[0];
        Instant upper = (Instant) r[1];

        List<Instant> dateList = new ArrayList<Instant>();
        Calendar calendar = Calendar.getInstance();

        // The preferred gap which should be between two tick marks.
        double averageTickGap = 100;
        double averageTicks = v / averageTickGap;

        List<Instant> previousDateList = new ArrayList<Instant>();

        Interval previousInterval = Interval.values()[0];

        // Starting with the greatest interval, add one of each calendar unit.
        for (Interval interval : Interval.values()) {
            // Reset the calendar.
            calendar.setTimeInMillis(lower.toEpochMilli());
            // Clear the list.
            dateList.clear();
            previousDateList.clear();
            actualInterval = interval;

            // Loop as long we exceeded the upper bound.
            while (calendar.getTime().getTime() <= upper.toEpochMilli()) {
                dateList.add(Instant.ofEpochMilli(calendar.getTimeInMillis()));
                calendar.add(interval.interval, interval.amount);
            }
            // Then check the size of the list. If it is greater than the amount of ticks, take that list.
            if (dateList.size() > averageTicks) {
                calendar.setTimeInMillis(lower.toEpochMilli());
                // Recheck if the previous interval is better suited.
                while (calendar.getTime().getTime() <= upper.toEpochMilli()) {
                    previousDateList.add(Instant.ofEpochMilli(calendar.getTimeInMillis()));
                    calendar.add(previousInterval.interval, previousInterval.amount);
                }
                break;
            }

            previousInterval = interval;
        }
        if (previousDateList.size() - averageTicks > averageTicks - dateList.size()) {
            dateList = previousDateList;
            actualInterval = previousInterval;
        }

        // At last add the upper bound.
        dateList.add(upper);

        List<Instant> evenDateList = makeDatesEven(dateList, calendar);
        // If there are at least three dates, check if the gap between the lower date and the second date is at least half the gap of the second and third date.
        // Do the same for the upper bound.
        // If gaps between dates are to small, remove one of them.
        // This can occur, e.g. if the lower bound is 25.12.2013 and years are shown. Then the next year shown would be 2014 (01.01.2014) which would be too narrow to 25.12.2013.
        if (evenDateList.size() > 2) {

        	Instant secondDate = evenDateList.get(1);
        	Instant thirdDate = evenDateList.get(2);
        	Instant lastDate = evenDateList.get(dateList.size() - 2);
        	Instant previousLastDate = evenDateList.get(dateList.size() - 3);

            // If the second date is too near by the lower bound, remove it.
            if (secondDate.toEpochMilli() - lower.toEpochMilli() < (thirdDate.toEpochMilli() - secondDate.toEpochMilli()) / 2) {
                evenDateList.remove(secondDate);
            }

            // If difference from the upper bound to the last date is less than the half of the difference of the previous two dates,
            // we better remove the last date, as it comes to close to the upper bound.
            if (upper.toEpochMilli() - lastDate.toEpochMilli() < (lastDate.toEpochMilli() - previousLastDate.toEpochMilli()) / 2) {
                evenDateList.remove(lastDate);
            }
        }

        return evenDateList;
    }

    @Override
    protected void layoutChildren() {
        if (!isAutoRanging()) {
            currentLowerBound.set(getLowerBound().toEpochMilli());
            currentUpperBound.set(getUpperBound().toEpochMilli());
        }
        super.layoutChildren();
    }

    @Override
    protected String getTickMarkLabel(Instant date) {

        StringConverter<Instant> converter = getTickLabelFormatter();
        if (converter != null) {
            return converter.toString(date);
        }

        DateFormat dateFormat;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date.toEpochMilli());

        if (actualInterval.interval == Calendar.YEAR && calendar.get(Calendar.MONTH) == 0 && calendar.get(Calendar.DATE) == 1) {
            dateFormat = new SimpleDateFormat("yyyy");
        } else if (actualInterval.interval == Calendar.MONTH && calendar.get(Calendar.DATE) == 1) {
            dateFormat = new SimpleDateFormat("MMM yy");
        } else {
            switch (actualInterval.interval) {
                case Calendar.DATE:
                case Calendar.WEEK_OF_YEAR:
                default:
                    dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
                    break;
                case Calendar.HOUR:
                case Calendar.MINUTE:
                    dateFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
                    break;
                case Calendar.SECOND:
                    dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
                    break;
                case Calendar.MILLISECOND:
                    dateFormat = DateFormat.getTimeInstance(DateFormat.FULL);
                    break;
            }
        }
        return dateFormat.format(new Date(date.toEpochMilli()));
    }

    /**
     * Makes dates even, in the sense of that years always begin in January, months always begin on the 1st and days always at midnight.
     *
     * @param dates The list of dates.
     * @return The new list of dates.
     */
    private List<Instant> makeDatesEven(List<Instant> dates, Calendar calendar) {
        // If the dates contain more dates than just the lower and upper bounds, make the dates in between even.
        if (dates.size() > 2) {
            List<Instant> evenDates = new ArrayList<Instant>();

            // For each interval, modify the date slightly by a few millis, to make sure they are different days.
            // This is because Axis stores each value and won't update the tick labels, if the value is already known.
            // This happens if you display days and then add a date many years in the future the tick label will still be displayed as day.
            for (int i = 0; i < dates.size(); i++) {
                calendar.setTimeInMillis(dates.get(i).toEpochMilli());
                switch (actualInterval.interval) {
                    case Calendar.YEAR:
                        // If its not the first or last date (lower and upper bound), make the year begin with first month and let the months begin with first day.
                        if (i != 0 && i != dates.size() - 1) {
                            calendar.set(Calendar.MONTH, 0);
                            calendar.set(Calendar.DATE, 1);
                        }
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 6);
                        break;
                    case Calendar.MONTH:
                        // If its not the first or last date (lower and upper bound), make the months begin with first day.
                        if (i != 0 && i != dates.size() - 1) {
                            calendar.set(Calendar.DATE, 1);
                        }
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 5);
                        break;
                    case Calendar.WEEK_OF_YEAR:
                        // Make weeks begin with first day of week?
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 4);
                        break;
                    case Calendar.DATE:
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 3);
                        break;
                    case Calendar.HOUR:
                        if (i != 0 && i != dates.size() - 1) {
                            calendar.set(Calendar.MINUTE, 0);
                            calendar.set(Calendar.SECOND, 0);
                        }
                        calendar.set(Calendar.MILLISECOND, 2);
                        break;
                    case Calendar.MINUTE:
                        if (i != 0 && i != dates.size() - 1) {
                            calendar.set(Calendar.SECOND, 0);
                        }
                        calendar.set(Calendar.MILLISECOND, 1);
                        break;
                    case Calendar.SECOND:
                        calendar.set(Calendar.MILLISECOND, 0);
                        break;

                }
                evenDates.add(Instant.ofEpochMilli(calendar.getTimeInMillis()));
            }

            return evenDates;
        } else {
            return dates;
        }
    }

    /**
     * Gets the lower bound of the axis.
     *
     * @return The property.
     * @see #getLowerBound()
     * @see #setLowerBound(java.time.Instant)
     */
    public final ObjectProperty<Instant> lowerBoundProperty() {
        return lowerBound;
    }

    /**
     * Gets the lower bound of the axis.
     *
     * @return The lower bound.
     * @see #lowerBoundProperty()
     */
    public final Instant getLowerBound() {
        return lowerBound.get();
    }

    /**
     * Sets the lower bound of the axis.
     *
     * @param date The lower bound date.
     * @see #lowerBoundProperty()
     */
    public final void setLowerBound(Instant date) {
        lowerBound.set(date);
    }

    /**
     * Gets the upper bound of the axis.
     *
     * @return The property.
     * @see #getUpperBound() ()
     * @see #setUpperBound(java.time.Instant)
     */
    public final ObjectProperty<Instant> upperBoundProperty() {
        return upperBound;
    }

    /**
     * Gets the upper bound of the axis.
     *
     * @return The upper bound.
     * @see #upperBoundProperty()
     */
    public final Instant getUpperBound() {
        return upperBound.get();
    }

    /**
     * Sets the upper bound of the axis.
     *
     * @param date The upper bound date.
     * @see #upperBoundProperty() ()
     */
    public final void setUpperBound(Instant date) {
        upperBound.set(date);
    }

    /**
     * Gets the tick label formatter for the ticks.
     *
     * @return The converter.
     */
    public final StringConverter<Instant> getTickLabelFormatter() {
        return tickLabelFormatter.getValue();
    }

    /**
     * Sets the tick label formatter for the ticks.
     *
     * @param value The converter.
     */
    public final void setTickLabelFormatter(StringConverter<Instant> value) {
        tickLabelFormatter.setValue(value);
    }

    /**
     * Gets the tick label formatter for the ticks.
     *
     * @return The property.
     */
    public final ObjectProperty<StringConverter<Instant>> tickLabelFormatterProperty() {
        return tickLabelFormatter;
    }

    /**
     * The intervals, which are used for the tick labels. Beginning with the largest interval, the axis tries to calculate the tick values for this interval.
     * If a smaller interval is better suited for, that one is taken.
     */
    private enum Interval {
        DECADE(Calendar.YEAR, 10),
        YEAR(Calendar.YEAR, 1),
        MONTH_6(Calendar.MONTH, 6),
        MONTH_3(Calendar.MONTH, 3),
        MONTH_1(Calendar.MONTH, 1),
        WEEK(Calendar.WEEK_OF_YEAR, 1),
        DAY(Calendar.DATE, 1),
        HOUR_12(Calendar.HOUR, 12),
        HOUR_6(Calendar.HOUR, 6),
        HOUR_3(Calendar.HOUR, 3),
        HOUR_1(Calendar.HOUR, 1),
        MINUTE_15(Calendar.MINUTE, 15),
        MINUTE_5(Calendar.MINUTE, 5),
        MINUTE_1(Calendar.MINUTE, 1),
        SECOND_15(Calendar.SECOND, 15),
        SECOND_5(Calendar.SECOND, 5),
        SECOND_1(Calendar.SECOND, 1),
        MILLISECOND(Calendar.MILLISECOND, 1);

        private final int amount;

        private final int interval;

        Interval(int interval, int amount) {
            this.interval = interval;
            this.amount = amount;
        }
    }
}