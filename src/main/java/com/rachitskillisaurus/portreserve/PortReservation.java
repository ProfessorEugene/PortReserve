package com.rachitskillisaurus.portreserve;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketImpl;
import java.util.Arrays;

/**
 * An API for representing a Port Reservation.  A port reservation wraps a bound server socket that can be
 * "transferred"
 * to another owner via one of the provided transfer methods.
 * <p/>
 * A transfer occurs by allowing code executing within the transfer callback to attempt to bind a server socket on the
 * port and interface represented by the reservation.  Instead of throwing an exception due to the address being
 * already
 * in use, the reserved socket is transferred to the code attempting the bind.
 * <p/>
 * The transfer process works by intercepting bind calls and delegating underlying socket implementations to the code
 * attempting the bind operation.
 *
 * @see com.rachitskillisaurus.portreserve.PortReservationProvider
 */
public class PortReservation implements Closeable {
    final InheritableThreadLocal<Boolean> transferMode = new InheritableThreadLocal<Boolean>();
    ServerSocket serverSocket;
    SocketImpl socketImpl;

    /**
     * Internal (package local) constructor
     */
    PortReservation() {
        transferMode.set(false);
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
     * Get the {@link java.net.InetAddress} for this reservation.  Equivalent to calling {@code
     * reservation.getServerSocket().getInetAddress()}
     *
     * @return the {@link java.net.InetAddress} for this reservation
     */
    public InetAddress getAddress() {
        return serverSocket.getInetAddress();
    }

    /**
     * Get the port for this reservation.  Equivalent to calling {@code reservation.getServerSocket().getLocalPort()}
     *
     * @return port for this reservation
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Get the server socket for this reservation
     *
     * @return server socket
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Transfer the underlying socket for this reservation to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable a {@code TransferCallback}
     * @see #transfer(TransferCallback, Iterable)
     * @see #transfer(TransferCallback, PortReservation...)
     */
    public void transfer(TransferCallback runnable) {
        transfer(runnable, this);
    }

    /**
     * Transfer multiple port reservations to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable     a {@code TransferCallback}
     * @param reservations a collection of {@code PortReservation} instances
     */
    public static void transfer(final TransferCallback runnable, Iterable<PortReservation> reservations) {
        try {
            for (PortReservation portReservation : reservations) {
                portReservation.transferMode.set(true);
            }
            runnable.transfer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            for (PortReservation portReservation : reservations) {
                portReservation.transferMode.set(false);
            }
        }
    }

    /**
     * Transfer multiple port reservations to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable     a {@code TransferCallback}
     * @param reservations a collection of {@code PortReservation} instances
     */
    public static void transfer(final TransferCallback runnable, PortReservation... reservations) {
        transfer(runnable, Arrays.asList(reservations));
    }

    /**
     * Set underlying server socket SocketImpl
     *
     * @param socketImpl server socket SocketImpl
     */
    protected void setSocketImpl(SocketImpl socketImpl) {
        this.socketImpl = socketImpl;
    }

    /**
     * Set underlying ServerSocket
     *
     * @param serverSocket server socket
     */
    protected void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Returns {@code true} if the current thread or any of its descendants are currently executing a transfer method
     *
     * @return {@code true} if the current thread or any of its descendants are currently executing a transfer method
     */
    protected boolean isInTransferMode() {
        return transferMode.get();
    }

    /**
     * Returns the {@code SocketImpl} for the underlying server socket
     *
     * @return the {@code SocketImpl} for the underlying server socket
     */
    protected SocketImpl getSocketImpl() {
        return socketImpl;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PortReservation{");
        sb.append("serverSocket=").append(serverSocket);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PortReservation that = (PortReservation) o;

        if (serverSocket != null ? !serverSocket.equals(that.serverSocket) : that.serverSocket != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return serverSocket != null ? serverSocket.hashCode() : 0;
    }

    /**
     * Close this port reservation by closing the underlying server socket
     *
     * @throws IOException if underlying server socket can not be closed
     */
    @Override
    public void close() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
}
