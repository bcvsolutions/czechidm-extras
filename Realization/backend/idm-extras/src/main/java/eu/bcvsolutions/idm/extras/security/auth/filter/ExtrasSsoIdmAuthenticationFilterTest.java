package eu.bcvsolutions.idm.extras.security.auth.filter;

import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.auth.filter.SsoIdmAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.exception.IdmAuthenticationException;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


/**
 * Test authentication using SSO.
 *
 * @author Artem Kolychev
 */
@Transactional
public class ExtrasSsoIdmAuthenticationFilterTest extends AbstractIntegrationTest {

	private static final String TEST_SAME_SERVICE_LOGIN_FIELD = "sameServiceLoginField";
	private static final String TEST_SAME_SERVICE_LOGIN = "sameServiceLogin";

	private static final String PARAMETER_UID_SUFFIXES = "@domain.tld";

	@Autowired
	private IdmConfigurationService configurationService;
	@Autowired
	private FormService formService;
	private ExtrasSsoIdmAuthenticationFilter filter;

	@Before
	public void init() {
		filter = AutowireHelper.createBean(ExtrasSsoIdmAuthenticationFilter.class);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELDS),
				TEST_SAME_SERVICE_LOGIN_FIELD
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						SsoIdmAuthenticationFilter.PARAMETER_UID_SUFFIXES),
				PARAMETER_UID_SUFFIXES
		);
	}

	@After
	public void after() {
		// reset to default
		this.logout();
	}

	@Test
	public void testSsoAuthUserWithFormField() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity.getId());
		//
		IdmFormAttributeDto eavAttributeContract = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentityContract.class,
				PersistentType.SHORTTEXT
		);
		getHelper().setEavValue(
				primeContract,
				eavAttributeContract,
				IdmIdentityContract.class,
				TEST_SAME_SERVICE_LOGIN,
				PersistentType.SHORTTEXT
		);
		//
		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}


	@Test
	public void testSsoAuthUserWithFormContractMultiValueField() {
		IdmFormAttributeDto eavAttributeContract = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentityContract.class,
				PersistentType.SHORTTEXT);
		eavAttributeContract.setMultiple(true);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract1 = getHelper().getPrimeContract(identity.getId());
		formService.saveValues(
				primeContract1.getId(), primeContract1.getClass(), eavAttributeContract, Lists.newArrayList(TEST_SAME_SERVICE_LOGIN, PARAMETER_UID_SUFFIXES));

		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}
	@Test
	public void testSsoAuthUserWithFormIdentityMultiValueField() {
		IdmFormAttributeDto eavAttributeIdentity = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentity.class,
				PersistentType.SHORTTEXT);
		eavAttributeIdentity.setMultiple(true);

		IdmIdentityDto identity = getHelper().createIdentity();
		formService.saveValues(
				identity.getId(),
				identity.getClass(),
				eavAttributeIdentity,
				Lists.newArrayList(TEST_SAME_SERVICE_LOGIN, PARAMETER_UID_SUFFIXES));

		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testSsoAuthUserWithEmptyToken() {
		IdmFormAttributeDto eavAttributeIdentity = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentity.class,
				PersistentType.SHORTTEXT);
		eavAttributeIdentity.setMultiple(true);

		IdmIdentityDto identity = getHelper().createIdentity();
		formService.saveValues(
				identity.getId(),
				identity.getClass(),
				eavAttributeIdentity,
				Lists.newArrayList(null, PARAMETER_UID_SUFFIXES));

		Assert.assertFalse(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testSsoAuthUserWithDuplicatedFormFieldDifferentPeople() {

		IdmFormAttributeDto eavAttributeContract = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentityContract.class,
				PersistentType.SHORTTEXT);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract1 = getHelper().getPrimeContract(identity.getId());
		getHelper().setEavValue(
				primeContract1,
				eavAttributeContract,
				IdmIdentityContract.class,
				TEST_SAME_SERVICE_LOGIN,
				PersistentType.SHORTTEXT
		);

		IdmIdentityDto identity2 = getHelper().createIdentity();
		IdmIdentityContractDto primeContract2 = getHelper().getPrimeContract(identity2.getId());
		getHelper().setEavValue(
				primeContract2,
				eavAttributeContract,
				IdmIdentityContract.class,
				TEST_SAME_SERVICE_LOGIN,
				PersistentType.SHORTTEXT
		);

		Assert.assertFalse(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testSsoAuthUserWithDuplicatedFormFieldSamePeople() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity.getId());
		IdmIdentityContractDto primeContract2 = getHelper().getPrimeContract(identity.getId());
		//
		IdmFormAttributeDto eavAttributeContract;
		eavAttributeContract = getHelper()
				.createEavAttribute(TEST_SAME_SERVICE_LOGIN_FIELD, IdmIdentityContract.class, PersistentType.SHORTTEXT);

		getHelper().setEavValue(primeContract,
				eavAttributeContract,
				IdmIdentityContract.class,
				TEST_SAME_SERVICE_LOGIN,
				PersistentType.SHORTTEXT
		);

		getHelper().setEavValue(primeContract2,
				eavAttributeContract,
				IdmIdentityContract.class,
				TEST_SAME_SERVICE_LOGIN,
				PersistentType.SHORTTEXT
		);

		//
		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

}
