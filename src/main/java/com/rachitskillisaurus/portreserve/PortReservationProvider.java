package com.rachitskillisaurus.portreserve;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Port Reservation utility.
 *
 * @see com.rachitskillisaurus.portreserve.PortReservation
 */
public class PortReservationProvider {
    private static Logger log = LoggerFactory.getLogger(PortReservationProvider.class);
    private Map<InetSocketAddress, PortReservation> reservationRegistry = new ConcurrentHashMap<InetSocketAddress, PortReservation>();
    private ThreadLocal<PortReservation> currentReservation = new ThreadLocal<PortReservation>();
    private static PortReservationProvider instance;

    static {
        try {
            instance = new PortReservationProvider();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static PortReservationProvider get() {
        return instance;
    }

    protected PortReservationProvider() throws IOException {
        final SocketImplFactory socksSocketFactory = getOriginalSocketImplFactory();
        updateSocketImplFactory(new SocketImplFactory() {
            @Override
            public SocketImpl createSocketImpl() {
                final SocketImpl originalSocketImpl = socksSocketFactory.createSocketImpl();
                /* if we are in the process of reserving a port, remember original socket impl */
                PortReservation ipr = currentReservation.get();
                if (ipr != null) {
                    log.debug("Making port reservation via {}", originalSocketImpl);
                    ipr.setSocketImpl(originalSocketImpl);
                    return originalSocketImpl;
                }
                /* in all other cases return a SocketImpl that can dynamically switch delegates */
                return (SocketImpl) Enhancer.create(SocketImpl.class, new MethodInterceptor() {
                    volatile SocketImpl delegate = originalSocketImpl;

                    @Override
                    public Object intercept(Object socketImplInstance, Method method, Object[] arguments, MethodProxy methodProxy) throws Throwable {
                        /* reset delegate when attempting to bind */
                        if ("bind".equals(method.getName())) {
                            InetSocketAddress bindAddress = new InetSocketAddress((InetAddress) arguments[0], (Integer) arguments[1]);
                            log.debug("Attempting to bind a socket to {}", bindAddress);
                            PortReservation internalPortReservation = reservationRegistry.get(bindAddress);
                            if (internalPortReservation != null) {
                                log.debug("Found port reservation {}", internalPortReservation);
                                if (internalPortReservation.isInTransferMode()) {
                                    delegate = internalPortReservation.getSocketImpl();
                                    log.debug("Delegating bind on {} to {}", bindAddress, delegate);
                                    /* socket is already bound so do nothing */
                                    return null;
                                }
                            }
                        }
                        method.setAccessible(true);
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
            }
        });
    }

    protected SocketImplFactory getOriginalSocketImplFactory() {
        return new SocksSocketImplFactory();
    }

    protected void updateSocketImplFactory(SocketImplFactory socketImplFactory) throws IOException {
        ServerSocket.setSocketFactory(socketImplFactory);
    }


    public PortReservation reservePort(InetSocketAddress address) throws IOException {
        /* bind a server socket using original socket factory */
        PortReservation ipr = new PortReservation();
        try {
            currentReservation.set(ipr);
            ServerSocket serverSocket = new ServerSocket(address.getPort(), 0, address.getAddress());
            ipr.setServerSocket(serverSocket);
            /* register bound socket's SocketImpl in global map */
            reservationRegistry.put(address, ipr);
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
                log.debug("Could not reserve {} {}", address, testPort);
                testPort++;
            }
        }
        throw new RuntimeException("Exhausted all available ports");
    }

    public PortReservation reserveOpenPort(int startPort) {
        return reserveOpenPort(null, startPort);
    }

}
