package com.rachitskillisaurus.portreserve.bootstrap;

import java.net.SocketImpl;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public interface HasDelegate {
    SocketImpl getDelegate();
    void setDelegate(SocketImpl delegate);
}
