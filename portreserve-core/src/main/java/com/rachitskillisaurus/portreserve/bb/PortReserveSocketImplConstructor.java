package com.rachitskillisaurus.portreserve.bb;

import com.rachitskillisaurus.portreserve.bootstrap.HasDelegate;
import com.rachitskillisaurus.portreserve.internal.OriginalSocksSocketImplFactory;
import net.bytebuddy.implementation.bind.annotation.This;

import java.net.SocketImplFactory;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class PortReserveSocketImplConstructor {
    public final static SocketImplFactory socksSocketFactory = getOriginalSocketImplFactory();

    private static SocketImplFactory getOriginalSocketImplFactory() {
        return new OriginalSocksSocketImplFactory();
    }

    @SuppressWarnings("unused")
    public static void construct(@This HasDelegate newObj) throws Exception {
       newObj.setDelegate(socksSocketFactory.createSocketImpl());
    }
}
