package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;

/**
 * @author Roman Kucera
 */
public class ImportAutomaticRoleByAttributesCSVExecutorTest extends AbstractRoleExecutorTest {
	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importAutoRole.csv";

	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;

	@Test
	@Transactional
	public void importAutoRolesDefinitions() {
		setPath(path, "importAutoRole.csv");
		IdmAttachmentDto attachment = createAttachment();

		helper.createRole("role");
		helper.createEavAttribute("eav1", IdmIdentity.class, PersistentType.SHORTTEXT);
		helper.createEavAttribute("eav2", IdmIdentityContract.class, PersistentType.SHORTTEXT);

		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CSV_ATTACHMENT_ENCODING, "utf-8");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_ROLES_COLUMN_NAME, "role");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_DEFINITION_NAME_COLUMN_NAME, "name");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_COLUMN_SEPARATOR, ";");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_IDENTITY_ATTR_NAME_PREFIX, "identityAttrName");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_IDENTITY_ATTR_VALUE_PREFIX, "identityAttrValue");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX, "identityEavAttrName");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX, "identityEavAttrValue");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CONTRACT_ATTR_NAME_PREFIX, "contractAttrName");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CONTRACT_ATTR_VALUE_PREFIX, "contractAttrValue");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, "contractEavAttrName");
		configOfLRT.put(ImportAutomaticRoleByAttributesCSVExecutor.PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, "contractEavAttrValue");

		ImportAutomaticRoleByAttributesCSVExecutor lrt = new ImportAutomaticRoleByAttributesCSVExecutor();
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Long count = task.getCount();
		Long total = 3L;
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		// check if definitions are created with rules
		checkDefinition("5 rules", 5);
		checkDefinition("username", 1);
		checkDefinition("officer", 1);
	}

	private void checkDefinition(String name, int numberOfRules) {
		IdmAutomaticRoleFilter automaticRoleFilter = new IdmAutomaticRoleFilter();
		automaticRoleFilter.setName(name);
		List<IdmAutomaticRoleAttributeDto> automaticRoles = automaticRoleAttributeService.find(automaticRoleFilter, null).getContent();
		assertEquals(1 ,automaticRoles.size());

		List<IdmAutomaticRoleAttributeRuleDto> allRulesForAutomaticRole = automaticRoleAttributeRuleService.findAllRulesForAutomaticRole(automaticRoles.get(0).getId());
		assertEquals(numberOfRules, allRulesForAutomaticRole.size());
	}

}