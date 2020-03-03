package eu.bcvsolutions.idm.extras.event.processor.role;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.InitApplicationData;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;


public class RoleRequestIdentitySystemProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper testHelper;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService mappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemAttributeMappingService attributeMappingService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private SysProvisioningArchiveService sysProvisioningArchiveService;

	@Before
	public void init() {
		loginAsAdmin(InitApplicationData.ADMIN_USERNAME);
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void testCreateSystemAndProvideRole() {
		// create system
		SysSystemDto system = testHelper.createSystem(TestResource.TABLE_NAME);
		// generate schema
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		//
		assertEquals(1, objectClasses.size());
		// create mapping with one attribute mapped
		createMappingSystem(SystemEntityType.IDENTITY,
				objectClasses.get(0), "TestMappingProvisioning");
		// create role
		IdmRoleDto role = testHelper.createRole();
		// create role for system
		IdmRoleDto systemRole = testHelper.createRole();
		// put role on the system
		testHelper.createRoleSystem(systemRole, system);
		// create identity
		IdmIdentityDto identity = testHelper.createIdentity();
		// give identity system role
		IdmRoleRequestDto roleRequestA = testHelper.createRoleRequest(identity, systemRole);
		roleRequestA = roleRequestService.startRequest(roleRequestA.getId(), true);
		roleRequestService.executeRequest(roleRequestA.getId());

		// give identity another role and test if not provided
		IdmRoleRequestDto roleRequest = testHelper.createRoleRequest(identity, role);
		roleRequest = roleRequestService.startRequest(roleRequest.getId(), true);
		roleRequestService.executeRequest(roleRequest.getId());
		//
		SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
		filter.setSystemId(system.getId());
		List<SysProvisioningArchiveDto> content = sysProvisioningArchiveService.find(filter, null).getContent();
		// test just created
		assertEquals(1, content.size());
		// start processor
		configurationService.setValue("idm.sec.extras.processor.extras-role-request-identity-system-processor.enabled"
				, "true");
		configurationService.setValue(extrasConfiguration.EXTRAS_SYSTEM_EXCHANGE_ID, system.getId().toString());
		// new role request should start processor
		IdmRoleDto newRole = testHelper.createRole();
		IdmRoleRequestDto roleRequestNew = testHelper.createRoleRequest(identity, newRole);
		roleRequestNew = roleRequestService.startRequest(roleRequestNew.getId(), true);
		roleRequestService.executeRequest(roleRequestNew.getId());
		// test result
		content = sysProvisioningArchiveService.find(filter, null).getContent();
		// should be create and one update after setting a processor
		assertEquals(2, content.size());
	}

	public SysSystemMappingDto createMappingSystem(SystemEntityType type, SysSchemaObjectClassDto objectClass,
												   String name) {
		// system mapping
		SysSystemMappingDto mapping = new SysSystemMappingDto();
		mapping.setName(name);
		mapping.setEntityType(type);
		mapping.setObjectClass(objectClass.getId());
		mapping.setOperationType(SystemOperationType.PROVISIONING);
		mapping = mappingService.save(mapping);
		//
		SysSchemaAttributeFilter filter = new SysSchemaAttributeFilter();
		filter.setObjectClassId(objectClass.getId());
		List<SysSchemaAttributeDto> content = schemaAttributeService.find(filter, null).getContent();
		Object[] newRes = content.stream().filter(attribute -> attribute.getName().equals(
				"__NAME__")).toArray();
		//
		SysSystemAttributeMappingDto uid = new SysSystemAttributeMappingDto();
		uid.setSchemaAttribute(((SysSchemaAttributeDto) (newRes[0])).getId());
		uid.setEntityAttribute(true);
		uid.setName("username");
		uid.setIdmPropertyName("username");
		uid.setUid(true);
		uid.setSystemMapping(mapping.getId());
		//
		attributeMappingService.save(uid);
		//
		return mappingService.save(mapping);
	}

}