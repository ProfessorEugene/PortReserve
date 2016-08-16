package com.rachitskillisaurus.portreserve.bootstrap;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketImpl;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class PortReservationInternal implements Closeable {
    private final AtomicBoolean transferMode = new AtomicBoolean();
    private ServerSocket serverSocket;
    private SocketImpl socketImpl;

    /**
     * Set underlying server socket SocketImpl
     *
     * @param socketImpl server socket SocketImpl
     */
    public void setSocketImpl(SocketImpl socketImpl) {
        this.socketImpl = socketImpl;
    }

    /**
     * Set underlying ServerSocket
     *
     * @param serverSocket server socket
     */
    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Get the server socket for this reservation, it's not the same socket that you got from
     * {@link com.rachitskillisaurus.portreserve.PortReservationProvider#reservePort(InetSocketAddress)},
     * {@link com.rachitskillisaurus.portreserve.PortReservationProvider#reserveOpenPort(InetAddress, int)}} or
     * {@link com.rachitskillisaurus.portreserve.PortReservationProvider#reserveOpenPort(int)}
     * It's an original ServerSocket. Use with caution.
     * For example, if you will call close() on this socket - PortReservationProvider and PortReservation will not be cleaned up
     *
     * @return server socket
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Get an {@link java.net.InetSocketAddress} for this reservation.  Equivalent to calling {@code new
     * InetSocketAddress(reservation.getServerSocket().getInetAddress(), reservation.getServerSocket().getLocalPort());}
     *
     * @return an {@link java.net.InetSocketAddress} for this reservation
     */
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    }

    /**
     * Returns {@code true} if the current thread or any of its descendants are currently executing a transfer method
     *
     * @return {@code true} if the current thread or any of its descendants are currently executing a transfer method
     */
    public boolean isInTransferMode() {
        return transferMode.get();
    }

    public AtomicBoolean getTransferMode() {
        return transferMode;
    }

    /**
     * @return the {@code SocketImpl} for the underlying server socket
     */
    public SocketImpl getSocketImpl() {
        return socketImpl;
    }

    /**
     * Close this port reservation by closing the underlying server socket
     *
     * @throws IOException if underlying server socket can not be closed
     */
    @Override
    public void close() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            PortReservationRegistry.INSTANCE.removePortReservationFromRegistry(this);
            serverSocket.close();
        }
    }

    @Override
    public String toString() {
        return "PortReservation{" + "serverSocket=" + serverSocket + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PortReservationInternal that = (PortReservationInternal) o;

        if (serverSocket != null ? ! serverSocket.equals(that.serverSocket) : that.serverSocket != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return serverSocket != null ? serverSocket.hashCode() : 0;
    }
}
