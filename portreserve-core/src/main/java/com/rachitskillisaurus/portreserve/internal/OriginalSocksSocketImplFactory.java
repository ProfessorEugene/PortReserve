package com.rachitskillisaurus.portreserve.internal;

import java.lang.reflect.Constructor;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class OriginalSocksSocketImplFactory implements SocketImplFactory {
    private Constructor<? extends SocketImpl> socksSocketImplConstructor;

    @SuppressWarnings("unchecked")
    public OriginalSocksSocketImplFactory() {
        try {
            socksSocketImplConstructor = (Constructor<? extends SocketImpl>)
                    Class.forName("java.net.SocksSocketImpl").getDeclaredConstructor();
            socksSocketImplConstructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SocketImpl createSocketImpl() {
        try {
            return socksSocketImplConstructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
