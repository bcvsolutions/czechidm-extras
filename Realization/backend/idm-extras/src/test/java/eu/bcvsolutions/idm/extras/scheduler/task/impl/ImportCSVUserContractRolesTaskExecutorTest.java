package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFormAttributeFilter;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.test.api.TestHelper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 
 * @author Petr Hanák
 */
public class ImportCSVUserContractRolesTaskExecutorTest extends AbstractCsvImportTaskTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesEav.csv";
	private static final String path1 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesPrime.csv";
	private static final String path2 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesAll.csv";
	private static final String path3 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesParamsSingleValue.csv";
	private static final String path4 = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesParamsSingleValueMultipleAttrs.csv";
	private static final String ROLE_ATTRIBUTE_CODE = "requiredRoleAttribute";
	private static final String ROLE_ATTRIBUTE_CODE2 = "requiredRoleAttribute2";

	@Autowired
	private FormService formService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private TestHelper testHelper;
	@Autowired
	IdmRoleFormAttributeService roleAttributeService;
	
	@Test
	public void assignRolesToEavContractTest() {
		loginAsAdmin();
		
		IdmAttachmentDto attachement = createAttachment(path, "importRolesEav.csv");

		testHelper.createRole("testRole1");
		testHelper.createRole("testRole2");
		testHelper.createRole("testRole3");

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
		testHelper.createIdentityContact(identity);

		formService.saveValues(identityContract1, definitionProject, Arrays.asList(getFormValue(formAttribute.getId(), "2")));
		formService.saveValues(identityContract2, definitionProject, Arrays.asList(getFormValue(formAttribute.getId(), "3")));
		//
		Map<String, Object> config = new HashMap<>();
		config.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachement.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, "eavContractName");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, "eavContractValue");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "eavContract");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_MULTI_VALUE_SEPARATOR, ',');
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
		
		IdmAttachmentDto attachement = createAttachment(path1, "importRolesPrime.csv");
		
		// create Roles
		testHelper.createRole("testMainContractRole1");
		testHelper.createRole("testMainContractRole2");
		testHelper.createRole("testMainContractRole3");

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
		testHelper.createIdentityContact(identity2,position,validStartDate,nonvalidEndDate);

		identityContract1.setMain(true);
		identityContract2.setMain(true);

		Map<String, Object> config = new HashMap<>();
		config.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachement.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_MULTI_VALUE_SEPARATOR, ',');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "primeContract");
		
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
		
		IdmAttachmentDto attachement = createAttachment(path2, "importRolesAll.csv");

		testHelper.createRole("testMainContractRole5");
		testHelper.createRole("testMainContractRole6");
		testHelper.createRole("testMainContractRole7");
		testHelper.createRole("testMainContractRole8");

		// create formAttributes
		IdmIdentityDto identity1 = testHelper.createIdentity("testUserR1");
		IdmIdentityDto identity2 = testHelper.createIdentity("testUserR2");

		IdmTreeNodeDto position = testHelper.createTreeNode();
		LocalDate validStartDate =  LocalDate.of(2019, 1, 8);
		LocalDate futureDate =  LocalDate.now().plusDays(40L);

		testHelper.createIdentityContact(identity1,position,futureDate,null);
		testHelper.createIdentityContact(identity1,position,validStartDate,futureDate);
		testHelper.createIdentityContact(identity2,position,validStartDate,futureDate);

		Map<String, Object> config = new HashMap<>();
		config.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachement.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "allContracts");
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


	@Test
	public void assignRolesToAllContractParamsSingleValueTest() {
		loginAsAdmin();

		IdmAttachmentDto attachement = createAttachment(path3, "importRolesParamsSingleValue.csv");

		IdmRoleDto testMainContractRole9 = createRoleWithAttributes("assignRolesToAllContractParamsSingleValueTestROLE");

		// create formAttributes
		IdmIdentityDto identity1 = testHelper.createIdentity("assignRolesToAllContractParamsSingleValueTestUSER1");
		IdmIdentityDto identity2 = testHelper.createIdentity("assignRolesToAllContractParamsSingleValueTestUSER2");

		testHelper.createIdentityContact(identity1);
		testHelper.createIdentityContact(identity2);

		Map<String, Object> config = new HashMap<>();
		config.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachement.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "allContracts");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_MULTI_VALUE_SEPARATOR, ',');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_IS_ROLE_MULTI_VALUE, false);
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX, "roleattrname");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX, "roleattrvalue");

		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);

		Long count = task.getCount();
		Long total = 2L; //how many contracts affected? 2 createIdentityContact + 2 default contracts
		Assert.assertEquals(total, count);

		//user 1 with primeContract
		// check number of assigned roles to right contracts
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity1.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(2, result.size());

		Assert.assertTrue(hasRoleOnContract(result,testMainContractRole9.getCode()));

		List<IdmIdentityRoleDto> allIRoles9AllContractside1 = result.stream().filter(ir -> ir.getRole().equals(testMainContractRole9.getId())).collect(Collectors.toList());
		allIRoles9AllContractside1
				.forEach(ir -> Assert.assertEquals("val1", getRoleEavValue(ir,
								testMainContractRole9.getIdentityRoleAttributeDefinition(), ROLE_ATTRIBUTE_CODE)));

		logout();
	}

	@Test
	public void assignRolesToAllContractParamsSingleValueMultipleAttrsTest() {
		loginAsAdmin();

		IdmAttachmentDto attachement = createAttachment(path4, "importRolesParamsSingleValueMultipleAttrs.csv");

		IdmRoleDto testMainContractRole9 = createRoleWithAttributes("assignRolesToAllContractParamsSingleValueMultipleAttrsTestROLE");

		// create formAttributes
		IdmIdentityDto identity1 = testHelper.createIdentity("assignRolesToAllContractParamsSingleValueMultipleAttrsTestUSER1");
		IdmIdentityDto identity2 = testHelper.createIdentity("assignRolesToAllContractParamsSingleValueMultipleAttrsTestUSER2");

		testHelper.createIdentityContact(identity1);
		testHelper.createIdentityContact(identity2);

		Map<String, Object> config = new HashMap<>();
		config.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachement.getId());
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME, "username");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, "allContracts");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_MULTI_VALUE_SEPARATOR, ',');
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_IS_ROLE_MULTI_VALUE, false);
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX, "roleattrname");
		config.put(ImportCSVUserContractRolesTaskExecutor.PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX, "roleattrvalue");

		ImportCSVUserContractRolesTaskExecutor userContractRolesLRT = new ImportCSVUserContractRolesTaskExecutor();
		userContractRolesLRT.init(config);
		longRunningTaskManager.executeSync(userContractRolesLRT);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(userContractRolesLRT);

		//user 1 with primeContract
		// check number of assigned roles to right contracts
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity1.getId());
		List<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null).getContent();
		Assert.assertEquals(2, result.size());

		Assert.assertTrue(hasRoleOnContract(result,testMainContractRole9.getCode()));

		List<IdmIdentityRoleDto> allIRoles9AllContractside1 = result.stream().filter(ir -> ir.getRole().equals(testMainContractRole9.getId())).collect(Collectors.toList());
		allIRoles9AllContractside1
				.forEach(ir -> {
					Assert.assertEquals("val1", getRoleEavValue(ir, testMainContractRole9.getIdentityRoleAttributeDefinition(), ROLE_ATTRIBUTE_CODE));
					Assert.assertEquals("val2", getRoleEavValue(ir, testMainContractRole9.getIdentityRoleAttributeDefinition(), ROLE_ATTRIBUTE_CODE2));
				});

		logout();
	}

	private IdmRoleDto createRoleWithAttributes(String roleName) {
		IdmRoleDto role = getHelper().createRole(roleName);
		assertNull(role.getIdentityRoleAttributeDefinition());

		IdmFormAttributeDto roleAttribute = new IdmFormAttributeDto(ROLE_ATTRIBUTE_CODE);
		roleAttribute.setPersistentType(PersistentType.SHORTTEXT);
		roleAttribute.setRequired(false);
		roleAttribute.setDefaultValue(getHelper().createName());

		IdmFormAttributeDto roleAttribute2 = new IdmFormAttributeDto(ROLE_ATTRIBUTE_CODE2);
		roleAttribute.setPersistentType(PersistentType.SHORTTEXT);
		roleAttribute.setRequired(false);
		roleAttribute.setDefaultValue(getHelper().createName());

		IdmFormDefinitionDto definition = formService.createDefinition(IdmIdentityRole.class, getHelper().createName(),
				ImmutableList.of(roleAttribute, roleAttribute2));
		role.setIdentityRoleAttributeDefinition(definition.getId());
		role = roleService.save(role);
		assertNotNull(role.getIdentityRoleAttributeDefinition());
		IdmRoleDto roleFinal = role;
		definition.getFormAttributes().forEach(attribute -> {
			roleAttributeService.addAttributeToSubdefintion(roleFinal, attribute);
		});

		return role;
	}

	private String getRoleEavValue(IdmIdentityRoleDto ir, UUID formDefinition, String attrName) {

		IdmFormInstanceDto idmFormInstanceDto = formService.getFormInstance(ir, formService.getDefinition(formDefinition));

		if (idmFormInstanceDto == null) {
			return null;
		}

		IdmFormAttributeDto mappedAttributeByCode = idmFormInstanceDto.getMappedAttributeByCode(attrName);

		return idmFormInstanceDto.getValues().stream()
				.filter(fv -> fv.getFormAttribute().equals(mappedAttributeByCode.getId()))
				.map(IdmFormValueDto::getShortTextValue)
				.findFirst()
				.orElse(null);
	}

	private IdmFormAttributeDto createFormAttribute(String code, UUID formDefinition) {
		IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
		formAttributeDto.setCode(code);
		formAttributeDto.setName(code);
		formAttributeDto.setFormDefinition(formDefinition);
		formAttributeDto.setPersistentType(PersistentType.SHORTTEXT);
		return formAttributeService.save(formAttributeDto);
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