package com.rachitskillisaurus.portreserve;

import java.lang.reflect.Field;
import java.net.SocketImpl;

/**
 * @author Dmitry Spikhalskiy <dspikhalskiy@pulsepoint.com>
 */
class DelegateUtil {
    static SocketImpl getDelegate(Object mockedSocket) {
        try {
            Field delegateField = mockedSocket.getClass().getField("delegate");
            delegateField.setAccessible(true);
            return (SocketImpl) delegateField.get(mockedSocket);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
