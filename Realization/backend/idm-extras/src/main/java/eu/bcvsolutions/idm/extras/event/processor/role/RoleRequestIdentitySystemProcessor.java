package eu.bcvsolutions.idm.extras.event.processor.role;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.event.ProvisioningEvent;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.RoleRequestProcessor;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent.RoleRequestEventType;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;

/**
 * Processor for executes provisoning to exchange after some role was added to identity.
 * BEWARE: this processor breaks incremental account management.
 *
 * @author Ondrej Kopr
 * @author Marek Klement
 *
 */
@Component("extrasRoleRequestIdentitySystemProcessor")
@Description("Ensure provisioning to system even no roles for exchange exists in request.")
public class RoleRequestIdentitySystemProcessor extends CoreEventProcessor<IdmRoleRequestDto>
		implements RoleRequestProcessor {

	public static final String PROCESSOR_NAME = "extras-role-request-identity-system-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(RoleRequestIdentitySystemProcessor.class);

	@Autowired
	private AccAccountService accountService;
	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;

	public RoleRequestIdentitySystemProcessor() {
		super(RoleRequestEventType.NOTIFY);
	}

	@Override
	public EventResult<IdmRoleRequestDto> process(EntityEvent<IdmRoleRequestDto> event) {
		IdmRoleRequestDto requestDto = event.getContent();

		UUID systemId = extrasConfiguration.getSystemId();
		if (systemId != null) {
			List<AccAccountDto> accounts = accountService.getAccounts(systemId, requestDto.getApplicant());

			// There will be probably only one account but for sure
			for (AccAccountDto account : accounts) {
				LOG.info("Do provisioning for system and account uid [{}]", account.getUid());
				provisioningService.doProvisioning(account);
			}
		} else {
			LOG.error("System ID for system isn't defined.");
		}

		return new DefaultEventResult<>(event, this);
	}

	@Override
	public boolean conditional(EntityEvent<IdmRoleRequestDto> event) {
		boolean skipProvisioning = this.getBooleanProperty(ProvisioningService.SKIP_PROVISIONING, event.getProperties());

		// For skipped provisioning return false
		if (skipProvisioning) {
			return false;
		}

		return super.conditional(event);
	}

	@Override
	public int getOrder() {
		// After default provisioning order
		return ProvisioningEvent.DEFAULT_PROVISIONING_ORDER + 1000;
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}
}

