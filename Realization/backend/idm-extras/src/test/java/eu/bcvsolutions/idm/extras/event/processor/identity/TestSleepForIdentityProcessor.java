package eu.bcvsolutions.idm.extras.event.processor.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.IdentityProcessor;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.model.event.IdentityEvent.IdentityEventType;

/**
 * Sleep processor for test of status notification
 *
 *
 */
@Component(TestSleepForIdentityProcessor.PROCESSOR_NAME)
@Description("Sleep processor.")
public class TestSleepForIdentityProcessor
		extends CoreEventProcessor<IdmIdentityDto> 
		implements IdentityProcessor {

	public static final String PROCESSOR_NAME = "test-sleep-identity-processor";

	@Autowired
	public TestSleepForIdentityProcessor() {
		super(IdentityEventType.NOTIFY);

	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmIdentityDto> process(EntityEvent<IdmIdentityDto> event) {
		try {
			Thread.sleep(200000);
		} catch (InterruptedException ex) {
			throw new CoreException("processor was interruped", ex);
		}
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public int getOrder() {
		return 10;
	}
}