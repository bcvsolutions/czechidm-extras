package eu.bcvsolutions.idm.extras.model.security.impl;

import static org.junit.Assert.*;

import java.util.List;

import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
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
 */
@Transactional
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
	private IdmRoleDto roleWithGuarantee;
	private IdmRoleDto roleWithoutGuarantee;
	private IdmIdentityContractDto primeValidContract;

	@Before
	public void init() {
		// prepare users
		identity = getHelper().createIdentity();
		primeValidContract = contractService.getPrimeValidContract(identity.getId());

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

		// create other role and set our user as guarantee
		roleWithGuarantee = getHelper().createRole();
		IdmRoleGuaranteeDto roleGuarantee = new IdmRoleGuaranteeDto();
		roleGuarantee.setRole(roleWithGuarantee.getId());
		roleGuarantee.setGuarantee(identity.getId());
		roleGuaranteeService.save(roleGuarantee);

		roleWithoutGuarantee = getHelper().createRole();
	}

	@After
	public void end() {
		getHelper().logout();
	}

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
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		fail("Removing role for which I am not guarantee is forbidden");
	}

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
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		fail("Removing role for which I am not guarantee is forbidden");
	}

	@Test(expected = ResultCodeException.class)
	public void testAddRoleNotGuarantee() {
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
		conceptRoleRequest.setRole(roleWithoutGuarantee.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
		conceptRoleRequestService.save(conceptRoleRequest);
		conceptRoleRequestService.save(conceptRoleRequest);
		// execute
		getHelper().executeRequest(roleRequestTwo, false);
		//
		// wait for executed events
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		fail("Removing role for which I am not guarantee is forbidden");
	}

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
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		assertEquals(numberOfRoles - 1, assignedRoles.size());
	}

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
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		assignedRoles = identityRoleService.findAllByIdentity(user.getId());
		assertEquals(numberOfRoles, assignedRoles.size());

	}

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
		final IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setOwnerId(roleRequestTwo.getId());
		getHelper().waitForResult(res -> {
			return entityEventService.find(eventFilter, new PageRequest(0, 1)).getTotalElements() != 0;
		}, 1000, 30);

		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		IdmIdentityRoleDto identityRoleDto = assignedRoles.stream().filter(ir -> ir.getRole().toString().equals(roleWithGuarantee.getId().toString())).findFirst().get();
		assertEquals(identityRoleDto.getRole().toString(), roleWithGuarantee.getId().toString());
	}
}