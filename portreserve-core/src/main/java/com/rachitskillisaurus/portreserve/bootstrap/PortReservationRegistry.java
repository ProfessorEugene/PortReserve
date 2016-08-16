package com.rachitskillisaurus.portreserve.bootstrap;

import com.rachitskillisaurus.portreserve.internal.PortReservationLogger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public enum PortReservationRegistry {
    INSTANCE;

    private Map<InetSocketAddress, PortReservationInternal> reservationRegistry = new ConcurrentHashMap<InetSocketAddress, PortReservationInternal>();

    public void removePortReservationFromRegistry(PortReservationInternal portReservation) {
        PortReservationInternal oldPortReservation = reservationRegistry.remove(portReservation.getSocketAddress());
        if (oldPortReservation != portReservation) {
            PortReservationLogger.error("We removed different port reservation from registry", portReservation);
        }
    }

    public PortReservationInternal get(InetSocketAddress bindAddress) {
        return reservationRegistry.get(bindAddress);
    }

    public void put(InetSocketAddress address, PortReservationInternal ipr) {
        reservationRegistry.put(address, ipr);
    }

    public int size() {
        return reservationRegistry.size();
    }
}
