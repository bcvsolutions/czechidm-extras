package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.extras.utils.Pair;

public class ImportAutomaticRoleAttributesFromCSVExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile01.csv";

	@Autowired
	private FormService formService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;

	@Test
	public void importAutomaticRoles() {
		setPath(path, "importRolesTestFile01.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		SysSystemDto system = pair.getFirst();
		Map<String, Object> configOfLRT = pair.getSecond();

		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		// create formAttributes
		String eavNameNode = "nodeAttribute";
		String eavTreeName = "treeAttribute";
		IdmFormDefinitionDto definition = formService.getDefinition(formService.getDefaultDefinitionType(IdmIdentityContractDto.class));
		createFormAttribute(eavNameNode, definition.getId());
		createFormAttribute(eavTreeName, definition.getId());
		//
		Map<String, Object> config = new HashMap<>();
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_ROLES_COLUMN_NAME, ROLE_ROW);
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_COLUMN_SEPARATOR, ';');
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_CSV_ATTACHMENT_ENCODING, "utf-8");
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_COLUMN_FIRST_ATTRIBUTE, "position");
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_FIRST_CONTRACT_EAV_NAME, eavNameNode);
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_COLUMN_SECOND_ATTRIBUTE, "organizational unit");
		config.put(ImportAutomaticRoleAttributesFromCSVExecutor.PARAM_SECOND_CONTRACT_EAV_NAME, eavTreeName);
		ImportAutomaticRoleAttributesFromCSVExecutor automaticRolesLRT = new ImportAutomaticRoleAttributesFromCSVExecutor();
		automaticRolesLRT.init(config);
		longRunningTaskManager.executeSync(automaticRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(automaticRolesLRT);
		Long count = task.getCount();
		Long total = 8L;
		Long counterTotal = 9L;
		Assert.assertEquals(total, task.getCounter());
		Assert.assertEquals(counterTotal, count);
		// check automatic roles settings
		List<IdmRoleDto> roles = roleService.find(new IdmRoleFilter(), null).getContent();
		List<IdmRoleDto> idmRoleDtoStream = roles.stream().filter(r -> r.getName().equals(CHECK_NAME)).collect(Collectors.toList());
		Assert.assertFalse(idmRoleDtoStream.isEmpty());
		IdmAutomaticRoleFilter automaticRoleFilter = new IdmAutomaticRoleFilter();
		automaticRoleFilter.setRoleId(idmRoleDtoStream.get(0).getId());
		List<IdmAutomaticRoleAttributeDto> idmAutomaticRoleAttributeDtos = automaticRoleAttributeService.find(automaticRoleFilter, null).getContent();
		Assert.assertFalse(idmAutomaticRoleAttributeDtos.isEmpty());
		List<IdmAutomaticRoleAttributeRuleDto> allRulesForAutomaticRole = automaticRoleAttributeRuleService.findAllRulesForAutomaticRole(idmAutomaticRoleAttributeDtos.get(0).getId());
		Assert.assertEquals(2, allRulesForAutomaticRole.size());

	}

	private void createFormAttribute(String code, UUID formDefinition) {
		IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
		formAttributeDto.setCode(code);
		formAttributeDto.setName(code);
		formAttributeDto.setFormDefinition(formDefinition);
		formAttributeDto.setPersistentType(PersistentType.SHORTTEXT);
		formAttributeDto.setMultiple(true);
		formAttributeService.save(formAttributeDto);
	}
}