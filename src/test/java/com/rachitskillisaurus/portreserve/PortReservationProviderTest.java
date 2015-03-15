package com.rachitskillisaurus.portreserve;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PortReservationProviderTest {

    //InetAddress localhost = InetAddress.getLoopbackAddress();

    @Test
    public void testMultipleReservations() throws IOException {
        final PortReservation ra = PortReservationProvider.get().reserveOpenPort(1024);
        final PortReservation rb = PortReservationProvider.get().reserveOpenPort(1024);
        assertExceptionReservingPort(ra.getPort());
        assertExceptionReservingPort(rb.getPort());
        final AtomicReference<ServerSocket> refA = new AtomicReference<ServerSocket>();
        final AtomicReference<ServerSocket> refB = new AtomicReference<ServerSocket>();
        PortReservation.transfer(new TransferCallback() {
            @Override
            public void transfer() throws Exception {
                refA.set(new ServerSocket(ra.getPort()));
                refB.set(new ServerSocket(rb.getPort()));
            }
        }, ra, rb);
        assertTrue(refA.get().isBound());
        assertTrue(refB.get().isBound());
        ra.close();
        rb.close();
    }

    @Test
    public void testSeparateThreadReservation() throws IOException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        final PortReservation reservation = PortReservationProvider.get().reserveOpenPort(1024);
        System.out.println(reservation);
        /* try to create a server socket on port and observe failure */
        assertExceptionReservingPort(reservation.getPort());
        /* now, try *transferring* a socket */
        final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
        reservation.transfer(new TransferCallback() {
            @Override
            public void transfer() throws Exception {
                final CountDownLatch cdl = new CountDownLatch(1);
                new Thread() {
                    public void run() {
                        try {
                            ref.set(new ServerSocket(reservation.getPort()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            cdl.countDown();
                        }
                    }
                }.start();
                cdl.await(30, TimeUnit.SECONDS);
            }
        });
        assertTrue(ref.get().isBound());
    }

    protected void assertExceptionReservingPort(int port) {
        IOException caught = null;
        try {
            new ServerSocket(port);
        } catch (IOException thrown) {
            caught = thrown;
        }
        assertNotNull(caught);
    }
}
