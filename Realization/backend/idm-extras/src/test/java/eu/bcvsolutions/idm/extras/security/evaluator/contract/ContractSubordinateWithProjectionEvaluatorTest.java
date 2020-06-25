package eu.bcvsolutions.idm.extras.security.evaluator.contract;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.dto.IdmContractGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormProjectionDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormProjectionService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

public class ContractSubordinateWithProjectionEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private IdmContractGuaranteeService contractGuaranteeService;
	@Autowired
	private IdmFormProjectionService formProjectionService;

	@Test
	public void testIdentityAccess() {
		IdmFormProjectionDto projetion1 = new IdmFormProjectionDto();
		projetion1.setCode("PROJECTION1");
		projetion1.setOwnerType(IdmIdentity.class.getName());
		projetion1 = formProjectionService.save(projetion1);

		IdmFormProjectionDto projetion2 = new IdmFormProjectionDto();
		projetion2.setCode("PROJECTION2");
		projetion2.setOwnerType(IdmIdentity.class.getName());
		projetion2 = formProjectionService.save(projetion2);
		// prepare users

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeValidContract = contractService.getPrimeValidContract(identity.getId());


		IdmIdentityDto subordinate1 = getHelper().createIdentity();
		subordinate1.setFormProjection(projetion1.getId());
		identityService.save(subordinate1);
		IdmIdentityContractDto primeValidContract2 = contractService.getPrimeValidContract(subordinate1.getId());

		getHelper().createIdentityContact(subordinate1);
		getHelper().createIdentityContact(subordinate1);
		getHelper().createIdentityContact(subordinate1);
		getHelper().createIdentityContact(subordinate1);

		IdmIdentityDto subordinate2 = getHelper().createIdentity();
		subordinate2.setFormProjection(projetion2.getId());
		identityService.save(subordinate2);
		IdmIdentityContractDto primeValidContract3 = contractService.getPrimeValidContract(subordinate2.getId());

		IdmContractGuaranteeDto cgd = new IdmContractGuaranteeDto();
		cgd.setGuarantee(identity.getId());
		cgd.setIdentityContract(primeValidContract2.getId());
		contractGuaranteeService.save(cgd);

		IdmContractGuaranteeDto cgd2 = new IdmContractGuaranteeDto();
		cgd2.setGuarantee(identity.getId());
		cgd2.setIdentityContract(primeValidContract3.getId());
		contractGuaranteeService.save(cgd2);

		// without permissions I will see only myself
		testRead(identity, 1);

		// create role with evaluator permissions
		IdmRoleDto role = getIdmRoleDto(projetion1, IdmBasePermission.READ);
		IdmRoleDto role2 = getIdmRoleDto(projetion2, IdmBasePermission.READ);

		getHelper().assignRoles(primeValidContract, false, role);

		// now wth permissions I will see all users
		testRead(identity, 2);

		getHelper().assignRoles(primeValidContract, false, role2);
		// now wth permissions I will see all users
		testRead(identity, 3);

		testUpdate(identity, primeValidContract2.getId(), true);

		IdmRoleDto roleUpdate1 = getIdmRoleDto(projetion1, IdmBasePermission.UPDATE);
		IdmRoleDto roleUpdate2 = getIdmRoleDto(projetion2, IdmBasePermission.UPDATE);

		getHelper().assignRoles(primeValidContract, false, roleUpdate2);
		//wrong projection
		testUpdate(identity, primeValidContract2.getId(), true);

		getHelper().assignRoles(primeValidContract, false, roleUpdate1);

		testUpdate(identity, primeValidContract2.getId(), false);
	}

	private void testRead(IdmIdentityDto identity, int i) {
		try {
			getHelper().login(identity);
			List<IdmIdentityContractDto> contracts = contractService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(i, contracts.size());
		} finally {
			getHelper().logout();
		}
	}

	private void testUpdate(IdmIdentityDto identity, UUID contractId, boolean expectError) {
		try {
			getHelper().login(identity);
			IdmIdentityContractDto contract = contractService.get(contractId);
			contract.setPosition(UUID.randomUUID().toString());
			contractService.save(contract, IdmBasePermission.UPDATE);
			Assert.assertFalse(expectError);
		} catch (AssertionError ass) {
			// we want to rethrow this
			System.out.println("Exception was not thrown");
			throw ass;
		} catch (Exception e) {
			Assert.assertTrue(expectError);
		} finally {
			getHelper().logout();
		}
	}

	private IdmRoleDto getIdmRoleDto(IdmFormProjectionDto projetion1, BasePermission... permissions) {
		IdmRoleDto role = getHelper().createRole();

		ConfigurationMap config = new ConfigurationMap();
		config.put("form-projection", projetion1.getId());

		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITYCONTRACT,
				IdmIdentityContract.class,
				ContractSubordinateWithProjectionEvaluator.class,
				config,
				permissions);
		return role;
	}

}
