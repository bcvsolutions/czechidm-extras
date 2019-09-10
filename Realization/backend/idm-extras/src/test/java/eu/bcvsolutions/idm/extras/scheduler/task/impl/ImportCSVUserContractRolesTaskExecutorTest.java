package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.extras.utils.Pair;

/**
 * 
 * @author Petr Hanák 
 */
public class ImportCSVUserContractRolesTaskExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importContractEavRolesTestFile.csv";
	
	@Autowired
	private FormService formService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	
	@Test
	public void assignRolesToContractTest() {
		loginAsAdmin();
		setPath(path, "importContractEavRolesTestFile.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		Map<String, Object> configOfLRT = pair.getSecond();

		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
		lrt.init(configOfLRT);
		// import roles
		longRunningTaskManager.executeSync(lrt);
		// create formAttributes
		String contractEav = "cicin";
		IdmFormDefinitionDto definition = formService.getDefinition(formService.getDefaultDefinitionType(IdmIdentityContractDto.class));
		IdmFormAttributeDto formAttribute = createFormAttribute(contractEav, definition.getId());
		IdmIdentityDto identity = createIdentity("user321");
		IdmIdentityContractDto identityContract1 = createContract(identity.getId());
		IdmIdentityContractDto identityContract2 = createContract(identity.getId());
		// set eav value for contract
		formService.saveValues(identityContract1, definition, Arrays.asList(getFormValue(formAttribute.getId(), "2")));
		formService.saveValues(identityContract2, definition, Arrays.asList(getFormValue(formAttribute.getId(), "3")));
		
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
		// there is one count for one contract eav value		
		Long count = task.getCount();
		Long total = 2L;
		Assert.assertEquals(total, count);
		
		// check number of assigned roles to right contracts
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(3, result.size());
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setCodeableIdentifier("testRole1");
		
		IdmRoleDto role = roleService.find(roleFilter, null).getContent().get(0);
		identityRoleFilter.setRoleId(role.getId());
		List<IdmIdentityRoleDto> result3 = identityRoleService.find(identityRoleFilter, null).getContent();
		IdmIdentityRoleDto identityRole2 = result3.get(0);
		Assert.assertEquals(identityContract1.getId(), identityRole2.getIdentityContract());
		logout();
	}

	private IdmFormAttributeDto createFormAttribute(String code, UUID formDefinition) {
		IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
		formAttributeDto.setCode(code);
		formAttributeDto.setName(code);
		formAttributeDto.setFormDefinition(formDefinition);
		formAttributeDto.setPersistentType(PersistentType.SHORTTEXT);
		return formAttributeService.save(formAttributeDto);
	}
	
	private IdmIdentityDto createIdentity(String username) {
		IdmIdentityDto identity = new IdmIdentityDto();
		identity.setUsername(username);
		return identityService.save(identity);
	}
	
	private IdmIdentityContractDto createContract(UUID identity) {
		IdmIdentityContractDto identityContract = new IdmIdentityContractDto();
		identityContract.setIdentity(identity);
		return identityContractService.save(identityContract);
	}
	
	private IdmFormValueDto getFormValue(UUID formAttribute, String value) {
		IdmFormValueDto formValue = new IdmFormValueDto();
		formValue.setFormAttribute(formAttribute);
		formValue.setShortTextValue(value);
		return formValue;
	}
}
