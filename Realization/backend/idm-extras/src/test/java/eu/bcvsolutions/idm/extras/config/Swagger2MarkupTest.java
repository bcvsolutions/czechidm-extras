
package eu.bcvsolutions.idm.extras.config;

import org.junit.Test;

import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.test.api.AbstractSwaggerTest;


/**
 * Static swagger generation to sources - will be used as input for swagger2Markup build
 * 
 * @author peter.sourek@bcvsolutions.eu
 *
 */
public class Swagger2MarkupTest extends AbstractSwaggerTest {
	
	@Test
	public void testConvertSwagger() throws Exception {
		super.convertSwagger(ExtrasModuleDescriptor.MODULE_ID);
	}
    
}