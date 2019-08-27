package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
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

public class ImportFromCSVUserContractRolesTaskExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/contractEavRolesTestFile.csv";
    
	@Autowired
	private FormService formService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	
	@Test
	public void importRolesTest() {
		setPath(path, "importContractEavRolesTestFile.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
//		SysSystemDto system = pair.getFirst();
		Map<String, Object> configOfLRT = pair.getSecond();

		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
		lrt.init(configOfLRT);
//		import roles Manager, MASTER RO, PAY-BLL
		longRunningTaskManager.executeSync(lrt);
		// create formAttributes
		String contractEav = "cicin";
		IdmFormDefinitionDto definition = formService.getDefinition(formService.getDefaultDefinitionType(IdmIdentityContractDto.class));
		createFormAttribute(contractEav, definition.getId());
//		TODO create identity with contract and filled eav -> cicin = 2
//		IdmIdentityDto identityDto = createIdentity();
//		IdmIdentityContractDto identityContractDto = createContract();
		
		//
		Map<String, Object> config = new HashMap<>();
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_COLUMN_NAME, "cicin");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_NAME, contractEav);
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_COLUMN_SEPARATOR, ';');
		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);
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
