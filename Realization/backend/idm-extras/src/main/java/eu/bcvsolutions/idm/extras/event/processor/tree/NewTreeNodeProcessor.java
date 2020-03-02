package eu.bcvsolutions.idm.extras.event.processor.tree;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;

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
			IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + "treeNodeRecipient";

	@Autowired
	private NotificationManager notificationManager;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private ConfigurationService configurationService;

	public NewTreeNodeProcessor() {
		super(TreeNodeEvent.TreeNodeEventType.CREATE);
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
		if (roleName == null) {
			return new DefaultEventResult<>(event, this);
		}
		List<IdmIdentityDto> recipients = identityService.findAllByRoleName(roleName);
		//
		if (treeNode != null) {
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
}
