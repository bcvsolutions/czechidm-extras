package eu.bcvsolutions.idm.extras.model.security.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import java.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmEntityEventService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.extras.security.evaluator.identity.IdentityAccessForRoleGuaranteeEvaluator;
import eu.bcvsolutions.idm.extras.security.evaluator.identity.IdentityRoleAccessForRoleGuaranteeEvaluator;
import eu.bcvsolutions.idm.extras.security.evaluator.identity.RoleRequestAccessForRoleGuaranteeEvaluator;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Roman Kucera
 * @author Ondrej Kopr
 */
public class ExtrasIdmConceptRoleRequestServiceTest extends AbstractIntegrationTest {

	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired
	private IdmEntityEventService entityEventService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;

	private IdmIdentityDto identity;
	private IdmIdentityDto requester;
	private IdmRoleDto roleWithGuarantee;
	private IdmRoleDto roleWithoutGuarantee;
	private IdmIdentityContractDto primeValidContract;
	private IdmIdentityContractDto requesterPrimeValidContract;

	@Before
	public void init() {
		getHelper().loginAdmin();
		// prepare users
		identity = getHelper().createIdentity();
		requester = getHelper().createIdentity();
		primeValidContract = contractService.getPrimeValidContract(identity.getId());
		requesterPrimeValidContract = contractService.getPrimeValidContract(requester.getId());

		// create role with all evaluators permissions
		IdmRoleDto role = getHelper().createRole();
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdentityAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITYROLE,
				IdmIdentityRole.class,
				IdentityRoleAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.ROLEREQUEST,
				IdmRoleRequest.class,
				RoleRequestAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().assignRoles(primeValidContract, false, role);
		getHelper().assignRoles(requesterPrimeValidContract, false, role);

		// create other role and set our user as guarantee
		roleWithGuarantee = getHelper().createRole();
		IdmRoleGuaranteeDto roleGuarantee = new IdmRoleGuaranteeDto();
		roleGuarantee.setRole(roleWithGuarantee.getId());
		roleGuarantee.setGuarantee(identity.getId());
		roleGuaranteeService.save(roleGuarantee);

		roleWithoutGuarantee = getHelper().createRole();
		getHelper().logout();
	}

	@After
	public void end() {
		getHelper().logout();
	}

	@Transactional
	@Test(expected = ResultCodeException.class)
	public void testRemoveRoleNotGuarantee() {
		IdmIdentityDto user = getHelper().createIdentity();
		IdmIdentityContractDto userContract = contractService.getPrimeValidContract(user.getId());
		getHelper().assignRoles(userContract, false, roleWithoutGuarantee);

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithoutGuarantee.getId().toString())).findFirst().get();

		getHelper().login(identity);

		// remove role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.identity.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// remove
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithoutGuarantee.getId());
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.REMOVE);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		fail("Removing role for which I am not guarantee is forbidden");
	}

	@Transactional
	@Test(expected = ResultCodeException.class)
	public void testEditRoleNotGuarantee() {
		IdmIdentityDto user = getHelper().createIdentity();
		IdmIdentityContractDto userContract = contractService.getPrimeValidContract(user.getId());
		getHelper().assignRoles(userContract, false, roleWithoutGuarantee);

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithoutGuarantee.getId().toString())).findFirst().get();

		getHelper().login(identity);

		// update role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.identity.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// update
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithoutGuarantee.getId());
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.UPDATE);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		fail("Edit role for which I am not guarantee is forbidden");
	}

	@Transactional
	@Test(expected = ResultCodeException.class)
	public void testAddRoleNotGuarantee() {
		getHelper().login(requester);

		// add role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.requester.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// add
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithoutGuarantee.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		fail("Adding role for which I am not guarantee is forbidden");
	}

	@Transactional
	@Test
	public void testRemoveRole() {
		IdmIdentityDto user = getHelper().createIdentity();
		IdmIdentityContractDto userContract = contractService.getPrimeValidContract(user.getId());
		getHelper().assignRoles(userContract, false, roleWithGuarantee);

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithGuarantee.getId().toString())).findFirst().get();

		int numberOfRoles = assignedRoles.size();

		getHelper().login(identity);

		// remove role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.identity.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// remove
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithGuarantee.getId());
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.REMOVE);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		assertEquals(numberOfRoles - 1, assignedRoles.size());
	}

	@Transactional
	@Test
	public void testEditRole() {
		IdmIdentityDto user = getHelper().createIdentity();
		IdmIdentityContractDto userContract = contractService.getPrimeValidContract(user.getId());
		getHelper().assignRoles(userContract, false, roleWithGuarantee);

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithGuarantee.getId().toString())).findFirst().get();

		int numberOfRoles = assignedRoles.size();

		getHelper().login(identity);

		// update role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.identity.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// update
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithGuarantee.getId());
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.UPDATE);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		assertEquals(numberOfRoles, assignedRoles.size());

	}

	@Transactional
	@Test
	public void testAddRole() {
		getHelper().login(identity);

		// add role
		final IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(this.identity.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		final IdmRoleRequestDto roleRequestTwo = roleRequestService.save(roleRequest);
		// add
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequestTwo.getId());
		conceptRoleRequest.setIdentityContract(primeValidContract.getId());
		conceptRoleRequest.setRole(roleWithGuarantee.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
		conceptRoleRequestService.save(conceptRoleRequest);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		waitForRequest(roleRequestTwo.getId());

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithGuarantee.getId().toString())).findFirst().get();
		assertEquals(identityRoleDto.getRole().toString(), roleWithGuarantee.getId().toString());
	}

	@Test
	public void testResaveConceptWithoutChangesAdd() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(requester);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(0, allByIdentity.size());

		getHelper().login(guarantee);

		IdmRoleRequestDto roleRequest = createRoleRequest(applicant);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.ADD);
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(requester);
		// Without change create
		conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(guarantee);
		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());
	}

	@Test(expected = ResultCodeException.class)
	public void testResaveConceptWithChangeValidTill() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(requester);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(0, allByIdentity.size());

		getHelper().login(guarantee);

		IdmRoleRequestDto roleRequest = createRoleRequest(applicant);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.ADD);
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(requester);

		conceptRoleRequest.setValidTill(LocalDate.now().minusDays(5));
		conceptRoleRequestService.save(conceptRoleRequest);
		fail();

		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());
	}

	@Test
	public void testResaveConceptWithChangeSystemState() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(requester);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(0, allByIdentity.size());

		getHelper().login(guarantee);

		IdmRoleRequestDto roleRequest = createRoleRequest(applicant);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.ADD);
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(requester);

		conceptRoleRequest.setSystemState(new OperationResultDto(OperationState.EXECUTED));
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		conceptRoleRequest.setSystemState(new OperationResultDto(OperationState.RUNNING));
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(guarantee);
		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());
	}

	@Test(expected = ResultCodeException.class)
	public void testCreateNewByApplicant() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(requester);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);

		IdmRoleRequestDto roleRequest = getHelper().assignRoles(getHelper().getPrimeContract(applicant), assignedRole);
		waitForRequest(roleRequest.getId());
		getHelper().login(guarantee);
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());

		IdmIdentityRoleDto identityRoleDto = allByIdentity.get(0);

		roleRequest = createRoleRequest(applicant);

		getHelper().login(requester);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.REMOVE);
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);
		fail();

		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());
	}

	@Test
	public void testEditExistingSystemState() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(requester);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);

		IdmRoleRequestDto roleRequest = getHelper().assignRoles(getHelper().getPrimeContract(applicant), assignedRole);
		waitForRequest(roleRequest.getId());
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());

		IdmIdentityRoleDto identityRoleDto = allByIdentity.get(0);

		getHelper().login(guarantee);
		roleRequest = createRoleRequest(applicant);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.REMOVE);
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(requester);
		conceptRoleRequest.setSystemState(new OperationResultDto(OperationState.EXECUTED));
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		conceptRoleRequest.setSystemState(new OperationResultDto(OperationState.RUNNING));
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(guarantee);
		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(0, allByIdentity.size());
	}

	@Test(expected = ResultCodeException.class)
	public void testEditExistingValidTill() {
		getHelper().loginAdmin();
		IdmIdentityDto applicant = getHelper().createIdentity();
		IdmIdentityDto requester = getHelper().createIdentity();
		IdmIdentityDto guarantee = getHelper().createIdentity();

		createAndAssignRoleWithPermissions(applicant);
		createAndAssignRoleWithPermissions(guarantee);

		IdmRoleDto assignedRole = getHelper().createRole();
		getHelper().createRoleGuarantee(assignedRole, guarantee);

		IdmRoleRequestDto roleRequest = getHelper().assignRoles(getHelper().getPrimeContract(applicant), assignedRole);
		waitForRequest(roleRequest.getId());
		getHelper().login(guarantee);
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(applicant.getId());
		filter.setRoleId(assignedRole.getId());
		List<IdmIdentityRoleDto> allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(1, allByIdentity.size());

		IdmIdentityRoleDto identityRoleDto = allByIdentity.get(0);

		roleRequest = createRoleRequest(applicant);

		IdmConceptRoleRequestDto conceptRoleRequest = prepareConcept(roleRequest, getHelper().getPrimeContract(applicant), ConceptRoleRequestOperation.REMOVE);
		conceptRoleRequest.setIdentityRole(identityRoleDto.getId());
		conceptRoleRequest.setRole(assignedRole.getId());
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);

		getHelper().login(requester);
		conceptRoleRequest.setValidTill(LocalDate.now().minusDays(6));
		conceptRoleRequest = conceptRoleRequestService.save(conceptRoleRequest);
		fail();

		getHelper().executeRequest(roleRequest, false);

		waitForRequest(roleRequest.getId());

		allByIdentity = identityRoleService.find(filter, null).getContent();
		assertEquals(0, allByIdentity.size());
	}

	/**
	 * Assign role with permission for create and edit concept.
	 *
	 * @param identity
	 */
	private void createAndAssignRoleWithPermissions(IdmIdentityDto identity) {
		IdmRoleDto role = getHelper().createRole();
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdentityAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITYROLE,
				IdmIdentityRole.class,
				IdentityRoleAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.ROLEREQUEST,
				IdmRoleRequest.class,
				RoleRequestAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		IdmRoleRequestDto requestDto = getHelper().assignRoles(getHelper().getPrimeContract(identity), role);
		waitForRequest(requestDto.getId());
	}

	/**
	 * Create empty role request for identity
	 *
	 * @param applicant
	 * @return
	 */
	private IdmRoleRequestDto createRoleRequest(IdmIdentityDto applicant) {
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(applicant.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		return roleRequestService.save(roleRequest);
	}

	/**
	 * Prepare concept for given request and contract with operation.
	 *
	 * @param roleRequest
	 * @param contract
	 * @param operation
	 * @return
	 */
	private IdmConceptRoleRequestDto prepareConcept(IdmRoleRequestDto roleRequest, IdmIdentityContractDto contract, ConceptRoleRequestOperation operation) {
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequest.getId());
		conceptRoleRequest.setIdentityContract(contract.getId());
		conceptRoleRequest.setOperation(operation);
		return conceptRoleRequest;
	}

	/**
	 * Wait for request result.
	 * TODO: asynchronous is disabled.
	 *
	 * @param requestId
	 */
	private void waitForRequest(UUID requestId) {
		// wait for executed events
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(requestId);
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, PageRequest.of(0, 1)).getTotalElements() != 0;
		}, 1000, 30);
	}
}