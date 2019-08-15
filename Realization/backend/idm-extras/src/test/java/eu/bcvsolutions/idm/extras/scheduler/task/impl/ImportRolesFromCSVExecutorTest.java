package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleFormAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCompositionFilter;
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
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.extras.utils.Pair;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class ImportRolesFromCSVExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile04.csv";

	private static final String ATTRIBUTE = "attr1";
	private static final int CRITICALITY = 3;
	private static final String GUARANTEE = "uzivatel1";
	private static final String GUARANTEE_ROLE = "role1";
	private static final String SUB_ROLE = "subrole";
	private static final String SUB_ROLE_COLUMN = "subroles";
	
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
	
	@Test
	public void importRolesTest() {
		setPath(path, "importRolesTestFile04.csv");
		CHECK_NAME = "CORE-CLOSE";
		// create system
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		SysSystemDto system = pair.getFirst();
		
		// creates identity
		IdmIdentityDto identity = testHelper.createIdentity(GUARANTEE);
        testHelper.createIdentityContact(identity);
		
        // create subrole
        IdmRoleDto subrole = testHelper.createRole(SUB_ROLE);
        
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
		List<IdmRoleGuaranteeDto> garantLinks = roleGuaranteeService.find(filterGuaranteeRole, null, null).getContent();
		IdmRoleGuaranteeDto garantLink = garantLinks.get(0);
		IdmIdentityDto garant = identityService.get(garantLink.getGuarantee());

		Assert.assertEquals(GUARANTEE, garant.getUsername());
		
		// test for setting guarantee role
		IdmRoleGuaranteeRoleFilter filterRoleGuaranteeRole = new IdmRoleGuaranteeRoleFilter();
		filterRoleGuaranteeRole.setRole(ourRole.getId());
		List<IdmRoleGuaranteeRoleDto> garantRoleLinks = roleGuaranteeRoleService.find(filterRoleGuaranteeRole, null, null).getContent();
		IdmRoleGuaranteeRoleDto garantRoleLink = garantRoleLinks.get(0);
		IdmRoleDto garantRole = roleService.get(garantRoleLink.getGuaranteeRole());
		
		Assert.assertEquals(GUARANTEE_ROLE, garantRole.getCode());

		// test for assigning sub roles
		List<IdmRoleCompositionDto> subRoles = roleCompositionService.findAllSubRoles(ourRole.getId(), null);
		Assert.assertEquals(1, subRoles.size());
		Assert.assertEquals(ourRole.getId(), subRoles.get(0).getSuperior());
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
		return configOfLRT;
	}
}