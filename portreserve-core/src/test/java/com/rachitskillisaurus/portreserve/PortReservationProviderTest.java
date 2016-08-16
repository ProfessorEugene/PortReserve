package com.rachitskillisaurus.portreserve;

import com.rachitskillisaurus.portreserve.bb.PortReservationAgent;
import com.rachitskillisaurus.portreserve.bootstrap.PortReservationRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.SocketImpl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PortReservationProviderTest {

    @BeforeClass
    public static void setUp() throws Exception {
        PortReservationProvider.get();
    }

    @Test
    public void testMultipleReservations() throws IOException {
        final PortReservation ra = PortReservationProvider.get().reserveOpenPort(1024);
        final PortReservation rb = PortReservationProvider.get().reserveOpenPort(1024);
        try {
            assertExceptionReservingPort(ra.getPort());
            assertExceptionReservingPort(rb.getPort());
            final AtomicReference<ServerSocket> refA = new AtomicReference<ServerSocket>();
            final AtomicReference<ServerSocket> refB = new AtomicReference<ServerSocket>();
            PortReservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    refA.set(new ServerSocket(ra.getPort()));
                    refB.set(new ServerSocket(rb.getPort()));
                    return null;
                }
            }, ra, rb);
            assertTrue(refA.get().isBound());
            assertTrue(refB.get().isBound());
            ra.close();
            rb.close();
        } finally {
            ra.close();
            rb.close();
        }
    }

    @Test
    public void testSeparateThreadReservation() throws IOException {
        final PortReservation reservation = PortReservationProvider.get().reserveOpenPort(1025);
        try {
            System.out.println(reservation);
            /* try to create a server socket on port and observe failure */
            assertExceptionReservingPort(reservation.getPort());
            /* now, try *transferring* a socket */
            final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
            reservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
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
                    return null;
                }
            });
            assertTrue(ref.get().isBound());
        } finally {
            reservation.close();
        }
    }

    @Test
    public void closeOnServerSocketRemoveFromReservationRegistry() throws IOException {
        int initialSize = PortReservationRegistry.INSTANCE.size();
        final PortReservation firstReservation = PortReservationProvider.get().reserveOpenPort(1026);
        try {
            System.out.println(firstReservation);
            assertEquals(1026, firstReservation.getPort());

            final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
            firstReservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    try {
                        ref.set(new ServerSocket(firstReservation.getPort()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            assertTrue(ref.get().isBound());
            ref.get().close();

            assertEquals(initialSize, PortReservationRegistry.INSTANCE.size());
        } finally {
            firstReservation.close();
        }
    }

    @Test
    public void closeRemoveFromReservationRegistry() throws IOException {
        int initialSize = PortReservationRegistry.INSTANCE.size();
        final PortReservation firstReservation = PortReservationProvider.get().reserveOpenPort(1027);
        final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
        try {
            assertEquals(1027, firstReservation.getPort());

            firstReservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    try {
                        ref.set(new ServerSocket(firstReservation.getPort()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            assertTrue(ref.get().isBound());
        } finally {
            firstReservation.close();
        }
        assertEquals(initialSize, PortReservationRegistry.INSTANCE.size());
    }

    @Test(expected = IllegalStateException.class)
    public void testTransferOfClosedSocketFailingWithIllegalStateException() throws IOException {
        final PortReservation reservation = PortReservationProvider.get().reserveOpenPort(1028);
        try {
            System.out.println(reservation);
            assertEquals(1028, reservation.getPort());

            final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
            reservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    try {
                        ref.set(new ServerSocket(reservation.getPort()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            assertTrue(ref.get().isBound());

            ref.get().close();

            reservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    return null;
                }
            });
        } finally {
            reservation.close();
        }
    }

    @Test
    public void bootstrapClassloader() throws Exception {
        assertNull("Class must be loaded by bootstrap classloader", Class.forName(
                PortReservationAgent.SOCKET_IMPL_CLASSNAME).getClassLoader());
    }

    @Test
    public void methodsDelegating() throws Exception {
        final PortReservation firstReservation = PortReservationProvider.get().reserveOpenPort(1029);
        try {
            System.out.println(firstReservation);
            assertEquals(1029, firstReservation.getPort());

            final AtomicReference<ServerSocket> ref = new AtomicReference<ServerSocket>();
            firstReservation.transfer(new TransferCallback() {
                @Override
                public Void transfer() throws Exception {
                    try {
                        ref.set(new ServerSocket(firstReservation.getPort()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });

            ServerSocket ss = ref.get();
            Field implField = ServerSocket.class.getDeclaredField("impl");
            implField.setAccessible(true);
            SocketImpl overriddenSocketImpl = (SocketImpl) implField.get(ss);

            SocketImpl originalSocketImpl = DelegateUtil.getDelegate(overriddenSocketImpl);

            Method m = SocketImpl.class.getDeclaredMethod("getServerSocket");
            m.setAccessible(true);
            assertEquals("Package-local methods should return same result",
                         m.invoke(originalSocketImpl), m.invoke(overriddenSocketImpl));

            m = SocketImpl.class.getDeclaredMethod("getLocalPort");
            m.setAccessible(true);
            assertEquals("Protected methods should return same result",
                         m.invoke(originalSocketImpl), m.invoke(overriddenSocketImpl));
        } finally {
            firstReservation.close();
        }
    }

    private void assertExceptionReservingPort(int port) {
        IOException caught = null;
        try {
            new ServerSocket(port);
        } catch (IOException thrown) {
            caught = thrown;
        }
        assertNotNull(caught);
    }
}
