package eu.bcvsolutions.idm.extras.event.processor.contract;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
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
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
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

	@Test
	public void createAutomaticRoleToStructure() {
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
	}

}