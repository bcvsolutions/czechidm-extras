package eu.bcvsolutions.idm.extras.workflow;

import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.domain.ScriptAuthorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptAuthorityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptAuthorityService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.service.api.ExtrasWfApprovingService;
import eu.bcvsolutions.idm.extras.service.impl.DefaultExtrasWfApprovingService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
 
/**
 * Test service for extras role approval WF
 *
 * @author Petr Michal
 */
public class ExtrasWfApprovingServiceTest extends AbstractIntegrationTest {

	private static final String GUARANTEE_TYPE_A = "Type_A";
	private static final String GUARANTEE_TYPE_B = "Type_B";
	private static final String APPROVAL_CUSTOM_SCRIPT = "extrasTestWfApprovalCustomScript";
	
	private static final String APPROVER_IDENTITY_FORM_CUSTOM_SCRIPT = "approverFromScript";
	
	private static final String GUARANTEE_IDENTITY = "guaranteeIdentity";
	private static final String GUARANTEE_IDENTITY_A = "guaranteeIdentityTypeA";
	private static final String GUARANTEE_IDENTITY_B = "guaranteeIdentityTypeB";
	
	private static final String MANAGER_FOR_CONTRACT_1 = "managerForContract_1";
	private static final String MANAGER_FOR_CONTRACT_2 = "managerForContract_2";
	
	@Autowired
	private IdmConfigurationService configurationService;
	@Autowired
	private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	IdmIdentityContractService idmIdentityContractService;
	@Autowired
	ExtrasWfApprovingService extrasWfApprovingService;
	@Autowired
	DefaultExtrasWfApprovingService defaultExtrasWfApprovingService;
	@Autowired
	IdmScriptAuthorityService scriptAuthorityService;
	@Autowired
	IdmScriptService scriptService;
	@Autowired
	GroovyScriptService groovyScriptService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	SecurityService securityService;
	@Autowired
	IdmIdentityRoleService identityRoleService;


	private IdmRoleDto roleForApproval;
	private IdmRoleDto roleForApproval2;
	private IdmIdentityContractDto identityContractWithManager1;
	private IdmIdentityContractDto identityContractWithManager2;


	public void init() {
		configurationService.setValue(ExtrasConfiguration.EXTRAS_APPROVAL_WF_CUSTOM_SCRIPT, APPROVAL_CUSTOM_SCRIPT);
		configurationService.setValue(ExtrasConfiguration.EXTRAS_APPROVAL_WF_GUARANTEE_TYPE_A, GUARANTEE_TYPE_A);
		configurationService.setValue(ExtrasConfiguration.EXTRAS_APPROVAL_WF_GUARANTEE_TYPE_B, GUARANTEE_TYPE_B);
		
		//Identity
		IdmIdentityDto conceptIdentity = getHelper().createIdentity();
		
		//Contrats
		identityContractWithManager1 = getHelper().createIdentityContact(conceptIdentity);
		identityContractWithManager2 = getHelper().createIdentityContact(conceptIdentity);
		
		//Identities for managers
		IdmIdentityDto managerIdentity1 = getHelper().createIdentity(MANAGER_FOR_CONTRACT_1);
		IdmIdentityDto managerIdentity2 = getHelper().createIdentity(MANAGER_FOR_CONTRACT_2);		
		getHelper().createIdentity(APPROVER_IDENTITY_FORM_CUSTOM_SCRIPT); 
		
		//Set Managers to contracts
		getHelper().createContractGuarantee(identityContractWithManager1, managerIdentity1);
		getHelper().createContractGuarantee(identityContractWithManager2, managerIdentity2);
		
		//Guarantees identity
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity(GUARANTEE_IDENTITY);
		IdmIdentityDto guaranteeIdentityTypeA = getHelper().createIdentity(GUARANTEE_IDENTITY_A);
		IdmIdentityDto guaranteeIdentityTypeB = getHelper().createIdentity(GUARANTEE_IDENTITY_B);
		
		// Role for approval
		roleForApproval =  getHelper().createRole("roleForApproval");
		roleForApproval2 = getHelper().createRole("roleForApproval2");
		
		//Role Guarantees 
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentity);
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentityTypeA, GUARANTEE_TYPE_A);
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentityTypeB, GUARANTEE_TYPE_B);
		//
		getHelper().createRoleGuarantee(roleForApproval2, guaranteeIdentity);
	}

	@After
	public void logout() {
		super.logout();
	}
	
	@Test
	@Transactional
	public void approvalByContractManager() {
		init();
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getContractManagersForApproval(concept);
		assertEquals(MANAGER_FOR_CONTRACT_1, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeWithouGuaranteesByType() {
		init();
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval2, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_A);
		assertEquals(GUARANTEE_IDENTITY, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeForTypeA() {
		init();
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_A);
		assertEquals(GUARANTEE_IDENTITY_A, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeForTypeB() {
		init();
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_B);
		assertEquals(GUARANTEE_IDENTITY_B, approver);
	}
	
	@Test
	@Transactional
	public void getAdminAsApprover() {
		IdmIdentityDto adminIdentity;
		IdmRoleDto adminRole;
		//Administrator
		adminRole = roleService.getByCode(RoleConfiguration.DEFAULT_ADMIN_ROLE);
		adminIdentity = getHelper().createIdentity(getHelper().createName());
		getHelper().createIdentityContact(adminIdentity);
		getHelper().createIdentityRole(adminIdentity, adminRole);
		//
		String approvers =  extrasWfApprovingService.getAdminAsCandidate();
		assertTrue(approvers.contains(adminIdentity.getCode()));
	}
	
	@Test
	@Transactional
	public void loggedUserInCandidates() {
		getHelper().loginAdmin();
		String candidates = "admin";
		String loggedUserId = securityService.getCurrentId().toString();
		boolean isAdminCandidate =  extrasWfApprovingService.isUserInCandidates(candidates, loggedUserId);
		assertTrue(isAdminCandidate);
	}


	@Test
	@Transactional
	public void testEvaluateScriptWithAnotherCategory() {
		init();
		IdmScriptDto subScript = new IdmScriptDto();
		subScript.setCategory(IdmScriptCategory.SYSTEM);
		subScript.setCode(APPROVAL_CUSTOM_SCRIPT);
		subScript.setName(APPROVAL_CUSTOM_SCRIPT);
		//
		subScript.setScript(createListScript());
		//
		subScript = scriptService.saveInternal(subScript);
		//
		createAuthority(subScript.getId(), ScriptAuthorityType.CLASS_NAME, IdmIdentityDto.class.getName(), null);
		createAuthority(subScript.getId(), ScriptAuthorityType.SERVICE, IdmIdentityService.class.getCanonicalName(), "identityService");
		
		String approvers = extrasWfApprovingService.getApproversFromScript(null);
		assertEquals(APPROVER_IDENTITY_FORM_CUSTOM_SCRIPT, approvers);	
	}
	
	@Test
	public void testSetApprovalRoleAsAutomatic() {
		//Behavior for use advanced approval workflow is the same for all approval WF in extras module
		configurationService.setValue("idm.sec.core.wf.role.approval.4", "extras-approve-role-by-contract-manager");
		
		//Identity for automatic role
		IdmIdentityDto autoRoleIdentity = getHelper().createIdentity("AutoroleIdentity");
		getHelper().createIdentityContact(autoRoleIdentity);
		IdmRoleDto roleForAutomaticTest = getHelper().createRole();
		//Use advanced approval WF for this role
		roleForAutomaticTest.setPriority(4);
		
		IdmAutomaticRoleAttributeDto automaticRole = getHelper().createAutomaticRole(roleForAutomaticTest.getId());
		getHelper().createAutomaticRoleRule(automaticRole.getId(), AutomaticRoleAttributeRuleComparison.EQUALS, AutomaticRoleAttributeRuleType.IDENTITY, IdmIdentity_.username.getName(), null, autoRoleIdentity.getUsername());
		getHelper().recalculateAutomaticRoleByAttribute(automaticRole.getId());
		
		List<IdmIdentityRoleDto> identityRoles = identityRoleService.findAllByIdentity(autoRoleIdentity.getId());
		List<UUID> rolesIds = identityRoles.stream().map(IdmIdentityRoleDto::getRole).collect(Collectors.toList());
			
		assertTrue(rolesIds.contains(roleForAutomaticTest.getId()));
	}
	
	private String createListScript() {
		StringBuilder script = new StringBuilder();
		script.append("import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;\n");
		script.append("List<IdmIdentityDto> candidates = new ArrayList<IdmIdentityDto>();\n");
		script.append("candidates.add(identityService.getByUsername(\"approverFromScript\"));\n");
		script.append("return candidates;\n");

		return script.toString();
	}
	
	private IdmScriptAuthorityDto createAuthority(UUID scriptId, ScriptAuthorityType type, String className, String service) {
		IdmScriptAuthorityDto auth = new IdmScriptAuthorityDto();
		auth.setClassName(className);
		auth.setType(type);
		auth.setScript(scriptId);
		if (type == ScriptAuthorityType.SERVICE) {
			auth.setService(service);
		}
		return scriptAuthorityService.saveInternal(auth);
	}
	
	private IdmConceptRoleRequestDto createRoleConcept(IdmRoleDto role, IdmIdentityContractDto contract) {
		IdmRoleRequestDto roleRequest = createRoleRequest(contract.getIdentity());
		IdmConceptRoleRequestDto concept = new IdmConceptRoleRequestDto();
		concept.setRoleRequest(roleRequest.getId());
		concept.setOperation(ConceptRoleRequestOperation.ADD);
		concept.setRole(role.getId());
		concept.setIdentityContract(contract.getId());
		return conceptRoleRequestService.save(concept);
	}
	
	private IdmRoleRequestDto createRoleRequest(UUID identity) {
		IdmRoleRequestDto request = new IdmRoleRequestDto();
		request.setApplicant(identity);
		request.setExecuteImmediately(false);
		request.setRequestedByType(RoleRequestedByType.MANUALLY);
		return roleRequestService.save(request);
	}	
}