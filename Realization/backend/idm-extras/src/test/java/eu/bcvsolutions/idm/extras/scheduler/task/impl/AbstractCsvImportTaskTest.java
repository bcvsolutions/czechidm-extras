package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertNotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmProfileDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Abstract test for tasks importing data from a CSV file.
 * 
 * @author Tomáš Doischer
 *
 */
public abstract class AbstractCsvImportTaskTest extends AbstractIntegrationTest {

	@Autowired
	protected TestHelper helper;
	@Autowired
	protected SysSystemService systemService;
	@Autowired
	protected SysRoleSystemService roleSystemService;
	@Autowired
	protected LongRunningTaskManager longRunningTaskManager;
	@Autowired
	protected AttachmentManager attachmentManager;
	@Autowired
	protected SysSchemaAttributeService schemaAttributeService;
	@Autowired
	protected SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	protected SysSystemMappingService mappingService;
	@Autowired
	protected IdmRoleService roleService;
	@Autowired
	protected IdmRoleCatalogueService roleCatalogueService;

	boolean filterContains(IdmRoleDto role, String checkName) {
		if (role == null) {
			throw new IllegalArgumentException("Role is null!");
		}
		return role.getName().equals(checkName);
	}

	SysSystemDto initSystem(String systemName) {
		// create test system
		SysSystemDto system = helper.createTestResourceSystem(true, systemName);
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
	
	IdmAttachmentDto createAttachment(String path, String name) {
		File file = new File(path);
		DataInputStream stream = null;
		try {
			stream = new DataInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		assertNotNull(stream);
		IdmAttachmentDto attachment = new IdmAttachmentDto();
		attachment.setInputData(stream);
		attachment.setName(name);
		attachment.setMimetype("text/csv");
		//
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmProfileDto profile = getHelper().createProfile(identity);

		return attachmentManager.saveAttachment(profile, attachment);
	}
}