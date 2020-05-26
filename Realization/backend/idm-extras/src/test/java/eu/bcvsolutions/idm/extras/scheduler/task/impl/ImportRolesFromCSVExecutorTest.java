package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleFormAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFormAttributeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleFormAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.extras.utils.Pair;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class ImportRolesFromCSVExecutorTest extends AbstractRoleExecutorTest {

	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile04.csv";
	private static final String PATH_TWO = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile05.csv";
	private static final String PATH_THREE = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile06.csv";

	
	private static final String ATTRIBUTE = "attr1";
	private static final String ATTRIBUTE_TWO = "attr2";
	private static final int CRITICALITY = 3;
	private static final int CRITICALITY_TWO = 2;
	private static final String GUARANTEE = "uzivatel1";
	private static final String GUARANTEE_TWO = "uzivatel2";
	private static final String GUARANTEE_ROLE = "role1";
	private static final String GUARANTEE_ROLE_TWO = "role2";
	private static final String SUB_ROLE = "subrole";
	private static final String SUB_ROLE_TWO = "subrole2";
	private static final String SUB_ROLE_COLUMN = "subroles";
	private static final String ENVIRONMENT = "testing env";
	private static final String GUARANTEE_TYPE_COLUMN = "guaranteeType";
	private static final String GUARANTEE_ROLE_TYPE_COLUMN = "guaranteeRoleType";
	private static final String GUARANTEE_TYPE = "type1";
	
	@Autowired
	private IdmRoleFormAttributeService roleFormAttributeService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
    @Autowired
    private TestHelper testHelper;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private IdmRoleGuaranteeRoleService roleGuaranteeRoleService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleCompositionService roleCompositionService;
	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	
	@Test
	public void importRolesTest() {
		setPath(PATH, "importRolesTestFile04.csv");
		CHECK_NAME = "CORE-CLOSE";
		
		// create guarantee type
		IdmCodeListDto codeList = new IdmCodeListDto(UUID.randomUUID());
		codeList.setCode("guarantee-type");
		codeList.setName("guarantee-type");
		codeListService.save(codeList);
		
		IdmCodeListItemDto codeListItem = new IdmCodeListItemDto();
		codeListItem.setCode(GUARANTEE_TYPE);
		codeListItem.setName(GUARANTEE_TYPE);
		codeListItem.setCodeList(codeList.getId());
		codeListItemService.save(codeListItem);
		
		// create system
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		SysSystemDto system = pair.getFirst();
		
		// creates identity
		IdmIdentityDto identity = testHelper.createIdentity(GUARANTEE);
        testHelper.createIdentityContact(identity);
		
        // create subrole
        testHelper.createRole(SUB_ROLE);
        
		// create role
		IdmRoleDto roleToAssing = new IdmRoleDto();
		roleToAssing.setCode(GUARANTEE_ROLE);
		roleToAssing.setName(GUARANTEE_ROLE);
		
		roleToAssing = roleService.save(roleToAssing);
		//
		Map<String, Object> configOfLRT = addToCongig(pair.getSecond());
		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
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
		Long total = 4L;
		Assert.assertEquals(task.getCounter(), count);
		Assert.assertEquals(total, count);
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setSystemId(system.getId());
		List<SysRoleSystemDto> content = roleSystemService.find(filter, null).getContent();
		Assert.assertEquals(4, content.size());
		
		LinkedList<IdmRoleDto> roles = new LinkedList<>();
		content.forEach(r -> roles.add(roleService.get(r.getRole())));
		boolean contains = false;
		IdmRoleDto ourRole = null;
		for (IdmRoleDto role : roles) {
			if (filterContains(role)) {
				ourRole = role;
				contains = true;
				break;
			}
		}
		assertTrue(contains);
		assertNotNull(ourRole);
		// test for adding catalogues
		List<IdmRoleCatalogueDto> allByRole = roleCatalogueService.findAllByRole(ourRole.getId());
		assertEquals(2, allByRole.size());
		assertEquals("cat1", allByRole.get(0).getName());
		assertEquals("cat3", allByRole.get(1).getName());
		
		// test for adding attributes
		IdmRoleFormAttributeFilter roleFormAttributeFilter = new IdmRoleFormAttributeFilter();
		roleFormAttributeFilter.setRole(ourRole.getId());
		List<IdmRoleFormAttributeDto> allRolesParams = roleFormAttributeService.find(roleFormAttributeFilter, null).getContent();
		
		IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
		for(IdmRoleFormAttributeDto p : allRolesParams) {
			formAttributeDto = formAttributeService.get(p.getFormAttribute());
		}
		
		Assert.assertEquals(ATTRIBUTE, formAttributeDto.getName());
		
		// test for setting criticality
		Assert.assertEquals(CRITICALITY, ourRole.getPriority());
		
		// test for setting guarantee, finds the guarantee of the role
		IdmRoleGuaranteeFilter filterGuaranteeRole = new IdmRoleGuaranteeFilter();
		filterGuaranteeRole.setRole(ourRole.getId());
		List<IdmRoleGuaranteeDto> garantLinks = roleGuaranteeService.find(filterGuaranteeRole, null).getContent();
		IdmRoleGuaranteeDto garantLink = garantLinks.get(0);
		IdmIdentityDto garant = identityService.get(garantLink.getGuarantee());
		String guaranteeType = garantLink.getType();

		Assert.assertEquals(GUARANTEE, garant.getUsername());
		Assert.assertEquals(GUARANTEE_TYPE, guaranteeType);
		
		// test for setting guarantee role
		IdmRoleGuaranteeRoleFilter filterRoleGuaranteeRole = new IdmRoleGuaranteeRoleFilter();
		filterRoleGuaranteeRole.setRole(ourRole.getId());
		List<IdmRoleGuaranteeRoleDto> garantRoleLinks = roleGuaranteeRoleService.find(filterRoleGuaranteeRole, null).getContent();
		IdmRoleGuaranteeRoleDto garantRoleLink = garantRoleLinks.get(0);
		IdmRoleDto garantRole = roleService.get(garantRoleLink.getGuaranteeRole());
		String guaranteeRoleType = garantRoleLink.getType();
		
		Assert.assertEquals(GUARANTEE_ROLE, garantRole.getCode());
		Assert.assertEquals(GUARANTEE_TYPE, guaranteeRoleType);

		// test for assigning sub roles
		List<IdmRoleCompositionDto> subRoles = roleCompositionService.findAllSubRoles(ourRole.getId());
		Assert.assertEquals(1, subRoles.size());
		Assert.assertEquals(ourRole.getId(), subRoles.get(0).getSuperior());
		
		
		// ***testing role updates
		// create system
		
		setPath(PATH_TWO, "importRolesTestFile05.csv");
		Pair<SysSystemDto, Map<String, Object>> pairUpdate = createData();
		SysSystemDto systemUpdate = pairUpdate.getFirst();
		
		// creates identity
		IdmIdentityDto identityUpdate = testHelper.createIdentity(GUARANTEE_TWO);
        testHelper.createIdentityContact(identityUpdate);
        
        // create subrole
        testHelper.createRole(SUB_ROLE_TWO);
        
		// create role
		IdmRoleDto roleToAssingUpdate = new IdmRoleDto();
		roleToAssingUpdate.setCode(GUARANTEE_ROLE_TWO);
		roleToAssingUpdate.setName(GUARANTEE_ROLE_TWO);
		
		roleToAssingUpdate = roleService.save(roleToAssingUpdate);
		//
		Map<String, Object> configOfLRTUpdate = addToCongig(pairUpdate.getSecond());
		ImportRolesFromCSVExecutor lrtUpdate = new ImportRolesFromCSVExecutor();
		lrtUpdate.init(configOfLRTUpdate);
		longRunningTaskManager.executeSync(lrtUpdate);
		IdmLongRunningTaskDto taskUpdate = longRunningTaskManager.getLongRunningTask(lrtUpdate);
		while (taskUpdate.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		Long countUpdate = taskUpdate.getCount();
		Long totalUpdate = 1L;
		Assert.assertEquals(taskUpdate.getCounter(), countUpdate);
		Assert.assertEquals(totalUpdate, countUpdate);
		SysRoleSystemFilter filterUpdate = new SysRoleSystemFilter();
		filterUpdate.setSystemId(systemUpdate.getId());
		List<SysRoleSystemDto> contentUpdate = roleSystemService.find(filterUpdate, null).getContent();
		Assert.assertEquals(1, contentUpdate.size());

		LinkedList<IdmRoleDto> rolesUpdate = new LinkedList<>();
		contentUpdate.forEach(r -> rolesUpdate.add(roleService.get(r.getRole())));
		boolean containsUpdate = false;
		IdmRoleDto ourRoleUpdate = null;
		for (IdmRoleDto role : rolesUpdate) {
			if (filterContains(role)) {
				ourRoleUpdate = role;
				containsUpdate = true;
				break;
			}
		}
		assertTrue(containsUpdate);
		assertNotNull(ourRoleUpdate);
		
		// test for adding attributes
		IdmRoleFormAttributeFilter roleFormAttributeFilterUpdate = new IdmRoleFormAttributeFilter();
		roleFormAttributeFilterUpdate.setRole(ourRoleUpdate.getId());
		List<IdmRoleFormAttributeDto> allRolesParamsUpdate = roleFormAttributeService.find(roleFormAttributeFilterUpdate, null).getContent();
		
		IdmFormAttributeDto formAttributeDtoUpdate = new IdmFormAttributeDto();
		for(IdmRoleFormAttributeDto p : allRolesParamsUpdate) {
			formAttributeDtoUpdate = formAttributeService.get(p.getFormAttribute());
		}
		
		Assert.assertEquals(ATTRIBUTE_TWO, formAttributeDtoUpdate.getName());
		
		// test for setting criticality
		Assert.assertEquals(CRITICALITY_TWO, ourRoleUpdate.getPriority());
		
		// test for setting guarantee, finds the guarantee of the role
		IdmRoleGuaranteeFilter filterGuaranteeRoleUpdate = new IdmRoleGuaranteeFilter();
		filterGuaranteeRoleUpdate.setRole(ourRoleUpdate.getId());
		List<IdmRoleGuaranteeDto> garantLinksUpdate = roleGuaranteeService.find(filterGuaranteeRoleUpdate, null).getContent();

		Assert.assertEquals(2, garantLinksUpdate.size());
		
		// test for setting guarantee role
		IdmRoleGuaranteeRoleFilter filterRoleGuaranteeRoleUpdate = new IdmRoleGuaranteeRoleFilter();
		filterRoleGuaranteeRoleUpdate.setRole(ourRoleUpdate.getId());
		List<IdmRoleGuaranteeRoleDto> garantRoleLinksUpdate = roleGuaranteeRoleService.find(filterRoleGuaranteeRoleUpdate, null).getContent();
		
		Assert.assertEquals(2, garantRoleLinksUpdate.size());

		// test for assigning sub roles
		List<IdmRoleCompositionDto> subRolesUpdate = roleCompositionService.findAllSubRoles(ourRoleUpdate.getId());
		Assert.assertEquals(2, subRolesUpdate.size());
	}
	
	@Test
	public void importRolesTestEnvironment() {
		setPath(PATH_THREE, "importRolesTestFile06.csv");
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		//
		Map<String, Object> configOfLRT = addToCongigOtherParams(pair.getSecond());
		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
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
		Long total = 1L;
		Assert.assertEquals(task.getCounter(), count);
		Assert.assertEquals(total, count);
		
		IdmRoleFilter rf = new IdmRoleFilter();
		rf.setEnvironment(ENVIRONMENT);
		List<IdmRoleDto> rolesForEnvironment = roleService.find(rf, null).getContent();
		Assert.assertEquals(1, rolesForEnvironment.size());
		Assert.assertEquals("testtest3" + "|" + ENVIRONMENT, rolesForEnvironment.get(0).getCode());
		Assert.assertEquals(ENVIRONMENT, rolesForEnvironment.get(0).getEnvironment());
	}

	private Map<String, Object> addToCongig(Map<String, Object> configOfLRT){
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_ATTRIBUTES_COLUMN_NAME, ROLE_ATTRIBUTE);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_FORM_DEFINITION_CODE, DEFINITION);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_DESCRIPTION_COLUMN_NAME, DESCRIPTION);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_COLUMN_NAME, GUARANTEE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_ROLE_COLUMN_NAME, GUARANTEE_ROLE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CRITICALITY_COLUMN_NAME, CRITICALITY_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CATALOGUES_COLUMN_NAME, CATALOGUES_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_SUBROLES_COLUMN_NAME, SUB_ROLE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_TYPE_COLUMN_NAME, GUARANTEE_TYPE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME, GUARANTEE_ROLE_TYPE_COLUMN);
		return configOfLRT;
	}
	
	private Map<String, Object> addToCongigOtherParams(Map<String, Object> configOfLRT){
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_ENVIRONMENT, ENVIRONMENT);
		return configOfLRT;
	}
}