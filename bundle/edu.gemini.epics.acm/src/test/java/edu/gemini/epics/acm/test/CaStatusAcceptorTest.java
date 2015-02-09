package edu.gemini.epics.acm.test;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.gemini.epics.acm.CaAttribute;
import edu.gemini.epics.acm.CaAttributeListener;
import edu.gemini.epics.acm.CaException;
import edu.gemini.epics.acm.CaService;
import edu.gemini.epics.acm.CaStatusAcceptor;
import gov.aps.jca.CAException;

// TODO: Create a test IOC to run these tests against it.
// For now, just use the TCS simulator.

public class CaStatusAcceptorTest {

    private static final Logger LOG = Logger.getLogger(CaStatusAcceptorTest.class.getName()); 

    private static final String CA_ADDR_LIST = "127.0.0.1";
	private static final String TOP = "test";
	private static final String SA_NAME = "sad";
	private static final String ATTR1_NAME = "status1";
	private static final String ATTR1_CHANNEL = TOP + ":" + TestSimulator.INTEGER_STATUS;
	private static final String ATTR2_NAME = "status2";
	private static final String ATTR2_CHANNEL = TOP + ":" + TestSimulator.STRING_STATUS;
	private static final long SLEEP_TIME = 2000;

	private static TestSimulator simulator;
	private static CaService caService;
	private boolean updated;

	@BeforeClass
	public static void setUp() throws Exception {
	    simulator = new TestSimulator(TOP);
	    simulator.start();
		CaService.setAddressList(CA_ADDR_LIST);
		caService = CaService.getInstance();
	}

	@AfterClass
	public static void tearDown() throws Exception {
		if (caService != null) {
			caService.unbind();
			caService = null;
		}
		simulator.stop();
	}

	@Test
	public void testCreateService() {
		assertNotNull("Unable to create CaService.", caService);
	}

	@Test
	public void testCreateStatusAcceptor() {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);

		assertNotNull("Unable to create CaStatusAcceptor.", sa);
		
		caService.destroyStatusAcceptor(SA_NAME);
	}

	@Test
	public void testGetStatusAcceptor() {
		CaStatusAcceptor sa1 = caService.createStatusAcceptor(SA_NAME);
		CaStatusAcceptor sa2 = caService.getStatusAcceptor(SA_NAME);

		assertEquals("Retrieved the wrong CaStatusAcceptor.", sa1, sa2);
        
        caService.destroyStatusAcceptor(SA_NAME);
	}

	@Test
	public void testCreateAttribute() throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);
		CaAttribute<Integer> attr = sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);

		assertNotNull("Unable to create status acceptor attribute.", attr);
        
        caService.destroyStatusAcceptor(SA_NAME);
	}

	@Test(expected = CaException.class)
	public void testRejectAttributeCreationWithDifferentType()
			throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);

		sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);
		try {
		    sa.addString(ATTR1_NAME, ATTR1_CHANNEL);
		}
		finally {
		    caService.destroyStatusAcceptor(SA_NAME);
		}
	}

	@Test(expected = CaException.class)
	public void testRejectAttributeCreationWithDifferentChannel()
			throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);
		sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);
		try {
		    sa.addInteger(ATTR1_NAME, ATTR2_CHANNEL);
		}
        finally {
            caService.destroyStatusAcceptor(SA_NAME);
        }
	}

	@Test
	public void testGetAttribute() throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);
		CaAttribute<Integer> attr1 = sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);
		CaAttribute<Integer> attr2 = sa.getIntegerAttribute(ATTR1_NAME);

		assertNotNull("Unable to retrieve status acceptor attribute.", attr2);

		assertEquals("Retrieved the wrong status acceptor attribute.", attr1,
				attr2);
        
        caService.destroyStatusAcceptor(SA_NAME);
	}

	@Test
	public void testGetInfo() throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);
		sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);
		sa.addString(ATTR2_NAME, ATTR2_CHANNEL);

		Set<String> attrSet = sa.getInfo();

		assertNotNull("Unable to retrieve attribute list.", attrSet);

		Set<String> testSet = new HashSet<String>();
		testSet.add(ATTR1_NAME);
		testSet.add(ATTR2_NAME);

		assertEquals("Retrieved bad attribute list.", attrSet, testSet);
        
        caService.destroyStatusAcceptor(SA_NAME);
	}

	@Test
	public void testAttributeMonitor() throws CaException, CAException {
		CaStatusAcceptor sa = caService.createStatusAcceptor(SA_NAME);
		CaAttribute<Integer> attr = sa.addInteger(ATTR1_NAME, ATTR1_CHANNEL);

		attr.addListener(new CaAttributeListener<Integer>() {

			@Override
			public void onValueChange(List<Integer> newVals) {
				updated = true;
			}

			@Override
			public void onValidityChange(boolean newValidity) {
			}
		});
		try {
			Thread.sleep(SLEEP_TIME);
		} catch (InterruptedException e) {
			LOG.warning(e.getMessage());
		}

		assertTrue("Attribute monitor did not receive updates.", updated);
        
        caService.destroyStatusAcceptor(SA_NAME);
	}

}
