package eu.bcvsolutions.idm.extras.event.processor.tree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.dto.filter.IdmNotificationFilter;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationLogService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class NewTreeNodeProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private TestHelper testHelper;
	@Autowired
	private IdmNotificationLogService notificationLogService;
	@Autowired
	private IdmNotificationTemplateService notificationTemplateService;


	@Test
	public void createTestAndSendNotification() {
		String roleName = "TestRoleNameNotify";

		configurationService.setValue(NewTreeNodeProcessor.TREE_NODE_CREATE_ROLE, roleName);

		// TODO set config for template

		IdmRoleDto role = testHelper.createRole(roleName);
		IdmIdentityDto identityDto = testHelper.createIdentity("TestIdentityNotify");
		testHelper.createIdentityRole(identityDto, role);

		//test send
		testHelper.createTreeNode();

		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identityDto.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		long count = notificationLogService.count(filter);

		assertEquals(1, count);
	}

	@Test
	public void createTestAndException() {
		String roleName = "TestRoleNameNotifyExc";

		configurationService.setValue(NewTreeNodeProcessor.TREE_NODE_CREATE_ROLE, roleName);

		// TODO set config for template

		IdmRoleDto role = testHelper.createRole(roleName);
		IdmIdentityDto identityDto = testHelper.createIdentity("TestIdentityNotifyExc");
		testHelper.createIdentityRole(identityDto, role);

		IdmNotificationTemplateDto template =
				notificationTemplateService.getByCode(ExtrasModuleDescriptor.TOPIC_NEW_TREE_NODE);

		assertNotNull(template);
		String code = template.getCode();
		template.setCode("TestCode0001");
		notificationTemplateService.save(template);

		//test send
		Exception ex = null;
		try {
			testHelper.createTreeNode();
		} catch (Exception e){
			ex = e;
		}
		assertNotNull(ex);

		template.setCode(code);
		notificationTemplateService.save(template);
	}

}