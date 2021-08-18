package org.wso2.notification.event.handler.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.notification.event.handler.handler.PaConsultingNotificationEventHandler;

/**
 * OSGi bundle to PaConsulting Notification event handler.
 */
@Component(
        name = "org.wso2.notification.event.handler",
        immediate = true
)
public class PaConsultingNotificationServiceComponent {

    private static Log log = LogFactory.getLog(PaConsultingNotificationServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        try {
            BundleContext bundleContext = context.getBundleContext();
            bundleContext.registerService(AbstractEventHandler.class.getName(),
                    new PaConsultingNotificationEventHandler(),
                    null);
            if (log.isDebugEnabled()) {
                log.debug("PaConsultingCustomEventHandler is activated.");
            }
        } catch (Throwable e) {
            log.error("Error while activating PaConsultingCustomEventHandler component.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        log.debug("PaConsultingCustomEventHandler is de-activated.");
    }
}
