package org.wso2.notification.event.handler.handler;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.User;
import org.wso2.carbon.identity.configuration.mgt.core.ConfigurationManager;
import org.wso2.carbon.identity.configuration.mgt.core.ConfigurationManagerImpl;
import org.wso2.carbon.identity.configuration.mgt.core.dao.ConfigurationDAO;
import org.wso2.carbon.identity.configuration.mgt.core.dao.impl.ConfigurationDAOImpl;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementException;
import org.wso2.carbon.identity.configuration.mgt.core.model.Attribute;
import org.wso2.carbon.identity.configuration.mgt.core.model.ConfigurationManagerConfigurationHolder;
import org.wso2.carbon.identity.core.bean.context.MessageContext;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.internal.IdentityRecoveryServiceDataHolder;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.notification.event.handler.constant.Constants;
import org.wso2.notification.event.handler.util.PaConsultingNotificationHandlerUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom-event handler to send notification when user changes the existing email address.
 */
public class PaConsultingNotificationEventHandler extends AbstractEventHandler {

    private static final Log log = LogFactory.getLog(PaConsultingNotificationEventHandler.class);

    @Override
    public String getName() {

        return "paConsultingNotificationHandler";
    }

    @Override
    public int getPriority(MessageContext messageContext) {

        return 64;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        Map<String, Object> eventProperties = event.getEventProperties();
        String eventName = event.getEventName();
        UserStoreManager userStoreManager = (UserStoreManager) eventProperties.get(
                IdentityEventConstants.EventProperty.USER_STORE_MANAGER);

        Map<String, String> claims = (Map<String, String>) eventProperties.get(IdentityEventConstants.EventProperty
                .USER_CLAIMS);
        User user = getUser(eventProperties, userStoreManager);
        if (IdentityEventConstants.Event.PRE_SET_USER_CLAIMS.equals(eventName) &&
                claims.containsKey(IdentityRecoveryConstants.EMAIL_ADDRESS_CLAIM)) {

            String existingEmail = getEmailClaimValue(user, userStoreManager);
            PaConsultingNotificationHandlerUtil.resetThreadLocalPreviousEmailAddress();
            PaConsultingNotificationHandlerUtil.resetThreadLocalNewEmailAddress();
            PaConsultingNotificationHandlerUtil.setThreadLocalPreviousEmailAddress(existingEmail);
            PaConsultingNotificationHandlerUtil
                    .setThreadLocalNewEmailAddress(claims.get(IdentityRecoveryConstants.EMAIL_ADDRESS_CLAIM));
        }
        if (IdentityEventConstants.Event.POST_SET_USER_CLAIMS.equals(eventName)) {
            String previousEmailAddress =
                    PaConsultingNotificationHandlerUtil.getThreadLocalPreviousEmailAddress();
            String newEmailAddress = PaConsultingNotificationHandlerUtil.getThreadLocalNewEmailAddress();

            if (StringUtils.isNotBlank(previousEmailAddress)) {
                sendNotificationOnEmailUpdate(user, previousEmailAddress, newEmailAddress);
                PaConsultingNotificationHandlerUtil.resetThreadLocalPreviousEmailAddress();
                PaConsultingNotificationHandlerUtil.resetThreadLocalNewEmailAddress();
            }
        }
    }

    protected User getUser(Map eventProperties, UserStoreManager userStoreManager) {

        String userName = (String) eventProperties.get(IdentityEventConstants.EventProperty.USER_NAME);
        String tenantDomain = (String) eventProperties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        String domainName = userStoreManager.getRealmConfiguration().getUserStoreProperty(
                UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

        User user = new User();
        user.setUserName(userName);
        user.setTenantDomain(tenantDomain);
        user.setUserStoreDomain(domainName);
        return user;
    }

    private void sendNotificationOnEmailUpdate(User user, String previousEmailAddress,
                                               String newEmailAddress) throws IdentityEventException {

        if (StringUtils.isBlank(previousEmailAddress) || previousEmailAddress.equals(newEmailAddress)) {
            return;
        }

        Map<String, String> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.USER_NAME, user.getUserName());
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, user.getTenantDomain());
        properties.put(Constants.EXISTING_EMAIL, previousEmailAddress);
        properties.put(IdentityRecoveryConstants.NEW_EMAIL_ADDRESS, newEmailAddress);
        properties.put(IdentityEventConstants.EventProperty.USER_STORE_DOMAIN, user.getUserStoreDomain());
        String predefinedEmail = getPredefinedEmailAttribute(user);

        // Trigger email to predefined email address.
        if (StringUtils.isNotBlank(predefinedEmail)) {
            triggerNotification(predefinedEmail, user, properties);
        }
    }

    private void triggerNotification(String sendTo, User user, Map<String,
            String> props) throws IdentityEventException {

        if (log.isDebugEnabled()) {
            String msg = String.format("Sending : " + Constants.NOTIFICATION_ON_EXISTING_EMAIL_UPDATE +
                    " notification to user : " + user.toFullQualifiedUsername());
            log.debug(msg);
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityRecoveryConstants.SEND_TO, sendTo);
        properties.put(IdentityRecoveryConstants.TEMPLATE_TYPE, Constants.NOTIFICATION_ON_EXISTING_EMAIL_UPDATE);

        if (CollectionUtils.size(props) > 0) {
            properties.putAll(props);
        }
        Event identityMgtEvent = new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION, properties);
        try {
            IdentityRecoveryServiceDataHolder.getInstance().getIdentityEventService().handleEvent(identityMgtEvent);
        } catch (IdentityEventException e) {
            throw new IdentityEventException("Error while sending notification for user: " +
                    user.toFullQualifiedUsername(), e);
        }
    }

    private String getEmailClaimValue(User user, UserStoreManager userStoreManager) throws IdentityEventException {

        String username = user.getUserName();
        if (StringUtils.isNotBlank(user.getUserStoreDomain())) {
            username = IdentityUtil.addDomainToName(username, user.getUserStoreDomain());
        }
        try {
            return userStoreManager.getUserClaimValue(username,
                    IdentityRecoveryConstants.EMAIL_ADDRESS_CLAIM,
                    null);
        } catch (UserStoreException e) {
            String error = String.format("Error occurred while retrieving existing email address for user: " +
                    "%s in tenant domain : %s.", username, user.getTenantDomain());
            throw new IdentityEventException(error, e);
        }
    }

    public String getPredefinedEmailAttribute(User user) {

        try {
            ConfigurationManager configurationManager = getConfigurationManager();
            Attribute attribute = configurationManager.getAttribute(Constants.RESOURCE_TYPE, Constants.RESOURCE,
                    Constants.ATTRIBUTE_KEY);

            return attribute.getValue();

        } catch (ConfigurationManagementException e) {
            String msg = String.format(user.getUserName(), e.getMessage());
            log.debug(msg);
            return null;
        }
    }

    private static ConfigurationManager getConfigurationManager() {

        ConfigurationManagerConfigurationHolder configurationHolder = new ConfigurationManagerConfigurationHolder();

        ConfigurationDAO configurationDAO = new ConfigurationDAOImpl();
        configurationHolder.setConfigurationDAOS(Collections.singletonList(configurationDAO));

        ConfigurationManager configurationManager = new ConfigurationManagerImpl(configurationHolder);
        return configurationManager;
    }

}
