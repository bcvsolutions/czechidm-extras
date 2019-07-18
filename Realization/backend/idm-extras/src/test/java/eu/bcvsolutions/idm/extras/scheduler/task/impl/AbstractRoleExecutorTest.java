package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertNotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
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
import eu.bcvsolutions.idm.extras.utils.Pair;

public abstract class AbstractRoleExecutorTest extends AbstractIntegrationTest {

	private String FILE_PATH;
	private String pathName;
	static final String ROLE_ROW = "roles";
	static final String MEMBER_OF_NAME = "rights";
	public String CHECK_NAME = "ACC-CLOSE";
	static final String DESCRIPTION = "description";
	static final String ROLE_ATTRIBUTE = "attribute";
	static final String GUARANTEE_COLUMN = "guarantees";
	static final String GUARANTEE_ROLE_COLUMN = "guarantee role";
	static final String CRITICALITY_COLUMN = "criticality";
	static final String CATALOGUES_COLUMN = "catalogue";
	static final String DEFINITION = "defin";
		
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
	IdmAttachmentDto attachment;

	boolean filterContains(IdmRoleDto role) {
		if (role == null) {
			throw new IllegalArgumentException("Role is null!");
		}
		return role.getName().equals(CHECK_NAME);
	}

	SysSystemDto initSystem() {

		// create test system
		String generatedString = RandomStringUtils.random(10, true, true);
		SysSystemDto system = helper.createTestResourceSystem(true, "TestSystemNameCSVRoles" + generatedString);
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

	IdmAttachmentDto createAttachment() {
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
		attachment.setName(pathName);
		attachment.setMimetype("text/csv");
		//
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmProfileDto profile = getHelper().createProfile(identity);

		return attachmentManager.saveAttachment(profile, attachment);

	}

	Pair<SysSystemDto, Map<String, Object>> createData() {
		SysSystemDto system = initSystem();
		Assert.assertNotNull(system);
		// create setting of lrt
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_SYSTEM_NAME, system.getCode());
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_COLUMN_SEPARATOR, ';');
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_ROLES_COLUMN_NAME, ROLE_ROW);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_MEMBER_OF_ATTRIBUTE, MEMBER_OF_NAME);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CAN_BE_REQUESTED, true);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_ATTRIBUTES_COLUMN_NAME, ROLE_ATTRIBUTE);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_FORM_DEFINITION_CODE, DEFINITION);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_DESCRIPTION_COLUMN_NAME, DESCRIPTION);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_COLUMN_NAME, GUARANTEE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_GUARANTEE_ROLE_COLUMN_NAME, GUARANTEE_ROLE_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CRITICALITY_COLUMN_NAME, CRITICALITY_COLUMN);
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CATALOGUES_COLUMN_NAME, CATALOGUES_COLUMN);

		//attachment
		attachment = createAttachment();
		configOfLRT.put(ImportRolesFromCSVExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		return new Pair<>(system, configOfLRT);
	}

	public void setPath(String path, String pathName){
		this.FILE_PATH = path;
		this.pathName = pathName;
	}
}