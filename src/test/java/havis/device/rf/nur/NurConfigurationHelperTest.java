package havis.device.rf.nur;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.nur.Constants.Antenna;
import havis.device.rf.nur.Constants.LinkFrequency;
import havis.device.rf.nur.Constants.Region;
import havis.device.rf.nur.Constants.TxLevel;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.junit.Before;
import org.junit.Test;

import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurSetup;
import com.nordicid.nurapi.ReflectedPower;

public class NurConfigurationHelperTest {

	private static final Logger log = Logger.getLogger(NurConfigurationHelper.class.getName());
	
	@Mocked
	private NurApi nurApi;
	@Mocked
	private NurSetup nurSetup;
	@Mocked
	private NurRespReaderInfo moduleInfo;
	
	@Before
	public void setup() {
		log.setLevel(Level.ALL);
	}
	
	@Test
	public void testNurConfigurationHelper() {
		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		assertEquals(nurApi, getField(config, "nurApi"));				
	}
	
	@Test
	public void testLoadModuleSetup() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
				nurApi.getReaderInfo();
				result = moduleInfo;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		new Verifications() {
			{
				nurApi.getModuleSetup();
				times = 1;
				nurApi.getReaderInfo();
				times = 1;
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = new Exception("Expected exception");
			}
		};

		try {
			config.loadModuleSetup();
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getMessage().contains("Expected exception"));
		}
	}

	@Test
	public void testSaveModuleSetup() throws Exception {

		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class), anyInt);
				result = nurSetup;

				nurApi.getModuleSetup();
				result = nurSetup;
				nurApi.getReaderInfo();
				result = moduleInfo;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		config.saveModuleSetup();

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceLike(nurSetup), anyInt);
				times = 1;
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class), anyInt);
				result = new Exception("Expected exception");
			}
		};

		try {
			config.saveModuleSetup();
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getMessage().contains("Expected exception"));
		}
	}

	@Test
	public void testGetAntennaState() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		nurSetup.antennaMask = 0b0001;
		assertEquals(config.getAntennaState(Antenna.Antenna1), true);

		nurSetup.antennaMask = 0b0010;
		assertEquals(config.getAntennaState(Antenna.Antenna1), false);
		assertEquals(config.getAntennaState(Antenna.Antenna2), true);

		nurSetup.antennaMask = 0b0100;
		assertEquals(config.getAntennaState(Antenna.Antenna1), false);
		assertEquals(config.getAntennaState(Antenna.Antenna2), false);
		assertEquals(config.getAntennaState(Antenna.Antenna3), true);

		nurSetup.antennaMask = 0b1000;
		assertEquals(config.getAntennaState(Antenna.Antenna1), false);
		assertEquals(config.getAntennaState(Antenna.Antenna2), false);
		assertEquals(config.getAntennaState(Antenna.Antenna3), false);
		assertEquals(config.getAntennaState(Antenna.Antenna4), true);

		nurSetup.antennaMask = 0b0000;
		assertEquals(config.getAntennaState(Antenna.Antenna1), false);
		assertEquals(config.getAntennaState(Antenna.Antenna2), false);
		assertEquals(config.getAntennaState(Antenna.Antenna3), false);
		assertEquals(config.getAntennaState(Antenna.Antenna4), false);
	}

	@Test
	public void testSetAntennaState() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();
		nurSetup.antennaMask = 0;

		/*
		 *  *** TEST: each antenna is enabled leading to corresponding bit in
		 * antenna mask being set ***
		 */
		config.setAntennaState(Antenna.Antenna1, true);
		assertTrue((nurSetup.antennaMask & 0b0001) > 0);

		config.setAntennaState(Antenna.Antenna2, true);
		assertTrue((nurSetup.antennaMask & 0b0010) > 0);

		config.setAntennaState(Antenna.Antenna3, true);
		assertTrue((nurSetup.antennaMask & 0b0100) > 0);

		config.setAntennaState(Antenna.Antenna4, true);
		assertTrue((nurSetup.antennaMask & 0b1000) > 0);

		/*
		 *  *** TEST: each antenna is disabled leading to corresponding bit in
		 * antenna mask not being set ***
		 */
		config.setAntennaState(Antenna.Antenna1, false);
		assertTrue((nurSetup.antennaMask & 0b0001) == 0);

		config.setAntennaState(Antenna.Antenna2, false);
		assertTrue((nurSetup.antennaMask & 0b0010) == 0);

		config.setAntennaState(Antenna.Antenna3, false);
		assertTrue((nurSetup.antennaMask & 0b0100) == 0);

		config.setAntennaState(Antenna.Antenna4, false);
		assertTrue((nurSetup.antennaMask & 0b1000) == 0);

		/*
		 *  *** TEST: exception during save will lead to ImplementationException
		 * to be thrown ***
		 */
		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceLike(nurSetup),
						NurApi.SETUP_ANTMASK);
				result = new Exception("Expected exception");
			}
		};

		try {
			config.setAntennaState(Antenna.Antenna1, true);
			fail();
		} catch (ImplementationException e) {
			assertTrue(e.getMessage().contains(
					"Failed to apply antenna state to value"));
		}

		/*
		 *  *** TEST: state applied to antenna is equal to current state, so save
		 * is not executed and no exception will occur ***
		 */
		try {
			config.setAntennaState(Antenna.Antenna1, false);
		} catch (ImplementationException e) {
			fail();
		}

	}

	@Test
	public void testApplyAntennaSelection() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		/*
		 *  *** TEST: Attempt to select three antennas with only two activated.
		 * The two remain activated and setup is not changed. ***
		 */
		nurSetup.antennaMask = 0b0011;
		final short antCount1 = config.applyAntennaSelection(Arrays.asList(
				(short) 1, (short) 2, (short) 3));

		// only two will be enabled (unchanged)
		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceLike(nurSetup),
						NurApi.SETUP_ANTMASK);
				times = 0;
				assertEquals(nurSetup.antennaMask, 0b0011);
				assertEquals(antCount1, 2);
			}
		};

		/*
		 *  *** TEST: Attempt to select one antenna with two activated. One of
		 * the two is disabled, setup is changed. ***
		 */
		nurSetup.antennaMask = 0b0011;
		final short antCount2 = config.applyAntennaSelection(Arrays
				.asList((short) 1));
		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceLike(nurSetup),
						NurApi.SETUP_ANTMASK);
				times = 1;
				assertEquals(nurSetup.antennaMask, 0b0001);
				assertEquals(antCount2, 1);
			}
		};
	}
	
	@Test
	public void testAutoDetect(@Mocked NurSetup setup) throws Exception {
		final NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		setField(config, "nurSetup", setup);

		final ReflectedPower rfp = new ReflectedPower(0,0,0,0);
		new NonStrictExpectations(config) {{
			nurApi.getReflectedPower();
			result = rfp;
			
			config.saveModuleSetup(anyInt);
			result = null;						
		}};
		
		/*
		 * Test:
		 * 	- autoDetect with region unspecified
		 * Expected:
		 * 	- result being false
		 */
		setField(config, "region", Region.RegionUnspecified);
		setField(setup, "regionId", Region.RegionUnspecified.nurApiRegion);
		assertEquals(false, config.autoDetect((short)1));
		
		/*
		 * Test:
		 * 	- reflected power = -Infinity
		 * Expected:
		 * 	- result being true
		 * 	- saveModuleSetup being called twice 	
		 */
		setField(config, "region", Region.RegionEU);
		setField(setup, "regionId", Region.RegionEU.nurApiRegion);
		rfp.iPart = 0; rfp.qPart = 0; rfp.divider = 1;		
		assertEquals(true, config.autoDetect((short)1));

		/*
		 * Test:
		 * 	- reflected power = 0
		 * Expected:
		 * 	- result being false
		 * 	- saveModuleSetup being called twice 
		 */
		rfp.iPart = 1; rfp.qPart = 0; rfp.divider = 1;		
		assertEquals(false, config.autoDetect((short)1));

		
		/*
		 * Test:
		 * 	- reflected power < 0
		 * Expected:
		 * 	- result being true
		 * 	- saveModuleSetup being called twice 
		 */		
		rfp.iPart = 1; rfp.qPart = 0; rfp.divider = 2;		
		assertEquals(true, config.autoDetect((short)1));

		/*
		 * Test:
		 * 	- reflected power > 0
		 * Expected:
		 * 	- result being false
		 * 	- saveModuleSetup being called twice 
		 */
		rfp.iPart = 1; rfp.qPart = 1; rfp.divider = 1;		
		assertEquals(false, config.autoDetect((short)1));
		
		new Verifications() {{				
			config.saveModuleSetup(NurApi.SETUP_ANTMASK);
			times = 8;
		}};

		/*
		 * Test:
		 * 	- nurApi.getReflectedPowerInfo throwing an exception
		 * Expected
		 * 	- ImplementationException being thrown
		 */
		
		new NonStrictExpectations() {{
			nurApi.getReflectedPower();
			result = new Exception("Expected exception");
			
			config.saveModuleSetup(anyInt);
			result = null;						
		}};
		
		try {
			config.autoDetect((short)1);
			fail("Exception expected.");			
		} catch (ImplementationException ex) { }		
	}
	
	@Test
	public void testGetRegion() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();
		assertEquals(Region.RegionUnspecified, config.getRegion());
		config.setRegion(Region.RegionEU);
		assertEquals(Region.RegionEU, config.getRegion());
	}

	@Test
	public void testSetRegion() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		final Region reg = Region.RegionEU;
		nurSetup.regionId = reg.nurApiRegion;
		setField(config, "region", Region.RegionEU);

		try {
			config.setRegion(reg);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_REGION);
				times = 0;
			}
		};

		nurSetup.regionId = NurApi.REGIONID_FCC;

		try {
			config.setRegion(reg);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_REGION);
				times = 1;

				assertEquals(nurSetup.regionId, reg.nurApiRegion);
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_REGION);
				result = new Exception("Expected exception");
			}
		};

		nurSetup.regionId = NurApi.REGIONID_FCC;
		final int oldVal = nurSetup.regionId;

		try {
			config.setRegion(reg);
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getCause().getMessage()
					.contains("Expected exception"));
		}

		new Verifications() {
			{
				assertEquals(nurSetup.regionId, oldVal);
			}
		};
	}

	@Test
	public void testGetDefaultTxLevel() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();
		nurSetup.txLevel = NurApi.TXLEVEL_20;

		TxLevel txLevel = config.getDefaultTxLevel();
		assertEquals(txLevel.nurApiConstant, nurSetup.txLevel);
	}

	@Test
	public void testSetDefaultTxLevel() throws Exception {

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		final TxLevel tl = TxLevel.TxLevel10;
		nurSetup.txLevel = tl.nurApiConstant;

		try {
			config.setDefaultTxLevel(tl);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_TXLEVEL);
				times = 0;
			}
		};

		nurSetup.txLevel = NurApi.TXLEVEL_11;

		try {
			config.setDefaultTxLevel(tl);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_TXLEVEL);
				times = 1;

				assertEquals(nurSetup.txLevel, tl.nurApiConstant);
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_TXLEVEL);
				result = new Exception("Expected exception");
			}
		};

		nurSetup.txLevel = NurApi.TXLEVEL_11;
		final int oldVal = nurSetup.txLevel;

		try {
			config.setDefaultTxLevel(tl);
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getCause().getMessage()
					.contains("Expected exception"));
		}

		new Verifications() {
			{
				assertEquals(nurSetup.txLevel, oldVal);
			}
		};

		try {
			config.setDefaultTxLevel(TxLevel.TxLevelDefault);
		} catch (ParameterException pex) {
			assertTrue(pex.getMessage().contains(
					"no valid parameter for this function"));
		}
	}

	@Test
	public void testGetTxLevel() throws Exception {

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();
		nurSetup.antPower = new int[] { TxLevel.TxLevel20.nurApiConstant,
				TxLevel.TxLevel22.nurApiConstant };

		try {
			TxLevel txl = config.getTxLevel(Antenna.Antenna1);
			assertEquals(txl.nurApiConstant,
					nurSetup.antPower[Antenna.Antenna1.nurApiAntId]);

		} catch (Exception ex) {
			fail();
		}

		try {
			config.getTxLevel(Antenna.Antenna4);
			fail();
		} catch (Exception ex) {
			assertTrue(ex instanceof ParameterException);
			assertTrue(ex.getMessage().contains(
					"No power information found for antenna"));
		}
	}
	
	@Test
	public void testSetTxLevel() throws Exception {

		/*
		 *  *** Test invocation with non-existent antenna, expect exception to be
		 * thrown ***
		 */
		nurSetup.antPower = new int[] { TxLevel.TxLevel20.nurApiConstant,
				TxLevel.TxLevel21.nurApiConstant };

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		try {
			config.setTxLevel(Antenna.Antenna3, TxLevel.TxLevel10);
			fail();
		} catch (Exception ex) {
			assertTrue(ex instanceof ParameterException);
			assertTrue(ex.getMessage().contains(
					"No power information found for the antenna"));
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(nurSetup, NurApi.SETUP_PERANTPOWER);
				times = 0;
			}
		};

		/*
		 *  *** Test invocation with AntennaAuto, expect TX levels of all
		 * antennas to be changed ***
		 */
		nurSetup.antPower = new int[] { TxLevel.TxLevel20.nurApiConstant,
				TxLevel.TxLevel21.nurApiConstant,
				TxLevel.TxLevel22.nurApiConstant,
				TxLevel.TxLevel23.nurApiConstant };

		config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		try {
			config.setTxLevel(Antenna.Auto, TxLevel.TxLevel10);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				assertEquals(nurSetup.antPower[0],
						TxLevel.TxLevel10.nurApiConstant);
				assertEquals(nurSetup.antPower[1],
						TxLevel.TxLevel10.nurApiConstant);
				assertEquals(nurSetup.antPower[2],
						TxLevel.TxLevel10.nurApiConstant);
				assertEquals(nurSetup.antPower[3],
						TxLevel.TxLevel10.nurApiConstant);

				nurApi.setModuleSetup(withInstanceLike(nurSetup),
						NurApi.SETUP_PERANTPOWER);
				times = 1;
			}
		};

		/*
		 *  *** Test invocation with specific antenna, expect TX level of the
		 * antenna to be changed ***
		 */
		try {
			config.setTxLevel(Antenna.Antenna1, TxLevel.TxLevel12);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceLike(nurSetup),
						NurApi.SETUP_PERANTPOWER);
				times = 2;
				assertEquals(nurSetup.antPower[0],
						TxLevel.TxLevel12.nurApiConstant);
			}
		};

		/*
		 *  *** Test invocation with exception during save, expect exception to
		 * be thrown and old values restored ***
		 */
		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(nurSetup, NurApi.SETUP_PERANTPOWER);
				result = new Exception("Expected exception");
			}
		};

		nurSetup.antPower = new int[] { TxLevel.TxLevel20.nurApiConstant,
				TxLevel.TxLevel21.nurApiConstant,
				TxLevel.TxLevel22.nurApiConstant,
				TxLevel.TxLevel23.nurApiConstant };

		try {
			config.setTxLevel(Antenna.Antenna1, TxLevel.TxLevel25);
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getMessage()
					.contains("Failed to apply antenna power"));
		}

		new Verifications() {
			{
				assertTrue(nurSetup.antPower[0] != TxLevel.TxLevel25.nurApiConstant);
			}
		};

		/*
		 *  *** Test invocation with TX level value already set, method to
		 * return, without saving setup ***
		 */
		nurSetup.antPower = new int[] { TxLevel.TxLevel20.nurApiConstant,
				TxLevel.TxLevel21.nurApiConstant,
				TxLevel.TxLevel22.nurApiConstant,
				TxLevel.TxLevel23.nurApiConstant };

		try {
			config.setTxLevel(Antenna.Antenna1, TxLevel.TxLevel20);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				assertEquals(nurSetup.antPower[0],
						TxLevel.TxLevel20.nurApiConstant);
			}
		};
	}

	@Test
	public void testGetLinkFrequency() throws Exception {

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();
		nurSetup.linkFreq = NurApi.LINK_FREQUENCY_256000;

		LinkFrequency linkFreq = config.getLinkFrequency();
		assertEquals(linkFreq.nurApiConstant, nurSetup.linkFreq);

	}
	
	@Test
	public void testSetLinkFrequency() throws Exception {

		new NonStrictExpectations() {
			{
				nurApi.getModuleSetup();
				result = nurSetup;
			}
		};

		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		final LinkFrequency lf = LinkFrequency.Frequency160kHz;
		nurSetup.linkFreq = lf.nurApiConstant;

		try {
			config.setLinkFrequency(lf);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_LINKFREQ);
				times = 0;
			}
		};

		nurSetup.linkFreq = NurApi.LINK_FREQUENCY_256000;

		try {
			config.setLinkFrequency(lf);
		} catch (Exception ex) {
			fail();
		}

		new Verifications() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_LINKFREQ);
				times = 1;

				assertEquals(nurSetup.linkFreq, lf.nurApiConstant);
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.setModuleSetup(withInstanceOf(NurSetup.class),
						NurApi.SETUP_LINKFREQ);
				result = new Exception("Expected exception");
			}
		};

		nurSetup.linkFreq = NurApi.LINK_FREQUENCY_256000;
		final int oldVal = nurSetup.linkFreq;

		try {
			config.setLinkFrequency(lf);
			fail();
		} catch (Exception ex) {
			assertTrue(ex.getCause().getMessage()
					.contains("Expected exception"));
		}

		new Verifications() {
			{
				assertEquals(nurSetup.linkFreq, oldVal);
			}
		};
	}
	

	@Test
	public void testGetFirmware() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getReaderInfo();
				result = moduleInfo;
			}
		};

		moduleInfo.swVersion = "moduleInfo.swVersion";
		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		try {
			assertEquals(config.getFirmware(), moduleInfo.swVersion);
		} catch (Exception ex) {
			fail();
		}

	}

	@Test
	public void testGetModuleName() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getReaderInfo();
				result = moduleInfo;
			}
		};

		moduleInfo.name = "moduleInfo.name";
		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		try {
			assertEquals(config.getModuleName(), moduleInfo.name);
		} catch (Exception ex) {
			fail();
		}
	}

	@Test
	public void testGetSerialNumber() throws Exception {
		new NonStrictExpectations() {
			{
				nurApi.getReaderInfo();
				result = moduleInfo;
			}
		};

		moduleInfo.serial = "moduleInfo.serial";
		NurConfigurationHelper config = new NurConfigurationHelper(nurApi);
		config.loadModuleSetup();

		try {
			assertEquals(config.getSerialNumber(), moduleInfo.serial);
		} catch (Exception ex) {
			fail();
		}
	}
}
