package com.rachitskillisaurus.portreserve;

import org.junit.Test;

import java.io.IOException;

/**
 * @author Dmitry Spikhalskiy <dspikhalskiy@spikhalskiy.com>
 */
public class TrivialServerIntegrationTest {
    @Test
    public void serverStartup() throws IOException {
        final PortReservation portReservation = PortReservationProvider.get().reserveOpenPort();
        TrivialServer server = portReservation.transfer(new TransferCallback<TrivialServer>() {
            @Override
            public TrivialServer transfer() throws Exception {
                return new TrivialServer(portReservation.getPort());
            }
        });

        server.stop();
    }
}
