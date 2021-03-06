package eu.bcvsolutions.idm.extras.security.auth.filter;

import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.rest.BaseDtoController;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.auth.filter.SsoIdmAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.exception.IdmAuthenticationException;
import eu.bcvsolutions.idm.test.api.AbstractRestTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static eu.bcvsolutions.idm.core.security.auth.filter.SsoIdmAuthenticationFilter.DEFAULT_HEADER_NAME;
import static eu.bcvsolutions.idm.test.api.TestHelper.HAL_CONTENT_TYPE;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Test authentication using SSO.
 *
 * @author Artem Kolychev
 */
@Transactional
public class ExtrasSsoIdmAuthenticationFilterTest extends AbstractRestTest {

	private static final String TEST_SAME_SERVICE_LOGIN_FIELD = "sameServiceLoginField";
	private static final String TEST_SAME_SERVICE_LOGIN_DEFINITION = "default";
	private static final String TEST_SAME_SERVICE_LOGIN = "sameServiceLogin";

	private static final String PARAMETER_UID_SUFFIXES = "@domain.tld";

	@Autowired
	private IdmConfigurationService configurationService;
	@Autowired
	private FormService formService;
	private ExtrasSsoIdmAuthenticationFilter filter;

	public void init() {
		filter = AutowireHelper.createBean(ExtrasSsoIdmAuthenticationFilter.class);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_ATTRIBUTE_CODE),
				TEST_SAME_SERVICE_LOGIN_FIELD
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_DEFINITION_CODE),
				TEST_SAME_SERVICE_LOGIN_DEFINITION
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						SsoIdmAuthenticationFilter.PARAMETER_UID_SUFFIXES),
				PARAMETER_UID_SUFFIXES
		);
		configurationService.setValue(
				"idm.sec.extras.authentication-filter.extras-sso-authentication-filter.enabled",
				"true"
		);
		configurationService.setValue(
				"idm.sec.core.authentication-filter.core-sso-authentication-filter.enabled",
				"false"
		);
	}

	public void initWrongDefinition() {
		filter = AutowireHelper.createBean(ExtrasSsoIdmAuthenticationFilter.class);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_ATTRIBUTE_CODE),
				TEST_SAME_SERVICE_LOGIN_FIELD
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_DEFINITION_CODE),
				"wrongDefinition"
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						SsoIdmAuthenticationFilter.PARAMETER_UID_SUFFIXES),
				PARAMETER_UID_SUFFIXES
		);
		configurationService.setValue(
				"idm.sec.extras.authentication-filter.extras-sso-authentication-filter.enabled",
				"true"
		);
		configurationService.setValue(
				"idm.sec.core.authentication-filter.core-sso-authentication-filter.enabled",
				"false"
		);
	}

	public void initWrongAttribute() {
		filter = AutowireHelper.createBean(ExtrasSsoIdmAuthenticationFilter.class);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_ATTRIBUTE_CODE),
				"WrongAttributeCode"
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_DEFINITION_CODE),
				TEST_SAME_SERVICE_LOGIN_DEFINITION
		);
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						SsoIdmAuthenticationFilter.PARAMETER_UID_SUFFIXES),
				PARAMETER_UID_SUFFIXES
		);
		configurationService.setValue(
				"idm.sec.extras.authentication-filter.extras-sso-authentication-filter.enabled",
				"true"
		);
		configurationService.setValue(
				"idm.sec.core.authentication-filter.core-sso-authentication-filter.enabled",
				"false"
		);
	}

	@After
	public void after() {
		// reset to default
		this.logout();
	}

	@Test
	public void testSsoAuthUserWithFormField() throws Exception {
		init();
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity.getId());
		//
		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CHOOSE),
				ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CONTRACT
		);
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
		getMockMvc().perform(get(getRemotePath())
				.header(DEFAULT_HEADER_NAME, TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES)
				.contentType(HAL_CONTENT_TYPE))
				.andExpect(status().isOk())
				.andExpect(content().contentType(HAL_CONTENT_TYPE))
				.andExpect(jsonPath("$.username", equalTo(identity.getUsername())));
		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testSsoAuthUserWithFormFieldWrongDefinition() {
		initWrongDefinition();
		Exception exception = null;
		try {
			IdmIdentityDto identity = getHelper().createIdentity();
			IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity.getId());
			//
			configurationService.setValue(
					filter.getConfigurationPropertyName(
							ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CHOOSE),
					ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CONTRACT
			);
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
			getMockMvc().perform(get(getRemotePath())
					.header(DEFAULT_HEADER_NAME, TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES)
					.contentType(HAL_CONTENT_TYPE))
					.andExpect(status().isOk())
					.andExpect(content().contentType(HAL_CONTENT_TYPE))
					.andExpect(jsonPath("$.username", equalTo(identity.getUsername())));
			filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null);
		} catch (Exception e){
			exception = e;
		}
		Assert.assertTrue(exception instanceof IdmAuthenticationException);
		IdmAuthenticationException exp = (IdmAuthenticationException) exception;
		Assert.assertTrue(exp.getMessage().startsWith("Definition"));
	}

	@Test
	public void testSsoAuthUserWithFormFieldWrongAttribute() {
		initWrongAttribute();
		Exception exception = null;
		try {
			IdmIdentityDto identity = getHelper().createIdentity();
			IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity.getId());
			//
			configurationService.setValue(
					filter.getConfigurationPropertyName(
							ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CHOOSE),
					ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CONTRACT
			);
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
			getMockMvc().perform(get(getRemotePath())
					.header(DEFAULT_HEADER_NAME, TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES)
					.contentType(HAL_CONTENT_TYPE))
					.andExpect(status().isOk())
					.andExpect(content().contentType(HAL_CONTENT_TYPE))
					.andExpect(jsonPath("$.username", equalTo(identity.getUsername())));
			filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null);
		} catch (Exception e){
			exception = e;
		}
		Assert.assertTrue(exception instanceof IdmAuthenticationException);
		IdmAuthenticationException exp = (IdmAuthenticationException) exception;
		Assert.assertTrue(exp.getMessage().startsWith("Attribute"));
	}

	@Test
	public void testSsoAuthUserWithFormContractMultiValueField() throws Exception {
		init();
		IdmFormAttributeDto eavAttributeContract = getHelper().createEavAttribute(
				TEST_SAME_SERVICE_LOGIN_FIELD,
				IdmIdentityContract.class,
				PersistentType.SHORTTEXT);
		eavAttributeContract.setMultiple(true);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract1 = getHelper().getPrimeContract(identity.getId());
		formService.saveValues(
				primeContract1.getId(), primeContract1.getClass(), eavAttributeContract, Lists.newArrayList(TEST_SAME_SERVICE_LOGIN, PARAMETER_UID_SUFFIXES));

		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CHOOSE),
				ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CONTRACT
		);

		getMockMvc().perform(get(getRemotePath())
				.header(DEFAULT_HEADER_NAME, TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES)
				.contentType(HAL_CONTENT_TYPE))
				.andExpect(status().isOk())
				.andExpect(content().contentType(HAL_CONTENT_TYPE))
				.andExpect(jsonPath("$.username", equalTo(identity.getUsername())));
		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testSsoAuthUserWithFormIdentityMultiValueField() throws Exception {
		init();
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

		configurationService.setValue(
				filter.getConfigurationPropertyName(
						ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_CHOOSE),
				ExtrasSsoIdmAuthenticationFilter.PARAMETER_FIELD_IDENTITY
		);

		getMockMvc().perform(get(getRemotePath())
				.header(DEFAULT_HEADER_NAME, TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES)
				.contentType(HAL_CONTENT_TYPE))
				.andExpect(status().isOk())
				.andExpect(content().contentType(HAL_CONTENT_TYPE))
				.andExpect(jsonPath("$.username", equalTo(identity.getUsername())));
		Assert.assertTrue(filter.authorize(TEST_SAME_SERVICE_LOGIN + PARAMETER_UID_SUFFIXES, null, null));
	}

	@Test
	public void testNullAndEmptyToken(){
		init();
		Assert.assertFalse(filter.authorize(null, null, null));
		Assert.assertFalse(filter.authorize("", null, null));
	}


	private String getRemotePath() {
		return BaseDtoController.BASE_PATH + "/authentication/remote-auth";
	}
}
