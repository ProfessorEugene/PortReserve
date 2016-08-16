package com.rachitskillisaurus.portreserve;

import com.rachitskillisaurus.portreserve.bb.PortReservationAgent;
import com.rachitskillisaurus.portreserve.bb.PortReserveSocketImplConstructor;
import com.rachitskillisaurus.portreserve.bootstrap.PortReservationInternal;
import com.rachitskillisaurus.portreserve.bootstrap.PortReservationRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

/**
 * A Port Reservation utility.
 *
 * @see com.rachitskillisaurus.portreserve.PortReservation
 */
public class PortReservationProvider {
    private ThreadLocal<PortReservationInternal> currentReservation = new ThreadLocal<PortReservationInternal>();
    private static PortReservationProvider instance;

    static {
        try {
            PortReservationAgent.premain(null, ByteBuddyAgent.install());
            if (PortReservationRegistry.class.getClassLoader() != null) {
                throw new IllegalStateException(
                        "com.rachitskillisaurus.portreserve.PortReservationProvider " +
                        "should be the first class you access for correct classloading");
            }
            instance = new PortReservationProvider();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PortReservationProvider get() {
        return instance;
    }

    private PortReservationProvider() throws Exception {
        @SuppressWarnings("unchecked")
        final Class <? extends SocketImpl> overriddenSocketImplClass = (Class <? extends SocketImpl>)
                Class.forName(PortReservationAgent.SOCKET_IMPL_CLASSNAME);

        updateSocketImplFactory(new SocketImplFactory() {
            @Override
            public SocketImpl createSocketImpl() {
                try {
                    PortReservationInternal ipr = currentReservation.get();
                    if (ipr != null) {
                        final SocketImpl originalSocketImpl = PortReserveSocketImplConstructor.socksSocketFactory.createSocketImpl();
                        ipr.setSocketImpl(originalSocketImpl);
                        return originalSocketImpl;
                    } else {
                        return overriddenSocketImplClass.newInstance();
                    }
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void updateSocketImplFactory(SocketImplFactory socketImplFactory) throws IOException {
        ServerSocket.setSocketFactory(socketImplFactory);
    }

    public PortReservation reservePort(InetSocketAddress address) throws IOException {
        /* bind a server socket using original socket factory */
        PortReservation ipr = new PortReservation();
        try {
            PortReservationInternal internal = ipr.getInternal();
            currentReservation.set(internal);
            ServerSocket serverSocket = new ServerSocket(address.getPort(), 0, address.getAddress());
            internal.setServerSocket(serverSocket);
            /* register bound socket's SocketImpl in global map */
            PortReservationRegistry.INSTANCE.put(address, internal);
        } finally {
            currentReservation.remove();
        }
        return ipr;
    }


    public PortReservation reserveOpenPort(InetAddress address, int startPort) {
        int testPort = startPort;
        while (testPort < 65535) {
            try {
                return reservePort(new InetSocketAddress(address, testPort));
            } catch (IOException exception) {
                testPort++;
            }
        }
        throw new RuntimeException("Exhausted all available ports");
    }

    public PortReservation reserveOpenPort(int startPort) {
        return reserveOpenPort(null, startPort);
    }

    public PortReservation reserveOpenPort() {
        return reserveOpenPort(1024);
    }
}
