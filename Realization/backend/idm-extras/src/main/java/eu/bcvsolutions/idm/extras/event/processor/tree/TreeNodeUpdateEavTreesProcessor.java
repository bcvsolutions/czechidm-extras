package eu.bcvsolutions.idm.extras.event.processor.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.SaveAllNodesForSubtreeExecutor;

/**
 * Save all sub-nodes when some node is changed
 *
 * @author Marek Klement
 */
@Component(TreeNodeUpdateEavTreesProcessor.PROCESSOR_NAME)
@Description("Update eav attribute for structure when node changed")
public class TreeNodeUpdateEavTreesProcessor extends CoreEventProcessor<IdmTreeNodeDto> {

	/**
	 * Processor's identifier - has to be unique by module
	 */
	public static final String PROCESSOR_NAME = "tree-node-update-eav-trees-processor";

	@Autowired
	private LongRunningTaskManager longRunningTaskManager;

	@Override
	public EventResult<IdmTreeNodeDto> process(EntityEvent<IdmTreeNodeDto> event) {
		IdmTreeNodeDto nodeChanged = event.getContent();
		SaveAllNodesForSubtreeExecutor lrt = new SaveAllNodesForSubtreeExecutor();
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(SaveAllNodesForSubtreeExecutor.PARAM_NODE_CODE, nodeChanged.getCode());
		lrt.init(configOfLRT);
		Boolean obj = longRunningTaskManager.executeSync(lrt);
		if (obj == null || !obj) {
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_LRT_FAILED);
		}
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public boolean conditional(EntityEvent<IdmTreeNodeDto> event) {
		IdmTreeNodeDto content = event.getContent();
		IdmTreeNodeDto source = event.getOriginalSource();
		if (source == null) {
			return false;
		}
		UUID newParent = content.getParent();
		UUID oldParent = source.getParent();
		if (newParent == null && oldParent == null) {
			return false;
		} else if (newParent == null || oldParent == null) {
			return true;
		} else return !newParent.equals(oldParent);
	}

	@Override
	public int getOrder() {
		return CoreEvent.DEFAULT_ORDER + 1;
	}
}
