package eu.bcvsolutions.idm.extras.event.processor.contract;

import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.ContractPositionProcessor;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.model.event.ContractPositionEvent.ContractPositionEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

/**
 * Class for setting tree structure or node to eav of contract - contract position
 *
 * @author Marek Klement
 */
@Component(ContractPositionSetEavTreesProcessor.PROCESSOR_NAME)
@Description("Set structure to eav")
public class ContractPositionSetEavTreesProcessor
		extends AbstractContractSetEavTreesProcessor<IdmContractPositionDto>
		implements ContractPositionProcessor {

	/**
	 * Processor's identifier - has to be unique by module
	 */
	public static final String PROCESSOR_NAME = "contract-position-set-eav-processor";
	private static final Logger LOG = LoggerFactory.getLogger(ContractPositionSetEavTreesProcessor.class);

	public ContractPositionSetEavTreesProcessor() {
		super(ContractPositionEventType.UPDATE, ContractPositionEventType.CREATE, ContractPositionEventType.DELETE);
	}

	@Autowired
	private IdmIdentityContractService contractService;

	@Override
	public String getName() {
		// processor's identifier - has to be unique by module
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmContractPositionDto> process(EntityEvent<IdmContractPositionDto> event) {
		IdmContractPositionDto contractPositionDto = event.getContent();
		IdmIdentityContractDto contract = contractService.get(contractPositionDto.getIdentityContract());
		actualProcess(contract);
		LOG.info("Attributes added successfully!");
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public boolean conditional(EntityEvent<IdmContractPositionDto> event) {
		return super.conditional(event);
	}
}

