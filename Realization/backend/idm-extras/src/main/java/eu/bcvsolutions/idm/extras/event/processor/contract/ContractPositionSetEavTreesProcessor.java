package eu.bcvsolutions.idm.extras.event.processor.contract;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.ContractPositionProcessor;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;


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
		IdmIdentityContractFilter filter = new IdmIdentityContractFilter();
		filter.setId(contractPositionDto.getIdentityContract());
		List<IdmIdentityContractDto> contracts = contractService.find(filter, null).getContent();
		if (contracts.size() == 0) {
			throw new IllegalArgumentException("Contracts should not be null!");
		} else if (contracts.size() > 1) {
			throw new IllegalArgumentException("Contracts should have just one contract!");
		}
		IdmIdentityContractDto contract = contracts.get(0);
		actualProcess(contract);
		LOG.info("Attributes added successfully!");
		return new DefaultEventResult<>(event, this);
	}
}

