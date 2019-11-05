package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertNotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmProfileDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;

public class ImportAutomaticRoleForTreeNodeFromCSVExecutorTest extends AbstractRoleExecutorTest {
	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importAutomaticRolesTest.csv";
	private static final String FILE_NAME = "importAutomaticRolesTest.csv";
	
	@Autowired
	private LongRunningTaskManager lrtManager;
	@Autowired
	private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	
	
	@Test
	public void testImport() {
		// create tree node
		IdmTreeNodeDto treeNode = getHelper().createTreeNode();
		treeNode.setCode("testtreenode");
		treeNode.setName("testtreenode");
		treeNodeService.saveInternal(treeNode);
		
		// create role
		IdmRoleDto role = getHelper().createRole("testrole");
		IdmRoleDto roletwo = getHelper().createRole("testroletwo");
		
		// create config of LRT
		IdmAttachmentDto attachment = createAttachment();
		
		Map<String, Object> properties = new HashMap<>();
		
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_NODE_CODES, "nodecodes");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_NODE_IDS, "nodeids");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_RECURSION_TYPE, "recursiontype");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_SEPARATOR, ";");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_CSV_ATTACHMENT, attachment.getId());
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		
		ImportAutomaticRoleForTreeNodeFromCSVExecutor lrt = new ImportAutomaticRoleForTreeNodeFromCSVExecutor();
		lrt.init(properties);

		lrtManager.executeSync(lrt);
		
		// check results
		IdmRoleTreeNodeFilter filter = new IdmRoleTreeNodeFilter();
		filter.setName("testtreenode");
		
		List<IdmRoleTreeNodeDto> found = roleTreeNodeService.find(filter, null, null).getContent();
		
		// we should find one automatic role on tree node
		Assert.assertEquals(2, found.size());
		
		// they should have the test roles
		IdmRoleTreeNodeDto foundRule = found.get(0);
		IdmRoleTreeNodeDto foundRuleTwo = found.get(1);
		List<UUID> ids = new ArrayList<>();
		ids.add(foundRule.getRole());
		ids.add(foundRuleTwo.getRole());
		Assert.assertTrue(ids.contains(role.getId()));
		Assert.assertTrue(ids.contains(roletwo.getId()));

		// they should have the recursion type UP
		Assert.assertEquals(RecursionType.UP, foundRule.getRecursionType());
		Assert.assertEquals(RecursionType.UP, foundRuleTwo.getRecursionType());
	}
	
	public IdmAttachmentDto createAttachment() {
		File file = new File(PATH);
		DataInputStream stream = null;
		try {
			stream = new DataInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		assertNotNull(stream);
		IdmAttachmentDto attachment = new IdmAttachmentDto();
		attachment.setInputData(stream);
		attachment.setName(FILE_NAME);
		attachment.setMimetype("text/csv");
		//
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmProfileDto profile = getHelper().createProfile(identity);

		return attachmentManager.saveAttachment(profile, attachment);
	}
	
	@Test
	public void getFormAttributesTest() {
		ImportAutomaticRoleForTreeNodeFromCSVExecutor lrt = new ImportAutomaticRoleForTreeNodeFromCSVExecutor();
		List<IdmFormAttributeDto> attrs = lrt.getFormAttributes();
		Assert.assertTrue(!attrs.isEmpty());
	}
}
