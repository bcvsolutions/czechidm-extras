package eu.bcvsolutions.idm.extras.event.processor.tree;

import static org.junit.Assert.assertEquals;

import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.api.dto.filter.IdmNotificationFilter;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationLogService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class NewTreeNodeProcessorTest extends AbstractIntegrationTest {

	private final static String PROCESSOR_ENABLED = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID +".processor.new-node-processor.enabled";

	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private TestHelper testHelper;
	@Autowired
	private IdmNotificationLogService notificationLogService;
	@Autowired
	private IdmNotificationConfigurationService notificationConfigurationService;
	@Autowired
	private IdmNotificationTemplateService notificationTemplateService;

	@Before
	public void init(){
		loginAsAdmin();
		configurationService.setValue(PROCESSOR_ENABLED, "true");
	}

	@After
	public void clean(){
		configurationService.setValue(PROCESSOR_ENABLED, "false");
		logout();
	}

	@Test
	public void createTestAndSendNotification() {
		String roleName = "TestRoleNameNotify";

		configurationService.setValue(NewTreeNodeProcessor.TREE_NODE_CREATE_ROLE, roleName);

		IdmRoleDto role = testHelper.createRole(roleName);
		IdmIdentityDto identityDto = testHelper.createIdentity("TestIdentityNotify");
		testHelper.createIdentityRole(identityDto, role);

		createNotificationConfiguration();

		//test send
		testHelper.createTreeNode();

		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identityDto.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		long count = notificationLogService.count(filter);

		assertEquals(1, count);
	}

	@Test
	public void createTestAndNotSendNotification() {
		String roleName = "TestRoleNameNotify002";

		IdmRoleDto role = testHelper.createRole(roleName);
		IdmIdentityDto identityDto = testHelper.createIdentity("TestIdentityNotify002");
		testHelper.createIdentityRole(identityDto, role);

		createNotificationConfiguration();

		//test send
		testHelper.createTreeNode();

		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identityDto.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		long count = notificationLogService.count(filter);

		assertEquals(0, count);
	}

	private void createNotificationConfiguration() {
		NotificationConfigurationDto notificationConfiguration =
				notificationConfigurationService.getConfigurationByTopicLevelNotificationType(
						ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE,
						NotificationLevel.SUCCESS,
						IdmEmailLog.NOTIFICATION_TYPE);
		if (notificationConfiguration == null) {
			notificationConfiguration = new NotificationConfigurationDto();
			notificationConfiguration.setTopic(ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE);
			notificationConfiguration.setNotificationType(IdmEmailLog.NOTIFICATION_TYPE);
			notificationConfiguration.setLevel(NotificationLevel.SUCCESS);

			//get notification template
			IdmNotificationTemplateDto idmNotificationTemplateDto =
					notificationTemplateService.getByCode(ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE);
			if (idmNotificationTemplateDto == null) {
				throw new IllegalArgumentException("No template found!");
			}
			notificationConfiguration.setTemplate(idmNotificationTemplateDto.getId());
			notificationConfigurationService.save(notificationConfiguration);
		}
	}

}