package com.rachitskillisaurus.portreserve;

import java.lang.reflect.Constructor;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

public class SocksSocketImplFactory implements SocketImplFactory {
    Constructor<? extends SocketImpl> socksSocketImplConstructor;

    public SocksSocketImplFactory() {
        try {
            socksSocketImplConstructor = (Constructor<? extends SocketImpl>) Class.forName("java.net.SocksSocketImpl").getDeclaredConstructor();
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
