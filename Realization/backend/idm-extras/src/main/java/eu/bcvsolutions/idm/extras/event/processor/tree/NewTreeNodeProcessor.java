package eu.bcvsolutions.idm.extras.event.processor.tree;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * After creation of new tree node sends notification to authority
 *
 * @author Marek Klement
 */
@Component
@Description("After creation of new tree node sends notification to authority")
public class NewTreeNodeProcessor extends AbstractEntityEventProcessor<IdmTreeNodeDto> {

	public static final String PROCESSOR_NAME = "new-node-processor";
	public static final String TREE_NODE_CREATE_ROLE =
			IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID +
					"treeNodeRecipient";

	private final NotificationManager notificationManager;
	private final IdmIdentityService identityService;

	@Autowired
	ExtrasConfiguration extrasConfiguration;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmNotificationConfigurationService notificationConfigurationService;
	@Autowired
	private IdmNotificationTemplateService notificationTemplateService;

	@Autowired
	public NewTreeNodeProcessor(NotificationManager notificationManager,
								IdmIdentityService identityService, IdmConfigurationService configurationService) {
		super(TreeNodeEvent.TreeNodeEventType.CREATE);
		//
		Assert.notNull(notificationManager);
		Assert.notNull(identityService);
		Assert.notNull(configurationService);
		//
		this.identityService = identityService;
		this.notificationManager = notificationManager;
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmTreeNodeDto> process(EntityEvent<IdmTreeNodeDto> event) {
		IdmTreeNodeDto treeNode = event.getContent();
		//
		String roleName = configurationService.getValue(TREE_NODE_CREATE_ROLE);
		List<IdmIdentityDto> recipients = identityService.findAllByRoleName(roleName);
		//
		if (treeNode != null && !recipients.isEmpty()) {
			NotificationConfigurationDto config =
					notificationConfigurationService
							.getConfigurationByTopicLevelNotificationType(
									ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE,
									NotificationLevel.SUCCESS,
									IdmEmailLog.NOTIFICATION_TYPE);
			if (config == null) {
				createNotificationConfiguration();
			}
			notificationManager.send(
					ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE,
					new IdmMessageDto.Builder()
							.setLevel(NotificationLevel.SUCCESS)
							.addParameter("treeNodeName", treeNode.getName())
							.addParameter("treeNodeCode", treeNode.getCode())
							.addParameter("created", treeNode.getCreated())
							.addParameter("uid", treeNode.getId())
							.build(),
					recipients);
		}
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public int getOrder() {
		// after create
		return 1000;
	}

	private void createNotificationConfiguration() {
		NotificationConfigurationDto notificationConfiguration = new NotificationConfigurationDto();
		notificationConfiguration.setTopic(ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE);
		notificationConfiguration.setNotificationType(IdmEmailLog.NOTIFICATION_TYPE);
		notificationConfiguration.setLevel(NotificationLevel.SUCCESS);

		//get notification template
		IdmNotificationTemplateDto idmNotificationTemplateDto =
				notificationTemplateService.getByCode(ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE);
		if (idmNotificationTemplateDto == null) {
			throw new ResultCodeException(ExtrasResultCode.TEMPLATE_NOT_FOUND,
					ImmutableMap.of("code", ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE));
		}
		notificationConfiguration.setTemplate(idmNotificationTemplateDto.getId());
		notificationConfigurationService.save(notificationConfiguration);
	}
}
