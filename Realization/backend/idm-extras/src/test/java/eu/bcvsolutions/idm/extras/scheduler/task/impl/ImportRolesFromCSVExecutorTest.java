package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmProfileDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

public class ImportRolesFromCSVExecutorTest extends AbstractIntegrationTest {

	private static final String FILE_PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importRolesTestFile.csv";
	private static final String ROLE_ROW = "roles";
	private static final String MEMBER_OF_NAME = "rights";
	private static final String CHECK_NAME = "ACC-CLOSE";

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	private SysSystemMappingService mappingService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;

	@Test
	public void importRolesTest() {
		// create system
		SysSystemDto system = initData();
		Assert.assertNotNull(system);
		//
		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
		// create setting of lrt
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_SYSTEM_NAME, system.getCode());
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_COLUMN_SEPARATOR, ';');
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_ROLES_COLUMN_NAME, ROLE_ROW);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_MEMBER_OF_ATTRIBUTE, MEMBER_OF_NAME);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CAN_BE_REQUESTED, true);
		//attachment
		IdmAttachmentDto attachment = createAttachment();
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		//
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		Long count = task.getCount();
		Long total = 7L;
		Assert.assertEquals(task.getCounter(), count);
		Assert.assertEquals(total, count);
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setSystemId(system.getId());
		List<SysRoleSystemDto> content = roleSystemService.find(filter, null).getContent();
		Assert.assertEquals(7, content.size());
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
		//
		List<IdmRoleCatalogueDto> allByRole = roleCatalogueService.findAllByRole(ourRole.getId());
		assertEquals(1, allByRole.size());
		assertEquals(ROLE_ROW, allByRole.get(0).getName());
	}

	private boolean filterContains(IdmRoleDto role) {
		if (role == null) {
			throw new IllegalArgumentException("Role is null!");
		}
		return role.getName().equals(CHECK_NAME);
	}

	private SysSystemDto initData() {

		// create test system
		SysSystemDto system = helper.createTestResourceSystem(true, "TestSystemNameCSVRoles");
		Assert.assertNotNull(system);
		List<SysSchemaObjectClassDto> schema = systemService.generateSchema(system);
		SysSchemaAttributeDto rights = new SysSchemaAttributeDto();
		rights.setName("rights");
		rights.setObjectClass(schema.get(0).getId());
		rights.setClassType(String.class.getName());
		rights.setReadable(true);
		rights.setUpdateable(true);
		rights.setMultivalued(true);
		rights.setCreateable(true);
		rights.setReturnedByDefault(true);
		rights = schemaAttributeService.save(rights);
		//
		SysSystemMappingDto mapping = mappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		SysSystemAttributeMappingDto mappingRights = new SysSystemAttributeMappingDto();
		mappingRights.setSystemMapping(mapping.getId());
		mappingRights.setSchemaAttribute(rights.getId());
		mappingRights.setName("RightsMultiValue");
		mappingRights.setStrategyType(AttributeMappingStrategyType.MERGE);
		mappingRights.setCached(true);
		systemAttributeMappingService.save(mappingRights);

		return system;

	}

	private IdmAttachmentDto createAttachment() {
		File file = new File(FILE_PATH);
		DataInputStream stream = null;
		try {
			stream = new DataInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		assertNotNull(stream);
		IdmAttachmentDto attachment = new IdmAttachmentDto();
		attachment.setInputData(stream);
		attachment.setName("importRolesTestFile.csv");
		attachment.setMimetype("text/csv");
		//
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmProfileDto profile = getHelper().createProfile(identity);

		return attachmentManager.saveAttachment(profile, attachment);

	}

}