package eu.bcvsolutions.idm.extras;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.PropertyModuleDescriptor;
import eu.bcvsolutions.idm.core.api.domain.ResultCode;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.extras.domain.ExtrasGroupPermission;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * Extras module descriptor
 * 
 * @author peter.sourek@bcvsolutions.eu
 */
@Component
@PropertySource("classpath:module-" + ExtrasModuleDescriptor.MODULE_ID + ".properties")
@ConfigurationProperties(prefix = "module." + ExtrasModuleDescriptor.MODULE_ID + ".build", ignoreUnknownFields = true, ignoreInvalidFields = true)
public class ExtrasModuleDescriptor extends PropertyModuleDescriptor {

	public static final String MODULE_ID = "extras";
	public static final String TOPIC_STATUS = String.format("%s:status", MODULE_ID);

	@Autowired
	private IdmNotificationTemplateService notificationTemplateService;


	@Override
	public String getId() {
		return MODULE_ID;
	}
	
	/**
	 * Enables links to swagger documention
	 */
	@Override
	public boolean isDocumentationAvailable() {
		return true;
	}

	@Override
	public List<NotificationConfigurationDto> getDefaultNotificationConfigurations() {
		List<NotificationConfigurationDto> configs = new ArrayList<>();

		IdmNotificationTemplateDto templateDto = notificationTemplateService.getByCode("statusNotification");
		configs.add(new NotificationConfigurationDto(
				TOPIC_STATUS,
				NotificationLevel.INFO,
				IdmEmailLog.NOTIFICATION_TYPE,
				"Status notification",
				templateDto != null ? templateDto.getId() : null));
		return configs;
	}


	@Override
	public List<GroupPermission> getPermissions() {
		return Arrays.asList(ExtrasGroupPermission.values());
	}

	@Override
	public List<ResultCode> getResultCodes() {
		return Arrays.asList(ExtrasResultCode.values());
	}
}
