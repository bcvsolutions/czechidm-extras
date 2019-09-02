package eu.bcvsolutions.idm.extras.event.processor.contract;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class ContractSetEavTreesProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper testHelper;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmTreeNodeService treeNodeService;

	@Test
	public void createAutomaticRoleToStructure() {
		prepareConfig();
		// prepare tree
		String rootNodeName = "rootNode";
		String childNodeName = "childNode";
		String grandchildNodeName = "grandchildNode";
		// root
		IdmTreeNodeDto rootNode = testHelper.createTreeNode(rootNodeName, null);
		// child
		IdmTreeNodeDto childNode = testHelper.createTreeNode(childNodeName, rootNode);
		// grandchild
		IdmTreeNodeDto grandchildNode = testHelper.createTreeNode(grandchildNodeName, childNode);
		// role
		IdmRoleDto role = testHelper.createRole();
		// automatic role
		String automaticRoleAttributeName = "automaticRoleAttribute";
		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(automaticRoleAttributeName);
		automaticRole = automaticRoleAttributeService.save(automaticRole);
		//
		String eavNameTree = configurationService.getValue(AbstractContractSetEavTreesProcessor.EAV_CONFIG_TREE_NAME);
		//
		IdmFormAttributeDto formAttribute = formAttributeService.findAttribute(IdmIdentityContract.class.getName(), FormService.DEFAULT_DEFINITION_CODE, eavNameTree);

		// rule for automatic role
		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
		rule.setValue(rootNodeName);
		rule.setAutomaticRoleAttribute(automaticRole.getId());
		rule.setFormAttribute(formAttribute.getId());
		automaticRoleAttributeRuleService.save(rule);
		// create identity
		IdmIdentityDto identityGrandchild = testHelper.createIdentity();
		// create contract
		testHelper.createIdentityContact(identityGrandchild, grandchildNode);
		//
		automaticRoleAttributeService.recalculate(automaticRole.getId());
		// find contracts
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.findAllByIdentity(identityGrandchild.getId());
		Assert.assertEquals("Size of assigned IdentityRoles should be 1!", 1, allByIdentity.size());
		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setId(allByIdentity.get(0).getRole());
		List<IdmRoleDto> roles = roleService.find(filter, null).getContent();
		Assert.assertEquals("Size of assigned Roles should be 1!", 1, roles.size());
		Assert.assertEquals("Wrong UUID of role!", role.getId(), roles.get(0).getId());
		resetConfig();
	}

	@Test
	public void changeStructureWhenChanged() {
		prepareConfig();
		String rootNode2a = "rootNode2a";
		String rootNode2b = "rootNode2b";
		String childNode2 = "childNode2";
		// root 1
		IdmTreeNodeDto rootNode = testHelper.createTreeNode(rootNode2a, null);
		// root 2
		IdmTreeNodeDto rootNode2 = testHelper.createTreeNode(rootNode2b, null);
		// child
		IdmTreeNodeDto grandchildNode = testHelper.createTreeNode(childNode2, rootNode);
		// create identity
		IdmIdentityDto identity = testHelper.createIdentity();
		// create contract
		IdmIdentityContractDto identityContact = testHelper.createIdentityContact(identity, grandchildNode);
		//
		String eavNameTree = configurationService.getValue(AbstractContractSetEavTreesProcessor.EAV_CONFIG_TREE_NAME);
		//
		IdmFormDefinitionDto definition = formService.getDefinition(identityContact.getClass(), FormService.DEFAULT_DEFINITION_CODE);
		//
		List<IdmFormValueDto> values = formService.getValues(identityContact, definition, eavNameTree);
		Assert.assertEquals("Values size should be 2!", 2, values.size());
		//
		grandchildNode.setParent(rootNode2.getId());
		treeNodeService.save(grandchildNode);
		List<IdmFormValueDto> newValues = formService.getValues(identityContact, definition, eavNameTree);
		Assert.assertEquals("Values size should be 2!", 2, newValues.size());
		String firstAfter = newValues.get(0).getShortTextValue();
		String secondAfter = newValues.get(1).getShortTextValue();
		int counterSame = 0;
		String sameName = "";
		for (IdmFormValueDto val : values) {
			String nodeName = val.getShortTextValue();
			if (nodeName.equals(firstAfter) || nodeName.equals(secondAfter)) {
				++counterSame;
				sameName = nodeName;
			}
		}
		Assert.assertEquals("Amount of same records should be 1!", 1, counterSame);
		Assert.assertEquals("Node name should be this!", childNode2, sameName);
		resetConfig();
	}

	private void prepareConfig(){
		configurationService.setValue("idm.sec.extras.processor.contract-position-set-eav-processor.enabled", "true");
		configurationService.setValue("idm.sec.extras.processor.identity-contract-set-eavs-processor.enabled", "true");
		configurationService.setValue("idm.sec.extras.processor.tree-node-update-eav-trees-processor.enabled", "true");
	}

	private void resetConfig(){
		configurationService.setValue("idm.sec.extras.processor.contract-position-set-eav-processor.enabled", "false");
		configurationService.setValue("idm.sec.extras.processor.identity-contract-set-eavs-processor.enabled", "false");
		configurationService.setValue("idm.sec.extras.processor.tree-node-update-eav-trees-processor.enabled", "false");
	}

}