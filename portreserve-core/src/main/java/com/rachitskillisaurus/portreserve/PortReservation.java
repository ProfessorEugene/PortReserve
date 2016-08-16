package com.rachitskillisaurus.portreserve;

import com.rachitskillisaurus.portreserve.bootstrap.PortReservationInternal;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
    private PortReservationInternal portReservationInternal;

    static {
        PortReservationProvider.get();
    }

    /**
     * Internal (package local) constructor
     */
    PortReservation() {
        portReservationInternal = new PortReservationInternal();
    }

    /**
     * Get an {@link java.net.InetSocketAddress} for this reservation.  Equivalent to calling {@code new
     * InetSocketAddress(reservation.getServerSocket().getInetAddress(), reservation.getServerSocket().getLocalPort());}
     *
     * @return an {@link java.net.InetSocketAddress} for this reservation
     */
    public InetSocketAddress getSocketAddress() {
        return portReservationInternal.getSocketAddress();
    }

    /**
     * Get the port for this reservation.  Equivalent to calling {@code reservation.getServerSocket().getLocalPort()}
     *
     * @return port for this reservation
     */
    public int getPort() {
        return portReservationInternal.getServerSocket().getLocalPort();
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
        return portReservationInternal.getServerSocket();
    }

    /**
     * Transfer the underlying socket for this reservation to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable a {@code TransferCallback}
     * @see #transfer(TransferCallback, Iterable)
     * @see #transfer(TransferCallback, PortReservation...)
     * @throws IllegalStateException if underlying socket of this <code>PortReservation</code> closed
     */
    public <T> T transfer(TransferCallback<T> runnable) {
        try {
            if (isClosed()) {
                throw new IllegalStateException("Underlying socket for " + this + " is closed");
            }
            startTransfer();
            return runnable.transfer();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            stopTransfer();
        }
    }

    /**
     * Transfer multiple port reservations to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable     a {@code TransferCallback}
     * @param reservations a collection of {@code PortReservation} instances
     * @throws IllegalStateException if underlying socket of any <code>PortReservation</code> from <code>reservations</code> closed
     */
    public static void transfer(final TransferCallback runnable, Iterable<PortReservation> reservations) {
        try {
            for (PortReservation portReservation : reservations) {
                if (portReservation.isClosed()) {
                    throw new IllegalStateException("Underlying socket for " + portReservation + " is closed");
                }
                portReservation.startTransfer();
            }
            runnable.transfer();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            for (PortReservation portReservation : reservations) {
                portReservation.stopTransfer();
            }
        }
    }

    /**
     * Transfer multiple port reservations to code executed within the context of the supplied {@link
     * com.rachitskillisaurus.portreserve.TransferCallback}
     *
     * @param runnable     a {@code TransferCallback}
     * @param reservations a collection of {@code PortReservation} instances
     * @throws IllegalStateException if underlying socket of any <code>PortReservation</code> from <code>reservations</code> closed
     */
    public static void transfer(final TransferCallback runnable, PortReservation... reservations) {
        transfer(runnable, Arrays.asList(reservations));
    }

    private void startTransfer() {
        if (!portReservationInternal.getTransferMode().compareAndSet(false, true)) {
            throw new IllegalStateException("Port reservation is already in transfer mode");
        }
    }

    private void stopTransfer() {
        portReservationInternal.getTransferMode().set(false);
    }

    @Override
    public String toString() {
        return portReservationInternal.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PortReservation that = (PortReservation) o;

        return portReservationInternal != null ? portReservationInternal.equals(that.portReservationInternal) :
                that.portReservationInternal == null;

    }

    @Override
    public int hashCode() {
        return portReservationInternal != null ? portReservationInternal.hashCode() : 0;
    }

    /**
     * Close this port reservation by closing the underlying server socket
     *
     * @throws IOException if underlying server socket can not be closed
     */
    @Override
    public void close() throws IOException {
        portReservationInternal.close();
    }

    /**
     * @return true if underlying serverSocket closed
     */
    public boolean isClosed() {
        ServerSocket serverSocket = getServerSocket();
        return serverSocket == null || serverSocket.isClosed();
    }

    PortReservationInternal getInternal() {
        return portReservationInternal;
    }
}
