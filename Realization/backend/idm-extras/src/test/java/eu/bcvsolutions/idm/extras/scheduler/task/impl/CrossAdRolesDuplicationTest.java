package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;

/**
 * @author Roman Kucera
 */
public class CrossAdRolesDuplicationTest {
	@Autowired
	private LongRunningTaskManager lrtManager;

	@Test
	@Transactional
	public void testDuplication() {
		CrossAdRolesDuplication crossAdRolesDuplication = new CrossAdRolesDuplication();
//		crossAdRolesDuplication.init(ImmutableMap.of(CrossAdRolesDuplication.CATALOG_PARAM, ""));

		OperationResult operationResult = lrtManager.executeSync(crossAdRolesDuplication);
		assertNotNull(operationResult);
	}
}