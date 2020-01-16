package eu.bcvsolutions.idm.extras.security.auth.filter;

import com.google.common.base.Strings;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultFormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.auth.filter.SsoIdmAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.exception.IdmAuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Enabled
@Order(40)
@Component(ExtrasSsoIdmAuthenticationFilter.FILTER_NAME)
public class ExtrasSsoIdmAuthenticationFilter extends SsoIdmAuthenticationFilter {
	private static final Logger LOG = LoggerFactory.getLogger(ExtrasSsoIdmAuthenticationFilter.class);
	static final String FILTER_NAME = "extras-sso-authentication-filter";
	static final String PARAMETER_ATTRIBUTE_CODE = "attribute-code";
	static final String PARAMETER_DEFINITION_CODE = "definition-code";
	static final String PARAMETER_FIELD_CHOOSE = "chooseField";
	static final String PARAMETER_FIELD_IDENTITY = "identity";
	static final String PARAMETER_FIELD_CONTRACT = "contract";
	@Autowired
	private LookupService lookupService;
	@Autowired
	private DefaultFormService formService;

	@Override
	public String getName() {
		return FILTER_NAME;
	}

	@Override
	public boolean isDefaultDisabled() {
		List<String> values = getConfigurationService()
				.getValues(getConfigurationPropertyName("enabled"));
		return !values.isEmpty() && "".equals(values.get(0));
	}

	@Override
	public boolean authorize(String token, HttpServletRequest request, HttpServletResponse response) {
		LOG.debug("Starting SSO filter authorization, value of the SSO header is: [{}]", token);
		if (Strings.isNullOrEmpty(token)) {
			return true;
		}
		// Remove suffix from the token - typically the domain
		String userName = removeUidSuffix(token);
		Set<UUID> uuids = new LinkedHashSet<>();

		List<String> fieldNames = getConfigurationService()
				.getValues(getConfigurationPropertyName(PARAMETER_ATTRIBUTE_CODE));

		String fieldChoose = getConfigurationService()
				.getValue(getConfigurationPropertyName(PARAMETER_FIELD_CHOOSE));

		String definitionCode = getConfigurationService()
				.getValue(getConfigurationPropertyName(PARAMETER_DEFINITION_CODE));

		if (null == definitionCode) {
			throw new IdmAuthenticationException("Definition code is empty.");
		}

		if (fieldChoose.isEmpty() ||
				(!fieldChoose.equals(PARAMETER_FIELD_IDENTITY) && !fieldChoose.equals(PARAMETER_FIELD_CONTRACT))
		) {
			throw new IdmAuthenticationException(MessageFormat.format(
					"Wrong configuration SSO. [{0}] is empty. If you want use SSO authentication, set value as [{1}] or [{2}]",
					getConfigurationPropertyName(PARAMETER_FIELD_CHOOSE), PARAMETER_FIELD_IDENTITY, PARAMETER_FIELD_CONTRACT));
		}


		for (String fieldName : fieldNames) {
			if (StringUtils.isEmpty(fieldName)) {
				return false;
			}
			Page<AbstractDto> owners;
			IdmFormDefinitionDto definition;
			if (PARAMETER_FIELD_CONTRACT.equals(fieldChoose)) {
				definition = formService.getDefinition(IdmIdentityContract.class, definitionCode);
			} else {
				definition = formService.getDefinition(IdmIdentity.class, definitionCode);
			}
			if (null == definition) {
				throw new IdmAuthenticationException(MessageFormat.format("Definition with code [{}] not found", definitionCode));
			}
			IdmFormAttributeDto attribute = formService.getAttribute(definition, fieldName);
			if (null == attribute) {
				throw new IdmAuthenticationException(MessageFormat.format("Attribute with code [{}] not found", fieldName));
			}
			if (PARAMETER_FIELD_CONTRACT.equals(fieldChoose)) {
				owners = formService.findOwners(IdmIdentityContract.class, attribute, userName, null);
				for (AbstractDto owner : owners) {
					uuids.add(((IdmIdentityContractDto) owner).getIdentity());
				}
			} else {
				owners = formService.findOwners(IdmIdentity.class, attribute, userName, null);
				for (AbstractDto owner : owners) {
					uuids.add(owner.getId());
				}
			}
		}

		if (!uuids.isEmpty()) {
			if (uuids.size() > 1) {
				throw new IdmAuthenticationException(MessageFormat.format(
						"The login [{0}] is not unique. SSO authentication is not passable.",
						userName));
			}
			IdmIdentityDto identity = (IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class, uuids.iterator().next());
			if (null == identity) {
				return false;
			}
			return super.authorize(identity.getUsername(), request, response);
		}

		return false;
	}


}
