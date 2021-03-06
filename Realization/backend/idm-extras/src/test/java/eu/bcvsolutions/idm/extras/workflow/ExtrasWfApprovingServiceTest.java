package eu.bcvsolutions.idm.extras.workflow;

import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.domain.ScriptAuthorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
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
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.service.api.ExtrasWfApprovingService;
import eu.bcvsolutions.idm.extras.service.impl.DefaultExtrasWfApprovingService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
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
	private static final String GUARANTEE_IDENTITY_MANUALLY_DISABLED = "guaranteeIdentityBlocked";
	
	private static final String MANAGER_FOR_CONTRACT_1 = "managerForContract1";
	private static final String MANAGER_FOR_CONTRACT_2 = "managerForContract2";
	
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
	private ExtrasConfiguration extrasConfiguration;


	private IdmRoleDto roleForApproval;
	private IdmRoleDto roleForApproval2;
	private IdmRoleDto roleForApproval3;
	private IdmRoleDto adminRole;
	private IdmIdentityContractDto identityContractWithManager1;
	private IdmIdentityContractDto identityContractWithManager2;
	private IdmIdentityDto adminIdentity;
	private IdmIdentityDto guaranteeIdentity;
	private IdmIdentityDto guaranteeIdentityBlocked;
	private IdmIdentityDto managerIdentity1;
	
	@Before
	@Transactional
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
		managerIdentity1 = getHelper().createIdentity(MANAGER_FOR_CONTRACT_1);
		IdmIdentityDto managerIdentity2 = getHelper().createIdentity(MANAGER_FOR_CONTRACT_2);		
		IdmIdentityDto approverFromScript  = getHelper().createIdentity(APPROVER_IDENTITY_FORM_CUSTOM_SCRIPT);
		getHelper().createIdentityContact(managerIdentity1);
		getHelper().createIdentityContact(managerIdentity2);
		getHelper().createIdentityContact(approverFromScript);
		
		//Set Managers to contracts
		getHelper().createContractGuarantee(identityContractWithManager1, managerIdentity1);
		getHelper().createContractGuarantee(identityContractWithManager2, managerIdentity2);
		
		//Guarantees identity
		
		IdmIdentityDto guaranteeIdentityTypeA = getHelper().createIdentity(GUARANTEE_IDENTITY_A);
		IdmIdentityDto guaranteeIdentityTypeB = getHelper().createIdentity(GUARANTEE_IDENTITY_B);
		//
		guaranteeIdentity = getHelper().createIdentity(GUARANTEE_IDENTITY);
		guaranteeIdentityBlocked = getHelper().createIdentity(GUARANTEE_IDENTITY_MANUALLY_DISABLED);
		guaranteeIdentityBlocked.setState(IdentityState.DISABLED_MANUALLY);
		//
		getHelper().createIdentityContact(guaranteeIdentityTypeA);
		getHelper().createIdentityContact(guaranteeIdentityTypeB);
		getHelper().createIdentityContact(guaranteeIdentity);
		getHelper().createIdentityContact(guaranteeIdentityBlocked);

		// Role for approval
		roleForApproval =  getHelper().createRole("roleForApproval");
		roleForApproval2 = getHelper().createRole("roleForApproval2");
		
		//Role Guarantees 
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentity);
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentityTypeA, GUARANTEE_TYPE_A);
		getHelper().createRoleGuarantee(roleForApproval, guaranteeIdentityTypeB, GUARANTEE_TYPE_B);
		//
		getHelper().createRoleGuarantee(roleForApproval2, guaranteeIdentity);
		
		//Administrator
		adminRole = roleService.getByCode(RoleConfiguration.DEFAULT_ADMIN_ROLE);
		adminIdentity = getHelper().createIdentity(getHelper().createName());
		getHelper().createIdentityContact(adminIdentity);
		getHelper().createIdentityRole(adminIdentity, adminRole);
	}

	@After
	public void logout() {
		super.logout();
	}
	
	@Test
	@Transactional
	public void approvalByContractManager() {
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getContractManagersForApproval(concept);
		assertEquals(MANAGER_FOR_CONTRACT_1, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeWithouGuaranteesByType() {
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval2, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_A);
		assertEquals(GUARANTEE_IDENTITY, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeForTypeA() {
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_A);
		assertEquals(GUARANTEE_IDENTITY_A, approver);
	}
	
	@Test
	@Transactional
	public void approvalByRoleGuranteeForTypeB() {
		IdmConceptRoleRequestDto concept = createRoleConcept(roleForApproval, identityContractWithManager1);	
		String approver =  extrasWfApprovingService.getRoleGuaranteesForApproval(concept, ExtrasWfApprovingService.APPROVE_BY_GUARANTEE_B);
		assertEquals(GUARANTEE_IDENTITY_B, approver);
	}
	
	@Test
	@Transactional
	public void getAdminAsApprover() {
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
	@Transactional
	public void selectApproversWithValidStates() {
	
	//use default configuration
	List<IdmIdentityDto> approvalCandidates = new ArrayList<IdmIdentityDto>();
	approvalCandidates.add(guaranteeIdentity);
	approvalCandidates.add(guaranteeIdentityBlocked);

	String processedCandidates = extrasWfApprovingService.processCandidates(approvalCandidates);
	
	assertEquals(guaranteeIdentity.getUsername(), processedCandidates);
	
	//use custom configuration
	approvalCandidates = new ArrayList<IdmIdentityDto>();
	approvalCandidates.add(guaranteeIdentity);
	approvalCandidates.add(guaranteeIdentityBlocked);
	
	configurationService.setValue(ExtrasConfiguration.EXTRAS_APPROVAL_WF_APPROVER_STATES, IdentityState.DISABLED_MANUALLY.toString());
	
	String processedCandidatesWithCustomProperty = extrasWfApprovingService.processCandidates(approvalCandidates);
	assertEquals(guaranteeIdentityBlocked.getUsername(), processedCandidatesWithCustomProperty);
	
	configurationService.setValue(ExtrasConfiguration.EXTRAS_APPROVAL_WF_APPROVER_STATES, "");
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