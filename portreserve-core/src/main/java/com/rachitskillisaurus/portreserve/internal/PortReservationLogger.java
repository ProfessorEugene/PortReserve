package com.rachitskillisaurus.portreserve.internal;

/**
 * @author Dmitry Spikhalskiy <dmitry@spikhalskiy.com>
 */
public class PortReservationLogger {
    public static void debug(String str, Object... args) {
        print(str, args);
    }

    public static void error(String str, Object... args) {
        print(str, args);
    }

    private static void print(String str, Object... args) {
        for (Object arg : args) {
            str = str.replace("{}", arg.toString());
        }
        System.out.println(str);
    }
}
