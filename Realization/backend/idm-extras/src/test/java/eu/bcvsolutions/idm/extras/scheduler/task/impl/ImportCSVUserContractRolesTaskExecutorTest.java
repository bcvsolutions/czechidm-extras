package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.extras.utils.Pair;
import eu.bcvsolutions.idm.test.api.TestHelper;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;

/**
 * 
 * @author Petr Hanák
 */
public class ImportCSVUserContractRolesTaskExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importContractEavRolesTestFile.csv";
	private static final String path1 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importContractEavRolesTestFilePrimeContract.csv";
	private static final String path2 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importContractEavRolesTestFileAllContractsMultiValue.csv";
	
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
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private TestHelper testHelper;
	
	@Test
	public void assignRolesToEavContractTest() {
		loginAsAdmin();
		setPath(path, "importContractEavRolesTestFile.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
//		Map<String, Object> configOfLRT = pair.getSecond();
//
//		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
//		lrt.init(configOfLRT);
//		// import roles
//		longRunningTaskManager.executeSync(lrt);

		IdmRoleDto roleTest1 = testHelper.createRole("testRole1");
		IdmRoleDto roleTest2 = testHelper.createRole("testRole2");
		IdmRoleDto roleTest3 = testHelper.createRole("testRole3");


		// create formAttributes
		String contractEav = "eavContract";
		String contractDefinition = "defContract";
		IdmFormDefinitionDto definitionProject = testHelper.createFormDefinition("eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract");
		definitionProject.setCode(contractDefinition);
		formDefinitionService.save(definitionProject);

		IdmFormAttributeDto formAttribute = createFormAttribute(contractEav, definitionProject.getId());

		IdmIdentityDto identity = testHelper.createIdentity("user321");
		IdmIdentityContractDto identityContract1 = testHelper.createIdentityContact(identity);
		IdmIdentityContractDto identityContract2 = testHelper.createIdentityContact(identity);
		IdmIdentityContractDto identityContract3 = testHelper.createIdentityContact(identity);

		formService.saveValues(identityContract1, definitionProject, Arrays.asList(getFormValue(formAttribute.getId(), "2")));
		formService.saveValues(identityContract2, definitionProject, Arrays.asList(getFormValue(formAttribute.getId(), "3")));
		//
		Map<String, Object> config = new HashMap<>();
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_COLUMN_NAME, "eavContract");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_NAME, contractEav);
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_COLUMN_SEPARATOR, ';');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "eavContract");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_FILE_ENCODING, "UTF-8");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_DEFINITION_CODE, "defContract");

		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);
		// there is one count for one contract eav value		
		Long count = task.getCount();
		Long total = 2L;
		Assert.assertEquals(total, count);

		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(3, result.size());

		Assert.assertTrue(hasRoleOnContract(result,"testRole1"));
		Assert.assertTrue(hasRoleOnContract(result,"testRole2"));
		Assert.assertTrue(hasRoleOnContract(result,"testRole3"));
	}


	@Test
	public void assignRolesToPrimeContractTest() {
		loginAsAdmin();
		setPath(path1, "importContractEavRolesTestFilePrimeContract.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
//		Map<String, Object> configOfLRT = pair.getSecond();
//
//		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
//		lrt.init(configOfLRT);
//		// import roles
//		longRunningTaskManager.executeSync(lrt);

		// create Roles
		IdmRoleDto roleTest1 = testHelper.createRole("testMainContractRole1");
		IdmRoleDto roleTest2 = testHelper.createRole("testMainContractRole2");
		IdmRoleDto roleTest3 = testHelper.createRole("testMainContractRole3");

		// create formAttributes
		IdmIdentityDto identity1 = testHelper.createIdentity("testUserE1");
		IdmIdentityDto identity2 = testHelper.createIdentity("testUserE2");
//		IdmIdentityContractDto identityContract1 = createContract(identity.getId());

		IdmTreeNodeDto position = testHelper.createTreeNode();
		LocalDate validStartDate =  LocalDate.of(2019, 1, 8);
		LocalDate validEndDate =  LocalDate.of(2020, 10, 10);
		LocalDate nonvalidEndDate =  LocalDate.of(2020, 1, 1);

		IdmIdentityContractDto identityContract1 = testHelper.createIdentityContact(identity1,position,validStartDate, null );
		IdmIdentityContractDto identityContract2 = testHelper.createIdentityContact(identity1,position,validStartDate,validEndDate);
		IdmIdentityContractDto identityContract3 = testHelper.createIdentityContact(identity2,position,validStartDate,nonvalidEndDate);

		identityContract1.setMain(true);
		identityContract2.setMain(true);

		Map<String, Object> config = new HashMap<>();
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_COLUMN_NAME, "cicin");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_COLUMN_SEPARATOR, ';');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "primeContract");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_FILE_ENCODING, "UTF-8");

		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);
		// there is one count for one contract eav value
		Long count = task.getCount();
		Long total = 2L; // 2 defautl contracts
		Assert.assertEquals(total, count);

		//user 1 with primeContract
		// check number of assigned roles to right contracts
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity1.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(2, result.size());

		Assert.assertTrue(hasRoleOnContract(result,"testMainContractRole1"));
		Assert.assertTrue(hasRoleOnContract(result,"testMainContractRole2"));


		//user 1 with primeContract
		// check number of assigned roles to right contracts
		identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity2.getId());
		List<IdmIdentityRoleDto> result1 = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(2, result1.size());

		Assert.assertTrue(hasRoleOnContract(result1,"testMainContractRole3"));
		Assert.assertTrue(hasRoleOnContract(result1,"testMainContractRole2"));
		logout();
	}


	@Test
	public void assignRolesToAllContractTest() {
		loginAsAdmin();
		setPath(path2, "importContractEavRolesTestFileAllContractsMultiValue.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
//		Map<String, Object> configOfLRT = pair.getSecond();
//
//		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
//		lrt.init(configOfLRT);
//		// import roles
//		longRunningTaskManager.executeSync(lrt);

		IdmRoleDto roleTest1 = testHelper.createRole("testMainContractRole5");
		IdmRoleDto roleTest2 = testHelper.createRole("testMainContractRole6");
		IdmRoleDto roleTest4 = testHelper.createRole("testMainContractRole7");
		IdmRoleDto roleTest5 = testHelper.createRole("testMainContractRole8");



		// create formAttributes
		IdmIdentityDto identity1 = testHelper.createIdentity("testUserR1");
		IdmIdentityDto identity2 = testHelper.createIdentity("testUserR2");
//		IdmIdentityContractDto identityContract1 = createContract(identity.getId());

		IdmTreeNodeDto position = testHelper.createTreeNode();
		LocalDate validStartDate =  LocalDate.of(2019, 1, 8);
		LocalDate validEndDate =  LocalDate.of(2020, 5, 5);
		LocalDate nonvalidEndDate =  LocalDate.of(2020, 1, 1);
		LocalDate futureDate =  LocalDate.of(2020, 12, 12);


//		IdmIdentityContractDto identityContract1 = testHelper.createIdentityContact(identity1,position,validStartDate, nonvalidEndDate );
		IdmIdentityContractDto identityContract2 = testHelper.createIdentityContact(identity1,position,futureDate,null);
		IdmIdentityContractDto identityContract3 = testHelper.createIdentityContact(identity1,position,validStartDate,futureDate);
		IdmIdentityContractDto identityContract4 = testHelper.createIdentityContact(identity2,position,validStartDate,futureDate);

		Map<String, Object> config = new HashMap<>();
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_COLUMN_NAME, "cicin");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_COLUMN_SEPARATOR, ';');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "allContracts");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_FILE_ENCODING, "UTF-8");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_MULTI_VALUE_SEPARATOR, ',');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_IS_ROLE_MULTI_VALUE, true);

		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);

		Long count = task.getCount();
		Long total = 5L; //how many contracts affected? 3 createIdentityContact + 2 default contracts
		Assert.assertEquals(total, count);

		//user 1 with primeContract
		// check number of assigned roles to right contracts
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity1.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(6, result.size());


		Assert.assertTrue(hasRoleOnContract(result,"testMainContractRole5"));
		Assert.assertTrue(hasRoleOnContract(result,"testMainContractRole6"));


		identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity2.getId());
		List<IdmIdentityRoleDto> result1 = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(4, result1.size());

		Assert.assertTrue(hasRoleOnContract(result1,"testMainContractRole7"));
		Assert.assertTrue(hasRoleOnContract(result1,"testMainContractRole8"));

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

	private boolean hasRoleOnContract(List<IdmIdentityRoleDto> roles, String roleName) {
		return roles.stream().anyMatch(idmIdentityRoleDto ->
				DtoUtils.getEmbedded(idmIdentityRoleDto, IdmIdentityRole_.role, IdmRoleDto.class).getCode().equals(roleName));
	}

}
