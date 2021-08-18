package org.wso2.notification.event.handler.util;

/**
 * Utility functionality for previousEmailAddress and new email address.
 */
public class PaConsultingNotificationHandlerUtil {

    private static ThreadLocal<String> threadLocalPreviousEmailAddress = new ThreadLocal<>();
    private static ThreadLocal<String> threadLocalNewEmailAddress = new ThreadLocal<>();

    public PaConsultingNotificationHandlerUtil() {

    }

    public static void resetThreadLocalPreviousEmailAddress() {

        threadLocalPreviousEmailAddress.remove();
    }

    public static String getThreadLocalPreviousEmailAddress() {

        return threadLocalPreviousEmailAddress.get();
    }

    public static void setThreadLocalPreviousEmailAddress(String value) {

        threadLocalPreviousEmailAddress.set(value);
    }

    public static String getThreadLocalNewEmailAddress() {

        return threadLocalNewEmailAddress.get();
    }

    public static void setThreadLocalNewEmailAddress(String value) {

        threadLocalNewEmailAddress.set(value);
    }

    public static void resetThreadLocalNewEmailAddress() {

        threadLocalNewEmailAddress.remove();
    }
}
