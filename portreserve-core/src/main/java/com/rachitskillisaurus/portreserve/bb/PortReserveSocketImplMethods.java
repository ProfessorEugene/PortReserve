package com.rachitskillisaurus.portreserve.bb;

import com.rachitskillisaurus.portreserve.bootstrap.HasDelegate;
import com.rachitskillisaurus.portreserve.internal.PortReservationLogger;
import com.rachitskillisaurus.portreserve.bootstrap.PortReservationInternal;
import com.rachitskillisaurus.portreserve.bootstrap.PortReservationRegistry;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.This;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketImpl;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class PortReserveSocketImplMethods {
    @SuppressWarnings("unused")
    public static void bind(InetAddress host, int port, @This HasDelegate obj, @Origin Method method) throws IOException {
        InetSocketAddress bindAddress = new InetSocketAddress(host, port);
        PortReservationLogger.debug("Attempting to bind a socket to {}", bindAddress);
        PortReservationInternal internalPortReservation = PortReservationRegistry.INSTANCE.get(bindAddress);
        if (internalPortReservation != null) {
            PortReservationLogger.debug("Found port reservation {}", internalPortReservation);
            if (internalPortReservation.isInTransferMode()) {
                closeInternal(obj.getDelegate());
                SocketImpl delegate = internalPortReservation.getSocketImpl();
                obj.setDelegate(delegate);
                PortReservationLogger.debug("Delegating bind on {} to {}", bindAddress, delegate);
                /* socket is already bound so do nothing */
                return;
            }
        }

        try {
            method.setAccessible(true);
            method.invoke(obj.getDelegate(), host, port);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void close(@This HasDelegate obj, @Origin Method method) throws IOException {
        SocketImpl delegate = obj.getDelegate();

        ServerSocket serverSocket;
        try {
            Field socketField = SocketImpl.class.getDeclaredField("serverSocket");
            socketField.setAccessible(true);
            serverSocket = (ServerSocket) socketField.get(delegate);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        InetSocketAddress socketAddress = (InetSocketAddress) serverSocket.getLocalSocketAddress();
        //it's possible to get null here if for example we call close in ServerSocket after unsuccessful bind() (port already in use)
        if (socketAddress != null) {
            PortReservationInternal portReservation = PortReservationRegistry.INSTANCE.get(socketAddress);
            if (portReservation != null) {
                PortReservationLogger.debug("Found port reservation {}, close", portReservation);
                portReservation.close();
                return;
            }
        }

        try {
            method.setAccessible(true);
            method.invoke(delegate);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            } else {
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeInternal(SocketImpl socketToClose) {
        try {
            Method closeMethod = SocketImpl.class.getDeclaredMethod("close");
            closeMethod.setAccessible(true);
            closeMethod.invoke(socketToClose);
        } catch (NoSuchMethodException e) {
            PortReservationLogger.error("Can't get close() method of " + socketToClose, e);
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            PortReservationLogger.error("Can't call close() method of " + socketToClose, e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            PortReservationLogger.error("Can't call close() method of " + socketToClose, e);
            throw new RuntimeException(e);
        }
    }
}
