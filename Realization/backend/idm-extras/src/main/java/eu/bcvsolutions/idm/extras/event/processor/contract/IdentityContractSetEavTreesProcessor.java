package eu.bcvsolutions.idm.extras.event.processor.contract;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.IdentityContractProcessor;
import eu.bcvsolutions.idm.core.model.event.IdentityContractEvent.IdentityContractEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

/**
 * Class for setting tree structure or node to eav of contract - identity contract
 *
 * @author Marek Klement
 */
@Component(IdentityContractSetEavTreesProcessor.PROCESSOR_NAME)
@Description("Set structure to eav")
public class IdentityContractSetEavTreesProcessor extends AbstractContractSetEavTreesProcessor<IdmIdentityContractDto>
		implements IdentityContractProcessor {

	/**
	 * Processor's identifier - has to be unique by module
	 */
	public static final String PROCESSOR_NAME = "identity-contract-set-eavs-processor";
	private static final Logger LOG = LoggerFactory.getLogger(IdentityContractSetEavTreesProcessor.class);

	public IdentityContractSetEavTreesProcessor() {
		super(IdentityContractEventType.UPDATE, IdentityContractEventType.CREATE, IdentityContractEventType.EAV_SAVE);
	}

	@Override
	public EventResult<IdmIdentityContractDto> process(EntityEvent<IdmIdentityContractDto> event) {
		IdmIdentityContractDto contract = event.getContent();
		actualProcess(contract);
		LOG.info("Attributes added successfully!");
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public String getName() {
		// processor's identifier - has to be unique by module
		return PROCESSOR_NAME;
	}

	@Override
	public int getOrder() {
		return 600;
	}

	@Override
	public boolean isDefaultDisabled() {
		return true;
	}
}
