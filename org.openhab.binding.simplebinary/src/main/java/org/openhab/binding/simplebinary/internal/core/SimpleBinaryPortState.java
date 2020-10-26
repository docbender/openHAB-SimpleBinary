/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.util.Calendar;

/**
 * Status of communication port
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryPortState {

    public enum PortStates {
        UNKNOWN,
        LISTENING,
        CLOSED,
        NOT_EXIST,
        NOT_AVAILABLE
    }

    private PortStates state = PortStates.UNKNOWN;
    private PortStates prevState = PortStates.UNKNOWN;
    private Calendar changedSince;
    private String itemState = null;
    private String itemPreviousState = null;
    private String itemStateChangeTime = null;

    /**
     * Return port status
     *
     * @return
     */
    public PortStates getState() {
        return state;
    }

    /**
     * Return previous status
     *
     * @return
     */
    public PortStates getPreviusState() {
        return prevState;
    }

    /**
     * Return date when last change occurred
     *
     * @return
     */
    public Calendar getChangeDate() {
        return changedSince;
    }

    /**
     * Set port state
     *
     * @param state
     */
    public void setState(PortStates state) {

        // set state only if previous is different
        if (this.state != state) {
            this.prevState = this.state;
            this.state = state;
            this.changedSince = Calendar.getInstance();

            /*
             * // update event bus
             * if (itemState != null) {
             * eventPublisher.postUpdate(itemState, new DecimalType(this.state.ordinal()));
             * }
             * if (itemPreviousState != null) {
             * eventPublisher.postUpdate(itemPreviousState, new DecimalType(this.prevState.ordinal()));
             * }
             * if (itemStateChangeTime != null) {
             * eventPublisher.postUpdate(itemStateChangeTime, new DateTimeType(this.changedSince));
             * }
             */
        }
    }
}
