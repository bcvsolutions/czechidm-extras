package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.dto.*;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.scheduler.api.config.SchedulerConfiguration;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmProcessedTaskItemDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmProcessedTaskItemFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmProcessedTaskItemService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

import static org.junit.Assert.assertNotNull;

public class ImportAutomaticRoleForTreeNodeFromCSVExecutorTest extends AbstractCsvImportTaskTest {
	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importAutomaticRolesTest.csv";
	private static final String FILE_NAME = "importAutomaticRolesTest.csv";
	
	@Autowired
	private LongRunningTaskManager lrtManager;
	@Autowired
	private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private IdmProcessedTaskItemService taskItemService;
	
	@Before
	public void init() {
		// To test the fix of the bug #2542 which happens when more automatic roles are added
		// and they are processed asynchronously (= standard settings for the production usage)
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, true);
	}
	
	@After
	public void cleanUp() {
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
	}
	
	@Test
	public void testImport() {
		// create tree node
		IdmTreeNodeDto testtreenode = createTreeNode("testtreenode");

		// create role
		IdmRoleDto role = getHelper().createRole("testrole");
		IdmRoleDto roletwo = getHelper().createRole("testroletwo");

		// create config of LRT
		IdmAttachmentDto attachment = createAttachment();
		
		Map<String, Object> properties = new HashMap<>();
		
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_NODE_CODES, "nodecodes");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_NODE_IDS, "nodeids");
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_COLUMN_RECURSION_TYPE, "recursiontype");
		properties.put(AbstractCsvImportTask.PARAM_SEPARATOR, ";");
		properties.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachment.getId());
		properties.put(ImportAutomaticRoleForTreeNodeFromCSVExecutor.PARAM_ROLES_COLUMN_NAME, "roles");
		
		ImportAutomaticRoleForTreeNodeFromCSVExecutor lrt = new ImportAutomaticRoleForTreeNodeFromCSVExecutor();
		lrt.init(properties);

		lrtManager.executeSync(lrt);

		// check results
		IdmRoleTreeNodeFilter filter = new IdmRoleTreeNodeFilter();
		filter.setTreeNodeId(testtreenode.getId());
		
		List<IdmRoleTreeNodeDto> found = roleTreeNodeService.find(filter, null).getContent();
		
		// we should find two automatic roles on tree node
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
		
		// there is a line in the csv faketreenode;faketreenode;testroletwo;UP (the tree node does not exist)
		// there is a line in the csv testtreenode;testtreenode;fakerole;UP (the role does not exist)
		// there should be 4 processed items (2 executed, 2 not executed)
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		
		long counter = task.getCount();
		Assert.assertEquals(4L, counter);
		
		// check that the executed and not executed items are logged correctly
		IdmProcessedTaskItemFilter ptif = new IdmProcessedTaskItemFilter();
		ptif.setLongRunningTaskId(task.getId());
		
		List<IdmProcessedTaskItemDto> processed = taskItemService.find(ptif, null).getContent();
		Assert.assertEquals(4, processed.size());
		
		List<OperationState> executed = new ArrayList<>();
		List<OperationState> notExecuted = new ArrayList<>();
		
		for (IdmProcessedTaskItemDto item : processed) {
			if (item.getOperationResult().getState().equals(OperationState.EXECUTED)) {
				executed.add(item.getOperationResult().getState());
			} 
			if (item.getOperationResult().getState().equals(OperationState.NOT_EXECUTED)){
				notExecuted.add(item.getOperationResult().getState());
			}
		}
		
		Assert.assertEquals(2, executed.size());
		Assert.assertEquals(2, notExecuted.size());
	}

	private IdmTreeNodeDto createTreeNode(String name) {
		IdmTreeNodeDto treeNode = getHelper().createTreeNode();
		treeNode.setCode(name);
		treeNode.setName(name);
		return treeNodeService.saveInternal(treeNode);
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
