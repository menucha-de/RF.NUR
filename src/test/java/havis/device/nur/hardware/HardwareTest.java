package havis.device.nur.hardware;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import javax.sound.midi.Synthesizer;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.nordicid.nativeserial.NativeSerialTransport;
import com.nordicid.nativeserial.SerialPort;
import com.nordicid.nurapi.CustomExchangeParams;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurCmdCustomExchange;
import com.nordicid.nurapi.NurInventoryExtended;
import com.nordicid.nurapi.NurInventoryExtendedFilter;
import com.nordicid.nurapi.NurRespCustomExchange;
import com.nordicid.nurapi.NurRespInventory;
import com.nordicid.nurapi.NurRespReadData;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;

import havis.device.rf.RFConsumer;
import havis.device.rf.RFDevice;
import havis.device.rf.capabilities.AntennaReceiveSensitivityRangeTableEntry;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.capabilities.TransmitPowerTable;
import havis.device.rf.capabilities.TransmitPowerTableEntry;
import havis.device.rf.common.Baudrate;
import havis.device.rf.common.CommunicationHandler;
import havis.device.rf.common.Environment;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaProperties;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.configuration.ConnectType;
import havis.device.rf.configuration.InventorySettings;
import havis.device.rf.configuration.SelectionMask;
import havis.device.rf.exception.CommunicationException;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.exception.RFControllerException;
import havis.device.rf.nur.NurConfigurationHelper;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.CustomOperation;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.LockOperation.Field;
import havis.device.rf.tag.operation.LockOperation.Privilege;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings("unused")
public class HardwareTest {

	RFDevice rfDevice = null; 
	RFConsumer rfConsumer = null;
	
	class TestConsumer implements RFConsumer {
		@Override
		public void connectionAttempted() {
			
		}

		@Override
		public List<TagOperation> getOperations(TagData arg0) {
			return null;
		}

		@Override
		public void keepAlive() {
			
		}		
	}
	
	@Before
	public void init() {
		if (rfDevice == null)
			rfDevice = new CommunicationHandler();
		
		if (rfConsumer == null)
			rfConsumer = new TestConsumer();
	}
	
	@Test
	public void test000ConnectTest() throws ConnectionException, ImplementationException {
		printTestHeader("connect test");
		rfDevice.openConnection(rfConsumer, 300);
		rfDevice.closeConnection();
	}
	
	@Test
	public void test010SetRegion() throws ConnectionException, ImplementationException, ParameterException {		
		printTestHeader("set region test");		
		rfDevice.openConnection(rfConsumer, 300);
		
		/* set region EU*/
		rfDevice.setRegion("EU");
		
		/* get antenna configurations */
		List<Configuration> antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		
		/* determine the highest possible transmit power index */
		List<Capabilities> capabilities = rfDevice.getCapabilities(CapabilityType.REGULATORY_CAPABILITIES);
		RegulatoryCapabilities regulatoryCapabilities = (RegulatoryCapabilities) capabilities.get(0);		
		TransmitPowerTable transmitPowerTable = regulatoryCapabilities.getTransmitPowerTable();
		TransmitPowerTableEntry transmitPowerTableEntry = transmitPowerTable.getEntryList().get(transmitPowerTable.getEntryList().size() - 1);		
		short transmitPowerIndex = transmitPowerTableEntry.getIndex();
		
		/* set the highest possible transmit power for each antenna */
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			antennaConfiguration.setTransmitPower(transmitPowerIndex);
		}
		/* transfer the modified configurations to the device */
		rfDevice.setConfiguration(antennaConfigurations);

		/* check if transmit power values have been set correctly */
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			assertEquals(transmitPowerIndex, (short)antennaConfiguration.getTransmitPower());			
		}
		
		/* set region "Unspecified" */
		rfDevice.setRegion(Environment.UNSPECIFIED_REGION_ID);
		
		/* check if transmit power has been set back to 0 */
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			assertEquals(S(0), antennaConfiguration.getTransmitPower());			
		}
		
		/* set region back to "EU" */
		rfDevice.setRegion("EU");
		
		/* set the highest possible transmit power */
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			antennaConfiguration.setTransmitPower(transmitPowerIndex);
		}
		rfDevice.setConfiguration(antennaConfigurations);
		
		rfDevice.closeConnection();		
	}
	
	@Test
	public void test020GetAntennaProperties() throws ConnectionException, ImplementationException, ParameterException {
		printTestHeader("get antenna properties test");
		
		rfDevice.openConnection(rfConsumer, 300);
		
		/* get antenna configurations */
		List<Configuration> antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		assertEquals(2, antennaConfigurations.size());
		
		AntennaConfiguration antennaConfiguration1 = (AntennaConfiguration)antennaConfigurations.get(0);
		AntennaConfiguration antennaConfiguration2 = (AntennaConfiguration)antennaConfigurations.get(1);
		
		/* set connect type to false for both antennas */
		antennaConfiguration1.setConnect(ConnectType.FALSE);
		antennaConfiguration2.setConnect(ConnectType.FALSE);		
		rfDevice.setConfiguration(antennaConfigurations);
		
		/* check if antennas are both disconnected */
		AntennaProperties antennaProperties1 = (AntennaProperties) 
			rfDevice.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, s(1), s(0), s(0)).get(0);
		
		AntennaProperties antennaProperties2 = (AntennaProperties) 
				rfDevice.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, s(2), s(0), s(0)).get(0);
		
		assertEquals(false, antennaProperties1.isConnected());
		assertEquals(false, antennaProperties2.isConnected());
		
		/* set connect type to true for both antennas */
		antennaConfiguration1.setConnect(ConnectType.TRUE);
		antennaConfiguration2.setConnect(ConnectType.TRUE);		
		rfDevice.setConfiguration(antennaConfigurations);
		
		antennaProperties1 = (AntennaProperties) 
			rfDevice.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, s(1), s(0), s(0)).get(0);
			
		antennaProperties2 = (AntennaProperties) 
			rfDevice.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, s(2), s(0), s(0)).get(0);
		
		/* check if antennas are both connected */	
		assertEquals(true, antennaProperties1.isConnected());
		assertEquals(true, antennaProperties2.isConnected());
		
		rfDevice.closeConnection();
	}
	
	@Test
	public void test021SetAntennaProperties() throws ConnectionException, ImplementationException, ParameterException {
		printTestHeader("set antenna properties test");
		
		rfDevice.openConnection(rfConsumer, 300);		
		rfDevice.setRegion("EU");
		
		List<Configuration> antennaProperties = rfDevice.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, s(0), s(0), s(0));
		assertEquals(2, antennaProperties.size());
		
		AntennaProperties antProps1 = (AntennaProperties) antennaProperties.get(0);
		AntennaProperties antProps2 = (AntennaProperties) antennaProperties.get(1);
		
		antProps1.setConnected(false);
		antProps2.setConnected(false);
		
		rfDevice.setConfiguration(antennaProperties);
		
		/* get antenna configurations */
		List<Configuration> antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		AntennaConfiguration antennaConfiguration1 = (AntennaConfiguration)antennaConfigurations.get(0);
		AntennaConfiguration antennaConfiguration2 = (AntennaConfiguration)antennaConfigurations.get(1);
		
		assertEquals(ConnectType.FALSE, antennaConfiguration1.getConnect());
		assertEquals(ConnectType.FALSE, antennaConfiguration2.getConnect());
		
		antProps1.setConnected(true);
		antProps2.setConnected(true);
		
		rfDevice.setConfiguration(antennaProperties);
		
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		antennaConfiguration1 = (AntennaConfiguration)antennaConfigurations.get(0);
		antennaConfiguration2 = (AntennaConfiguration)antennaConfigurations.get(1);
		
		assertEquals(ConnectType.TRUE, antennaConfiguration1.getConnect());
		assertEquals(ConnectType.TRUE, antennaConfiguration2.getConnect());
		
		rfDevice.closeConnection();
	}
	
	@Test 
	public void test030ResetConfiguration() throws ConnectionException, ImplementationException, ParameterException {
		printTestHeader("reset configuration test");
		
		rfDevice.openConnection(rfConsumer, 300);
		
		/* set region EU*/
		rfDevice.setRegion("EU");
		
		/* get antenna configurations */
		List<Configuration> antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		
		/* determine the highest possible transmit power index */
		List<Capabilities> capabilities = rfDevice.getCapabilities(CapabilityType.REGULATORY_CAPABILITIES);
		RegulatoryCapabilities regulatoryCapabilities = (RegulatoryCapabilities) capabilities.get(0);		
		TransmitPowerTable transmitPowerTable = regulatoryCapabilities.getTransmitPowerTable();
		TransmitPowerTableEntry transmitPowerTableEntry = transmitPowerTable.getEntryList().get(transmitPowerTable.getEntryList().size() - 1);		
		short transmitPowerIndex = transmitPowerTableEntry.getIndex();
		
		/* set the highest possible transmit power for each antenna */
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			antennaConfiguration.setTransmitPower(transmitPowerIndex);
		}
		/* transfer the modified configurations to the device */
		rfDevice.setConfiguration(antennaConfigurations);
		
		/* reset configuration */
		rfDevice.resetConfiguration();
		
		/* check if region is "unspecified" */
		assertEquals(Environment.UNSPECIFIED_REGION_ID, rfDevice.getRegion());		
		
		/* check if transmit power has been set back to 0 */
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			assertEquals(S(0), antennaConfiguration.getTransmitPower());			
		}
		
		/* set region back to "EU" */
		rfDevice.setRegion("EU");
		
		/* set the highest possible transmit power */
		antennaConfigurations = rfDevice.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, s(0), s(0), s(0));
		for (Configuration configuration : antennaConfigurations) {
			AntennaConfiguration antennaConfiguration = (AntennaConfiguration) configuration;
			antennaConfiguration.setTransmitPower(transmitPowerIndex);
		}
		rfDevice.setConfiguration(antennaConfigurations);
		
		
		rfDevice.closeConnection();
	}

	@Test
	public void test040Inventory() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("inventory test");
		
		rfDevice.openConnection(rfConsumer, 300);
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		TagData tag1 = null, tag2 = null;

		
		/* */		
		antennas.add(s(0));
		for (int i = 0; i < 500; i++)
			System.out.println(rfDevice.execute(antennas, filters, operations).size());
		rfDevice.closeConnection();
		if (true) return;		
		/* */
		
		
		System.out.println("Please place one tag on each antenna.");
		anyKey();
		
		/* run inventory on no antennas */
		System.out.println("Running inventory on no antennas...");
		result = rfDevice.execute(antennas, filters, operations);
		assertEquals(0, result.size());
		System.out.println("No tags found."); 
		
		/* run inventory on both antennas */
		System.out.println("Running inventory on both antennas...");
		antennas.add(s(0));
		result = rfDevice.execute(antennas, filters, operations);
		assertEquals(2, result.size());
		
		tag1 = result.get(0).getAntennaID() == s(1) ? result.get(0) : result.get(1);
		tag2 = result.get(1).getAntennaID() == s(2) ? result.get(1) : result.get(0);
		
		assertNotNull(tag1);
		assertEquals(s(1), tag1.getAntennaID());
		assertNotNull(tag2);
		assertEquals(s(2), tag2.getAntennaID());
		
		System.out.println("Found tag on antenna 1: " + RFUtils.bytesToHex(tag1.getEpc()));
		System.out.println("Found tag on antenna 2: " + RFUtils.bytesToHex(tag2.getEpc()));
		
		/* run inventory on antenna 1*/
		antennas.clear();
		antennas.add(s(1));
		System.out.println("Running inventory on antenna 1...");
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag1.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 1: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		/* run inventory on antenna 2*/
		antennas.clear();
		antennas.add(s(2));
		System.out.println("Running inventory on antenna 2...");
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag2.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 2: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		antennas.clear(); 
		antennas.add(s(0));
		
		/* run inventory on both antennas with tag on antenna 1 */
		System.out.println("Please remove tag from antenna 2.");
		anyKey();
		
		System.out.println("Running inventory on both antennas...");
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag1.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 1: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		/* run inventory on both antennas with tag on antenna 2 */
		System.out.println("Please place tag back on antenna 2 and remove tag from antenna 1.");
		anyKey();
		
		System.out.println("Running inventory on both antennas...");
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag2.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 2: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		/* run inventory on both antennas with filter for tag 1 */
		System.out.println("Please place tag back on antenna 1.");
		anyKey();
		
		antennas.clear(); 
		antennas.add(s(0));
		
		Filter filter = new Filter();
		filter.setBank(RFUtils.BANK_EPC);
		filter.setBitLength(s(tag1.getEpc().length * 8));
		filter.setBitOffset(s(32) /* 2 byte CRC + 2 byte PC */);
		filter.setMatch(true);
		filter.setData(tag1.getEpc());
		filter.setMask(get1Mask(tag1.getEpc().length));		
		filters.add(filter);
		
		/* run inventory with inclusive 1-masked EPC filter matching tag on antenna 1 */
		System.out.println("Running inventory on both antennas with inclusive 1-masked EPC filter matching tag on antenna 1...");
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag1.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 1: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		/* run inventory with inclusive 0-masked EPC filter */
		System.out.println("Running inventory on both antennas with inclusive 0-masked EPC filter...");
		filter.setMask(get0Mask(tag1.getEpc().length));
		result = rfDevice.execute(antennas, filters, operations);						
		assertEquals(2, result.size());		
		for (TagData tagData : result)
			System.out.printf("Found tag on antenna %d: %s\n", tagData.getAntennaID(), RFUtils.bytesToHex(tagData.getEpc()));
		
		/* run inventory with inclusive EPC filter having every other byte 1-masked matching (at least) tag on antenna 1 */
		System.out.println("Running inventory on both antennas with inclusive EPC filter, every other byte 1-masked, matching (at least) tag on antenna 1...");
		boolean[] maskBytes = new boolean[filter.getData().length]; 
		for (int i = 0; i < filter.getData().length; i++)
			maskBytes[i] = i%2 == 1;		
		filter.setMask(getByteMask(maskBytes));
		result = rfDevice.execute(antennas, filters, operations);
		assertTrue(result.size() == 1 || result.size() == 2);
		boolean tag1Found = false;
		for (TagData tagData : result) {
			if (Arrays.equals(tagData.getEpc(), tag1.getEpc())) tag1Found = true;
			System.out.printf("Found tag on antenna %d: %s\n", tagData.getAntennaID(), RFUtils.bytesToHex(tagData.getEpc()));
		}
		assertTrue(tag1Found);
		
		/* run inventory with exclusive 1-masked EPC filter non-matching tag on antenna 1 */
		System.out.println("Running inventory on both antennas with exclusive 1-masked EPC filter matching tag on antenna 1...");
		filter.setMatch(false);
		filter.setMask(get1Mask(tag1.getEpc().length));
		result = rfDevice.execute(antennas, filters, operations);				
		assertEquals(1, result.size());
		assertArrayEquals(tag2.getEpc(), result.get(0).getEpc());
		System.out.println("Found tag on antenna 2: " + RFUtils.bytesToHex(result.get(0).getEpc()));
		
		/* run inventory with exclusive 0-masked EPC filter */
		System.out.println("Running inventory on both antennas with exclusive 0-masked EPC filter...");
		filter.setMask(get0Mask(tag1.getEpc().length));
		result = rfDevice.execute(antennas, filters, operations);						
		assertEquals(2, result.size());		
		for (TagData tagData : result)
			System.out.printf("Found tag on antenna %d: %s\n", tagData.getAntennaID(), RFUtils.bytesToHex(tagData.getEpc()));
		
		/* run inventory with exclusive EPC filter having every other byte 1-masked non-matching (at least) tag on antenna 1 */
		System.out.println("Running inventory on both antennas with exclusive EPC filter, every other byte 1-masked, non-matching (at least) tag on antenna 1...");
		filter.setMask(getByteMask(maskBytes));
		result = rfDevice.execute(antennas, filters, operations);
		assertTrue(result.size() == 0 || result.size() == 1);
		tag1Found = false;
		if (result.size() == 0)
			System.out.println("No tags found.");
		for (TagData tagData : result) {
			if (Arrays.equals(tagData.getEpc(), tag1.getEpc())) tag1Found = true;
			System.out.printf("Found tag on antenna %d: %s\n", tagData.getAntennaID(), RFUtils.bytesToHex(tagData.getEpc()));
		}
		assertTrue(!tag1Found);		
		rfDevice.closeConnection();		
	}
	
	@Test
	public void test050InventorySettings() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		
		printTestHeader("inventory settings test");		
		rfDevice.openConnection(rfConsumer, 300);
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		TagData tag1 = null, tag2 = null;

		System.out.println("Please place one tag on each antenna.");
		anyKey();
		
		InventorySettings invSettings = (InventorySettings) rfDevice.getConfiguration(ConfigurationType.INVENTORY_SETTINGS, s(0), s(0), s(0)).get(0);
		invSettings.getRssiFilter().setMinRssi(s(0));
		invSettings.getRssiFilter().setMaxRssi(s(64));		
		invSettings.getSingulationControl().setQValue(s(1));
		invSettings.getSingulationControl().setRounds(s(1));
		invSettings.getSingulationControl().setSession(s(1));
		invSettings.getSingulationControl().setTransitTime(s(0));		
		invSettings.getSelectionMasks().clear();		
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));
		
		/* test different singulation control settings */
		invSettings.getSingulationControl().setQValue(s(15));
		invSettings.getSingulationControl().setRounds(s(10));
		invSettings.getSingulationControl().setSession(s(1));
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));
		
		result = rfDevice.execute(antennas, filters, operations);		
		System.out.println(result.size());
		
		rfDevice.closeConnection();
		
		/* run inventory on both antennas */
		System.out.println("Running inventory on both antennas with default inventory settings...");
		antennas.add(s(0));
		result = rfDevice.execute(antennas, filters, operations);
		assertEquals(2, result.size());
		
		tag1 = result.get(0).getAntennaID() == s(1) ? result.get(0) : result.get(1);
		tag2 = result.get(1).getAntennaID() == s(2) ? result.get(1) : result.get(0);
		
		assertNotNull(tag1);
		assertEquals(s(1), tag1.getAntennaID());
		assertNotNull(tag2);
		assertEquals(s(2), tag2.getAntennaID());
		
		System.out.println("Found tag on antenna 1: " + RFUtils.bytesToHex(tag1.getEpc()));
		System.out.println("Found tag on antenna 2: " + RFUtils.bytesToHex(tag2.getEpc()));		
		System.out.println("RSSI 1: " + tag1.getRssi());
		System.out.println("RSSI 2: " + tag2.getRssi());
		
		System.out.println("Running inventory with exclusive RSSI filter...");
		
		/* calculate exclusive RSSI filter */
		int minRssi = (tag1.getRssi() < tag2.getRssi() ? tag1.getRssi() : tag2.getRssi()) + 1;
		int maxRssi = (tag1.getRssi() > tag2.getRssi() ? tag1.getRssi() : tag2.getRssi()) - 1;		
		if (minRssi > maxRssi) minRssi = maxRssi;
		
		invSettings.getRssiFilter().setMinRssi(s(minRssi));
		invSettings.getRssiFilter().setMaxRssi(s(maxRssi));
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));
		
		/* run inventory with excluse RSSI filter */
		result = rfDevice.execute(antennas, filters, operations);		
		assertEquals(0, result.size());
		
		System.out.println("Resetting RSSI filter...");
		
		/* reset RSSI filter */
		invSettings.getRssiFilter().setMinRssi(s(0));
		invSettings.getRssiFilter().setMaxRssi(s(64));	
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));
		
		System.out.println("Running inventory with default filter matching tag on antenna 1...");
		
		/* add selection mask to inventory settings */
		SelectionMask sm1 = new SelectionMask();
		sm1.setBank(RFUtils.BANK_EPC);
		sm1.setBitLength(s(tag1.getEpc().length * 8));
		sm1.setBitOffset(s(32) /* 2 byte CRC + 2 byte PC */);
		sm1.setMask(tag1.getEpc());
		invSettings.getSelectionMasks().add(sm1);
		
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));		
		result = rfDevice.execute(antennas, filters, operations);
		assertEquals(1, result.size());
		assertArrayEquals(tag1.getEpc(), result.get(0).getEpc());
		
		System.out.println("Running inventory with additional default filter matching no tags ...");
		
		/* add second selection mask to inventory settings */
		SelectionMask sm2 = new SelectionMask();
		sm2.setBank(RFUtils.BANK_EPC);
		sm2.setBitLength(s(tag2.getEpc().length * 8));
		sm2.setBitOffset(s(32) /* 2 byte CRC + 2 byte PC */);
		sm2.setMask(tag2.getEpc());
		invSettings.getSelectionMasks().add(sm2);
		
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));		
		result = rfDevice.execute(antennas, filters, operations);
		assertEquals(0, result.size());
		
		/* remove default filters */
		invSettings.getSelectionMasks().clear();
		rfDevice.setConfiguration(Arrays.asList((Configuration)invSettings));
		
		rfDevice.closeConnection();
	}
	
	@Test
	public void test100Write() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("write test");
		rfDevice.openConnection(this.rfConsumer, 300);
		
		System.out.println("Please place a non-locked tag on antenna 1.");
		anyKey();
		
		/* write reserved bank */
		final String accsPsw = "CAFEBABE";
		final String killPsw = "DEADBEEF";
		
		System.out.print("Reading kill password: ");
		ReadResult readResultKillPswOld = readSingleTag(1, RFUtils.BANK_PSW, 0, 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultKillPswOld.getResult());
		System.out.println(RFUtils.bytesToHex(readResultKillPswOld.getReadData()));
		
		System.out.print("Reading access password: ");
		ReadResult readResultAccsPswOld = readSingleTag(1, RFUtils.BANK_PSW, 4, 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultAccsPswOld.getResult());
		System.out.println(RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		
		System.out.println("Writing kill password: " + killPsw);
		WriteResult writeResultKillPswNew = writeSingleTag(1, RFUtils.BANK_PSW, 0, killPsw, "writeKillPsw");
		assertEquals(WriteResult.Result.SUCCESS, writeResultKillPswNew.getResult());
		
		System.out.println("Writing access password: " + accsPsw);
		WriteResult writeResultAccsPswNew = writeSingleTag(1, RFUtils.BANK_PSW, 4, accsPsw, "writeAccsPsw");
		assertEquals(WriteResult.Result.SUCCESS, writeResultAccsPswNew.getResult());
		
		System.out.print("Reading kill password: ");
		ReadResult readResultKillPswNew = readSingleTag(1, RFUtils.BANK_PSW, 0, 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultKillPswNew.getResult());
		assertEquals(killPsw, RFUtils.bytesToHex(readResultKillPswNew.getReadData()));
		System.out.println(RFUtils.bytesToHex(readResultKillPswNew.getReadData()));
		
		System.out.print("Reading access password: ");
		ReadResult readResultAccsPswNew = readSingleTag(1, RFUtils.BANK_PSW, 4, 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultAccsPswNew.getResult());		
		assertEquals(accsPsw, RFUtils.bytesToHex(readResultAccsPswNew.getReadData()));
		System.out.println(RFUtils.bytesToHex(readResultAccsPswNew.getReadData()));

		System.out.println("Writing old kill password: " + RFUtils.bytesToHex(readResultKillPswOld.getReadData()));
		WriteResult writeResultKillPswOld = writeSingleTag(1, RFUtils.BANK_PSW, 0, RFUtils.bytesToHex(readResultKillPswOld.getReadData()));
		assertEquals(WriteResult.Result.SUCCESS, writeResultKillPswOld.getResult());
		
		System.out.println("Writing old access password: " + RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		WriteResult writeResultAccsPswOld = writeSingleTag(1, RFUtils.BANK_PSW, 4, RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		assertEquals(WriteResult.Result.SUCCESS, writeResultAccsPswOld.getResult());
		
		/* write TID bank */
		System.out.print("Trying to write TID bank: ");
		WriteResult writeResultTid = writeSingleTag(1, RFUtils.BANK_TID, 0, "E28011052000");		
		assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, writeResultTid.getResult());
		System.out.println(writeResultTid.getResult());

		/* write EPC bank */
		System.out.print("Reading EPC: ");
		ReadResult readResultEpcOld = readSingleTag(1, RFUtils.BANK_EPC, 4, 0);
		assertTrue(readResultEpcOld.getReadData().length >= 12); // at least 96-bit EPC
		System.out.println(RFUtils.bytesToHex(readResultEpcOld.getReadData()));
		
		byte[] epc = Arrays.copyOf(readResultEpcOld.getReadData(), readResultEpcOld.getReadData().length);
		for (int i = 0; i < 8; i++) // invert last 8 bytes of EPC
			epc[epc.length-1-i] = (byte)~epc[epc.length-1-i]; 	
		
		System.out.println("Writing new EPC: " + RFUtils.bytesToHex(epc));
		WriteResult writeResultEpcNew = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(epc));
		assertEquals(WriteResult.Result.SUCCESS, writeResultEpcNew.getResult());
		
		System.out.print("Reading new EPC: ");
		ReadResult readResultEpcNew = readSingleTag(1, RFUtils.BANK_EPC, 4, 0);
		assertArrayEquals(epc, readResultEpcNew.getReadData());
		System.out.println(RFUtils.bytesToHex(readResultEpcNew.getReadData()));
		
		System.out.println("Writing old EPC: " + RFUtils.bytesToHex(readResultEpcOld.getReadData()));
		WriteResult writeResultEpcOld = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(readResultEpcOld.getReadData()));
		assertEquals(WriteResult.Result.SUCCESS, writeResultEpcOld.getResult());		
		
		/* write user bank */
		final String userBankData = "FFEEDDCCBBAA9988776655443322110000112233445566778899AABBCCDDEEFF";
		
		System.out.print("Reading User bank: ");
		ReadResult readResultUserOld = readSingleTag(1, RFUtils.BANK_USR, 0, 0);
		System.out.println(RFUtils.bytesToHex(readResultUserOld.getReadData()));
		
		System.out.println("Writing User bank: " + userBankData);
		WriteResult writeResultUserNew = writeSingleTag(1, RFUtils.BANK_USR, 0, userBankData);
		assertEquals(WriteResult.Result.SUCCESS, writeResultUserNew.getResult());
		
		System.out.print("Reading User bank: ");
		ReadResult readResultUserNew = readSingleTag(1, RFUtils.BANK_USR, 0, 0);
		//assertTrue(condition);
		System.out.println(RFUtils.bytesToHex(readResultUserNew.getReadData()).substring(0, userBankData.length()));
		
		System.out.println("Writing User bank: " + RFUtils.bytesToHex(readResultUserOld.getReadData()));
		WriteResult writeResultUserOld = writeSingleTag(1, RFUtils.BANK_USR, 0, RFUtils.bytesToHex(readResultUserOld.getReadData()));
		assertEquals(WriteResult.Result.SUCCESS, writeResultUserOld.getResult());
		
		/* write reserved bank at wrong address */		
		int maxAddr = getBankLength(1, RFUtils.BANK_PSW);
		assertTrue(maxAddr > 0);
		System.out.print("Writing at wrong address in reserved bank: ");
		WriteResult writeResultRsvFail = writeSingleTag(1, RFUtils.BANK_PSW, maxAddr + 2, "abcd");
		assertEquals(WriteResult.Result.MEMORY_OVERRUN_ERROR, writeResultRsvFail.getResult());
		System.out.println(writeResultRsvFail.getResult());
		
		/* write EPC bank at wrong address */		
		maxAddr = getBankLength(1, RFUtils.BANK_EPC);
		assertTrue(maxAddr > 0);
		System.out.print("Writing at wrong address in EPC bank: ");
		WriteResult writeResultEpcFail = writeSingleTag(1, RFUtils.BANK_EPC, maxAddr + 2, "abcd");
		assertEquals(WriteResult.Result.MEMORY_OVERRUN_ERROR, writeResultEpcFail.getResult());
		System.out.println(writeResultEpcFail.getResult());
		
		/* write USR bank at wrong address */		
		maxAddr = getBankLength(1, RFUtils.BANK_USR);
		assertTrue(maxAddr > 0);
		System.out.print("Writing at wrong address in User bank: ");
		WriteResult writeResultUsrFail = writeSingleTag(1, RFUtils.BANK_USR, maxAddr + 2, "abcd");
		assertEquals(WriteResult.Result.MEMORY_OVERRUN_ERROR, writeResultUsrFail.getResult());
		System.out.println(writeResultUsrFail.getResult());
		
		rfDevice.closeConnection();
	}
	
	@Test
	public void test110Read() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("read test");
		
		rfDevice.openConnection(this.rfConsumer, 300);
		System.out.println("Please place a non-locked tag on antenna 1.");
		anyKey();
		
		/* Read reserved bank */		
		System.out.println("Reading reserved bank.");
		ReadResult readResultKillPsw = readSingleTag(1, RFUtils.BANK_PSW, 0, 4, "readKillPsw");			
		ReadResult readResultAccsPsw = readSingleTag(1, RFUtils.BANK_PSW, 4, 4, "readAccsPsw");		
		assertEquals(ReadResult.Result.SUCCESS, readResultKillPsw.getResult());
		assertEquals(ReadResult.Result.SUCCESS, readResultAccsPsw.getResult());
		assertEquals(4, readResultKillPsw.getReadData().length);
		assertEquals(4, readResultAccsPsw.getReadData().length);		
		System.out.println("Kill   password: " + RFUtils.bytesToHex(readResultKillPsw.getReadData()));
		System.out.println("Access password: " + RFUtils.bytesToHex(readResultAccsPsw.getReadData()));		
		
		/* Read TID bank */
		System.out.println("Reading TID bank.");		
		ReadResult readResultTid = readSingleTag(1, RFUtils.BANK_TID, 0, 6, "readTid");
		assertEquals(6, readResultTid.getReadData().length);
		assertEquals(ReadResult.Result.SUCCESS, readResultTid.getResult());
		byte tidByte0 = readResultTid.getReadData()[0];
		byte tidByte1 = readResultTid.getReadData()[1];
		assertEquals(0xe2, tidByte0 & 0xff);
		assertTrue((tidByte1 & 0b11110000) == 0x0 || (tidByte1 & 0b11110000) == 0x80);		
		System.out.print("TID: " + RFUtils.bytesToHex(readResultTid.getReadData()));
		System.out.println((tidByte1 & 0b11110000) == 0x80 ? " (XTID)" : " (no XTID)");
		
		/* Read EPC bank */
		System.out.println("Reading EPC bank.");
		ReadResult readResultEpc = readSingleTag(1, RFUtils.BANK_EPC, 0, 0, "readEpc");		
		assertTrue(readResultEpc.getReadData().length > 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultEpc.getResult());		
		byte[] crc = new byte[] { readResultEpc.getReadData()[0], readResultEpc.getReadData()[1] };
		byte[] pc = new byte[] { readResultEpc.getReadData()[2], readResultEpc.getReadData()[3] };
		byte[] epc = Arrays.copyOfRange(readResultEpc.getReadData(), 4, readResultEpc.getReadData().length);
		assertEquals(s(0), RFUtils.bytesToShort(crc));		
		System.out.println("EPC: " + RFUtils.bytesToHex(epc));
		System.out.println("CRC: " + RFUtils.bytesToHex(crc));
		System.out.println("PC : " + RFUtils.bytesToHex(pc));
		
		/* Read User bank */
		System.out.println("Reading User bank.");
		ReadResult readResultUsr = readSingleTag(1, RFUtils.BANK_USR, 0, 0, "readUser");
		assertTrue(readResultUsr.getReadData().length > 32);
		assertEquals(ReadResult.Result.SUCCESS, readResultUsr.getResult());		
		System.out.println("User bank data: " + RFUtils.bytesToHex(readResultUsr.getReadData()));
				
		/* Read invalid address from reserved bank */		
		int maxAddr = getBankLength(1, RFUtils.BANK_PSW);
		System.out.println("Reading wrong address from password bank.");
		ReadResult readResultReserved = readSingleTag(1, RFUtils.BANK_PSW, 0, maxAddr + 2);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readResultReserved.getResult());
		System.out.println("Result: " + readResultReserved.getResult());
		
		/* Read invalid address from TID bank */
		maxAddr = getBankLength(1, RFUtils.BANK_TID);
		System.out.println("Reading wrong address from TID bank.");
		readResultTid = readSingleTag(1, RFUtils.BANK_TID, 0, maxAddr + 2);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readResultTid.getResult());
		System.out.println("Result: " + readResultTid.getResult());				
		
		/* Read invalid address from EPC bank */
		maxAddr = getBankLength(1, RFUtils.BANK_EPC);		
		System.out.println("Reading wrong address from EPC bank.");
		readResultEpc = readSingleTag(1, RFUtils.BANK_EPC, 64, 32);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readResultEpc.getResult());
		System.out.println("Result: " + readResultEpc.getResult());				
		
		/* Read invalid address from User bank */
		maxAddr = getBankLength(1, RFUtils.BANK_USR);
		System.out.println("Reading wrong address from User bank.");
		readResultUsr = readSingleTag(1, RFUtils.BANK_USR, 32, 96);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readResultUsr.getResult());
		System.out.println("Result: " + readResultUsr.getResult());
		
		rfDevice.closeConnection();		
	}
	
	@Test
	public void test120Lock() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("lock test");
		
		rfDevice.openConnection(this.rfConsumer, 300);
		System.out.println("Please place a non-locked tag on antenna 1.");
		anyKey();
		final String accsPsw = "CAFEBABE";
		
		/* *** TEST ACCESS PASSWORD LOCK *** */
		
		/* read old access password */
		System.out.print("Reading access password: ");
		ReadResult readResultAccsPswOld = readSingleTag(1, RFUtils.BANK_PSW, 4, 4);
		assertEquals(ReadResult.Result.SUCCESS, readResultAccsPswOld.getResult());
		System.out.println(RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		
		/* write new access password */
		System.out.println("Writing access password: " + accsPsw);
		WriteResult writeResultAccsPswNew = writeSingleTag(1, RFUtils.BANK_PSW, 4, accsPsw, "writeAccsPsw");
		assertEquals(WriteResult.Result.SUCCESS, writeResultAccsPswNew.getResult());
		
		/* lock access password */
		System.out.print("Locking access password: ");
		LockResult lockResultAccsPsw = lockSingleTag(1, Field.ACCESS_PASSWORD, Privilege.LOCK, "lockAccsPsw", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, lockResultAccsPsw.getResult());
		System.out.println(lockResultAccsPsw.getResult());
		
		/* read locked access password (3 attempts)*/
		System.out.print("Trying to read access password (w/o password): ");
		ReadResult readResultAccsPswFail1 = readSingleTag(1, RFUtils.BANK_PSW, 4, 4);
		assertEquals(ReadResult.Result.MEMORY_LOCKED_ERROR, readResultAccsPswFail1.getResult());
		System.out.println(readResultAccsPswFail1.getResult());
		
		System.out.print("Trying to read access password (wrong password): ");
		ReadResult readResultAccsPswFail2 = readSingleTag(1, RFUtils.BANK_PSW, 4, 4, null, "aabbccdd");
		assertEquals(ReadResult.Result.INCORRECT_PASSWORD_ERROR, readResultAccsPswFail2.getResult());
		System.out.println(readResultAccsPswFail2.getResult());
		
		System.out.print("Trying to read access password (correct password): ");
		ReadResult readResultAccsPswNew = readSingleTag(1, RFUtils.BANK_PSW, 4, 4, null, accsPsw);
		assertEquals(ReadResult.Result.SUCCESS, readResultAccsPswNew.getResult());
		System.out.println(readResultAccsPswNew.getResult());
		
		/* unlock access password */
		System.out.print("Unlocking access password.");
		LockResult unlockResultAccsPsw = lockSingleTag(1, Field.ACCESS_PASSWORD, Privilege.UNLOCK, "unlockAccsPsw", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, unlockResultAccsPsw.getResult());
		System.out.println(unlockResultAccsPsw.getResult());
		
		/* *** TEST EPC BANK LOCK *** */
		
		/* lock EPC bank */
		System.out.print("Locking EPC bank: ");
		LockResult lockResultEpc = lockSingleTag(1, Field.EPC_MEMORY, Privilege.LOCK, "lockEpc", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, lockResultEpc.getResult());
		System.out.println(lockResultEpc.getResult());
		
		/* read locked EPC (3 attempts)*/
		System.out.print("Trying to read EPC (w/o password): ");
		ReadResult readResultEpc1 = readSingleTag(1, RFUtils.BANK_EPC, 0, 0);
		assertEquals(ReadResult.Result.SUCCESS, readResultEpc1.getResult());
		System.out.println(readResultEpc1.getResult());
		
		System.out.print("Trying to read EPC (wrong password): ");
		ReadResult readResultEpc2 = readSingleTag(1, RFUtils.BANK_EPC, 0, 0, null, "aabbccdd");
		assertEquals(ReadResult.Result.SUCCESS, readResultEpc2.getResult());
		System.out.println(readResultEpc2.getResult());
		
		System.out.print("Trying to read EPC (correct password): ");
		ReadResult readResultEpc3 = readSingleTag(1, RFUtils.BANK_EPC, 0, 0, null, accsPsw);
		assertEquals(ReadResult.Result.SUCCESS, readResultEpc3.getResult());
		System.out.println(readResultEpc3.getResult());
		
		byte[] randomBytes = new byte[8]; 
		new Random().nextBytes(randomBytes);
		
		/* write locked EPC (4 attempts)*/		
		System.out.print("Trying to write EPC (w/o password): ");
		WriteResult writeResultEpc1 = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(randomBytes));
		assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, writeResultEpc1.getResult());
		System.out.println(writeResultEpc1.getResult());
		
		System.out.print("Trying to write EPC (wrong password): ");
		WriteResult writeResultEpc2 = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(randomBytes), null, "aabbccdd");
		assertEquals(WriteResult.Result.INCORRECT_PASSWORD_ERROR, writeResultEpc2.getResult());
		System.out.println(writeResultEpc2.getResult());
		
		System.out.print("Trying to write new EPC (correct password): ");
		WriteResult writeResultEpc3 = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(randomBytes), null, accsPsw);
		assertEquals(WriteResult.Result.SUCCESS, writeResultEpc3.getResult());
		System.out.println(writeResultEpc3.getResult());
		
		System.out.print("Trying to restore old EPC (correct password): ");
		WriteResult writeResultEpc4 = writeSingleTag(1, RFUtils.BANK_EPC, 4, RFUtils.bytesToHex(readResultEpc1.getReadData()), null, accsPsw);
		assertEquals(WriteResult.Result.SUCCESS, writeResultEpc4.getResult());
		System.out.println(writeResultEpc4.getResult());
		
		/* unlock EPC bank */
		System.out.print("Unlocking EPC bank: ");
		LockResult unlockResultEpc = lockSingleTag(1, Field.EPC_MEMORY, Privilege.UNLOCK, "unlockEpc", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, unlockResultEpc.getResult());
		System.out.println(unlockResultEpc.getResult());
		
		/* *** TEST USER BANK LOCK *** */
		
		/* lock USER bank */
		System.out.print("Locking USER bank: ");
		LockResult lockResultUser = lockSingleTag(1, Field.USER_MEMORY, Privilege.LOCK, "lockUsr", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, lockResultUser.getResult());
		System.out.println(lockResultUser.getResult());
		
		/* read locked USER bank (3 attempts)*/
		System.out.print("Trying to read USER bank (w/o password): ");
		ReadResult readResultUsr1 = readSingleTag(1, RFUtils.BANK_USR, 0, 0);
		assertEquals(ReadResult.Result.SUCCESS, readResultUsr1.getResult());
		System.out.println(readResultEpc1.getResult());
		
		System.out.print("Trying to read USER bank (wrong password): ");
		ReadResult readResultUsr2 = readSingleTag(1, RFUtils.BANK_USR, 0, 0, null, "aabbccdd");
		assertEquals(ReadResult.Result.INCORRECT_PASSWORD_ERROR, readResultUsr2.getResult());
		System.out.println(readResultUsr2.getResult());
		
		System.out.print("Trying to read USER bank (correct password): ");
		ReadResult readResultUsr3 = readSingleTag(1, RFUtils.BANK_USR, 0, 0, null, accsPsw);
		assertEquals(ReadResult.Result.SUCCESS, readResultUsr3.getResult());
		System.out.println(readResultUsr3.getResult());
		
		new Random().nextBytes(randomBytes);
		
		/* write locked EPC (4 attempts)*/		
		System.out.print("Trying to write USER bank (w/o password): ");
		WriteResult writeResultUsr1 = writeSingleTag(1, RFUtils.BANK_USR, 0, RFUtils.bytesToHex(randomBytes));
		assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, writeResultUsr1.getResult());
		System.out.println(writeResultUsr1.getResult());
		
		System.out.print("Trying to write USER bank (wrong password): ");
		WriteResult writeResultUsr2 = writeSingleTag(1, RFUtils.BANK_USR, 0, RFUtils.bytesToHex(randomBytes), null, "aabbccdd");
		assertEquals(WriteResult.Result.INCORRECT_PASSWORD_ERROR, writeResultUsr2.getResult());
		System.out.println(writeResultUsr2.getResult());
		
		System.out.print("Trying to write new USER bank (correct password): ");
		WriteResult writeResultUsr3 = writeSingleTag(1, RFUtils.BANK_USR, 0, RFUtils.bytesToHex(randomBytes), null, accsPsw);
		assertEquals(WriteResult.Result.SUCCESS, writeResultUsr3.getResult());
		System.out.println(writeResultUsr3.getResult());
		
		System.out.print("Trying to restore old USER bank (correct password): ");
		WriteResult writeResultUsr4 = writeSingleTag(1, RFUtils.BANK_USR, 0, RFUtils.bytesToHex(readResultUsr1.getReadData()), null, accsPsw);
		assertEquals(WriteResult.Result.SUCCESS, writeResultUsr4.getResult());
		System.out.println(writeResultUsr4.getResult());
		
		/* unlock USER bank */
		System.out.print("Unlocking EPC bank: ");
		LockResult unlockResultUsr = lockSingleTag(1, Field.USER_MEMORY, Privilege.UNLOCK, "unlockUsr", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, unlockResultUsr.getResult());
		System.out.println(unlockResultUsr.getResult());
		
		/* *** PERMA LOCK TEST */
		System.out.println("The following tests will PERMANENTLY lock/unlock the tag on antenna 1.");
		anyKey();
		
		/* perma-lock EPC */
		System.out.print("Perma-locking EPC bank: ");
		LockResult permaLockResultEpc = lockSingleTag(1, Field.EPC_MEMORY, Privilege.PERMALOCK, "permaLockEpc", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, permaLockResultEpc.getResult());
		System.out.println(permaLockResultEpc.getResult());
		
		/* try unlock EPC bank */
		System.out.print("Trying to unlock EPC bank: ");
		LockResult unlockResultEpcFail = lockSingleTag(1, Field.EPC_MEMORY, Privilege.UNLOCK, "unlockEpc", accsPsw);
		assertEquals(LockResult.Result.MEMORY_LOCKED_ERROR, unlockResultEpcFail.getResult());
		System.out.println(unlockResultEpcFail.getResult());
		
		/* perma-unlock user bank */
		System.out.print("Perma-unlocking USER bank: ");
		LockResult permaUnlockResultUsr = lockSingleTag(1, Field.USER_MEMORY, Privilege.PERMAUNLOCK, "permaUnlockUsr", accsPsw);
		assertEquals(LockResult.Result.SUCCESS, permaUnlockResultUsr.getResult());
		System.out.println(permaUnlockResultUsr.getResult());
		
		/* try unlock EPC bank */
		System.out.print("Trying to lock USER bank: ");
		LockResult lockResultUsrFail = lockSingleTag(1, Field.USER_MEMORY, Privilege.LOCK, "lockUsr", accsPsw);
		assertEquals(LockResult.Result.MEMORY_LOCKED_ERROR, lockResultUsrFail.getResult());
		System.out.println(lockResultUsrFail.getResult());
		
		/* write old access password */
		System.out.println("Restoring old access password: " + RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		WriteResult writeResultAccsPswOld = writeSingleTag(1, RFUtils.BANK_PSW, 4, RFUtils.bytesToHex(readResultAccsPswOld.getReadData()));
		assertEquals(WriteResult.Result.SUCCESS, writeResultAccsPswOld.getResult());
				
		rfDevice.closeConnection();	
	}

	@Test
	public void test130Kill() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("kill test");
		
		rfDevice.openConnection(this.rfConsumer, 300);
		System.out.println("Please place a non-locked tag on antenna 1.");
		anyKey();
		final String killPsw = "DEADBEEF";
		
		/* *** TEST ACCESS PASSWORD LOCK *** */
		
		/* write kill password */
		System.out.println("Writing kill password: " + killPsw);
		WriteResult writeResultKillPsw = writeSingleTag(1, RFUtils.BANK_PSW, 0, killPsw, "writeKillPsw");
		assertEquals(WriteResult.Result.SUCCESS, writeResultKillPsw.getResult());
		
		System.out.println("The following tests will PERMANENTLY destroy the tag on antenna 1.");
		anyKey();
		
		System.out.print("Trying to kill tag (w/o password): ");
		KillResult killResult1 = killSingleTag(1, "kill1", null);
		assertEquals(KillResult.Result.ZERO_KILL_PASSWORD_ERROR, killResult1.getResult());
		System.out.println(killResult1.getResult());
		
		System.out.print("Trying to kill tag (wrong password): ");
		KillResult killResult2 = killSingleTag(1, "kill2", "DEAFBEAD");
		assertEquals(KillResult.Result.INCORRECT_PASSWORD_ERROR, killResult2.getResult());
		System.out.println(killResult2.getResult());
		
		System.out.print("Trying to kill tag (correct password): ");
		KillResult killResult3 = killSingleTag(1, "kill3", killPsw);
		assertEquals(KillResult.Result.SUCCESS, killResult3.getResult());
		System.out.println(killResult3.getResult());
		
		rfDevice.closeConnection();	
	}
	
	@Test
	public void test200ETBv2() throws ConnectionException, RFControllerException {
		printTestHeader("ETB v2 test");
		rfDevice.openConnection(this.rfConsumer, 300);
		
		String cmdInput = "E00248000000";
		int bitCount = 44;
		
		System.out.println("Please place an ETBv2 tag on antenna 1.");
		anyKey();
		
		System.out.println("Running custom command and read commands on ETBv2 tag (10 rounds, max. 10 inventories per round): ");
		
		for (int i = 0; i < 100; i++) {
			OperationResult[] results = null;
			
			for (int j = 0; j < 10; j++) {
				results = readETBv2(1, cmdInput, bitCount, "cust-cmd" + i);				
				System.out.print('.');
				if (results != null) break;							
			}
			System.out.println();
			
			if (results == null) System.out.println("No tag.");			
			else {				
				CustomResult custRes = (CustomResult) results[0];				
				
				ReadResult readResEpc = (ReadResult) results[1];
				ReadResult readResTid = (ReadResult) results[2];				
				
				//assertEquals(CustomResult.Result.SUCCESS, custRes.getResult());				
				if (custRes.getResult() == CustomResult.Result.SUCCESS) {
					double value = RFUtils.bytesToShort(custRes.getResultData());
					double resistence = ((0.0145 + (value * 1.599 / 4095.0) / 74.0) / 0.00002);
					double temperature = (-1000 * 0.0039083 + Math.sqrt((1000 * 0.003908) * (1000 * 0.003908) - 4000 * (-0.0000005775) * (1000 - resistence))) / (2000 * (-0.0000005775));
					
					System.out.println("x" + RFUtils.bytesToHex(custRes.getResultData()) + "=" + value);
					System.out.printf("R=%.2f Ω\n", resistence); 
					System.out.printf("θ=%.2f °C\n", temperature);
					System.out.println("EPC=" + RFUtils.bytesToHex(readResEpc.getReadData()));
					System.out.println("TID=" + RFUtils.bytesToHex(readResTid.getReadData()));
				}
			}											
		}
		rfDevice.closeConnection();	
	}
	
	public void test999InventoryPerformance() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		printTestHeader("Performance test");
		
		rfDevice.openConnection(rfConsumer, 300);
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		
		ReadOperation rdOp = new ReadOperation();
		rdOp.setBank(RFUtils.BANK_TID);
		rdOp.setOffset((short)3);
		rdOp.setOperationId("td-tid");
		rdOp.setLength((short)1);
		
		operations.add(rdOp);
		
		List<Filter> filters = new ArrayList<>();
		
		// 300833B2DDD9014000000002
		// E28011052000328DDF740012
		
		Filter filter1= new Filter();
		filter1.setBank(RFUtils.BANK_EPC);
		filter1.setBitLength((short)16);
		filter1.setData(new byte[] { (byte)0xff, (byte)0x08 });
		filter1.setMask(new byte[] { (byte)0xff, (byte)0xff });
		filter1.setBitOffset((short)32);
		filter1.setMatch(false);		
		//filters.add(filter1);
		
		Filter filter2 = new Filter();
		filter2.setBank(RFUtils.BANK_EPC);
		filter2.setBitLength((short)16);
		filter2.setData(new byte[] { (byte)0x33, (byte)0xb2 });
		filter2.setMask(new byte[] { (byte)0xff, (byte)0xff });
		filter2.setBitOffset((short)48);
		filter2.setMatch(false);
		//filters.add(filter2);
		
		Filter filter3 = new Filter();
		filter3.setBank(RFUtils.BANK_TID);
		filter3.setBitLength((short)48);		
		filter3.setData(RFUtils.hexToBytes("E28011052000328DDF740012"));
		filter3.setMask(RFUtils.hexToBytes("FFFFFFFF0000000000000000"));		
		filter3.setBitOffset((short)0);
		filter3.setMatch(true);
		filters.add(filter3);
		
		List<TagData> result;
		TagData tag1 = null, tag2 = null;

		System.out.println("Please place one tag on antenna 1.");
		anyKey();
		
		antennas.clear();
		antennas.add(s(1));
		System.out.println("Running 50 inventories on antenna 1...");
		
		long now = 0;
		
		for (int i = 0; i < 50; i++) {
			now = new Date().getTime();
			List<TagData> execResult = rfDevice.execute(antennas, filters, operations);
			
			if (execResult.size() > 0 && execResult.get(0).getResultList().size() > 0) {
				System.out.println("RES 0/" + execResult.get(0).getResultList().size() +": " + RFUtils.bytesToHex(((ReadResult)execResult.get(0).getResultList().get(0)).getReadData()));
			}
			
			System.out.println(new Date().getTime() - now + ": " + execResult.size());
		}
	}

	public void test999MultiFilter() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		rfDevice.openConnection(rfConsumer, 300);
			
		final short EPC96_LEN = (short)96;
		final short EPC96_OFFS = (short)32;
		
		final short EPC128_LEN = (short)128;
		final short EPC128_OFFS = (short)32;
		
		final short TID_LEN = (short)96;
		final short TID_OFFS = (short)0;
		
		Filter epcFilter96 = new Filter();
		epcFilter96.setBank((short)1);
		epcFilter96.setBitOffset(EPC96_OFFS);
		epcFilter96.setBitLength(EPC96_LEN);   
		epcFilter96.setData(RFUtils.hexToBytes("300833B2DDD9014000000001"));
		epcFilter96.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
		epcFilter96.setMatch(true);
		
		Filter epcFilter128 = new Filter();
		epcFilter128.setBank((short)1);
		epcFilter128.setBitOffset(EPC128_OFFS);
		epcFilter128.setBitLength(EPC128_LEN);   
		epcFilter128.setData(RFUtils.hexToBytes("F5C75AF0C30C30C30C30C30C30C30C61"));
		epcFilter128.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
		epcFilter128.setMatch(true);
		
		Filter tidFilter = new Filter();
		tidFilter.setBank((short)2);
		tidFilter.setBitOffset(TID_OFFS);
		tidFilter.setBitLength(TID_LEN);
		tidFilter.setData(RFUtils.hexToBytes("E280110520007294F98008AF"));
		tidFilter.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFF"));
		tidFilter.setMatch(true);
		
		Filter epcFilter128_1 = new Filter();
		epcFilter128_1.setBank((short)1);
		epcFilter128_1.setBitOffset(EPC128_OFFS);
		epcFilter128_1.setBitLength(EPC96_LEN);   
		epcFilter128_1.setData(RFUtils.hexToBytes("F5C75AF0C30C30C30C30C30C"));
		epcFilter128_1.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFF"));
		epcFilter128_1.setMatch(true);
		
		Filter epcFilter128_2  = new Filter();
		epcFilter128_2.setBank((short)1);
		epcFilter128_2.setBitOffset( (short) (EPC128_OFFS + EPC96_LEN));
		epcFilter128_2.setBitLength( (short) 32);   
		epcFilter128_2.setData(RFUtils.hexToBytes("30C30C61"));
		epcFilter128_2.setMask(RFUtils.hexToBytes("FFFFFFFF"));
		epcFilter128_2.setMatch(true);
		
		
		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("2");
		rdOp.setBank((short)1);
		rdOp.setOffset((short)2);
		rdOp.setLength((short)8);
		rdOp.setPassword((short)0);
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		
		antennas.add((short)0);
		operations.add(rdOp);
		
		filters.add(epcFilter128);		
		filters.add(tidFilter);		
		filters.add(epcFilter128);		
		filters.add(tidFilter);						
		
		RFUtils.printResult(rfDevice.execute(antennas, filters, operations), false);		
		rfDevice.closeConnection();
	}

	@Test
	public void test999UserbankFilter() throws ConnectionException, ImplementationException, ParameterException, CommunicationException {
		rfDevice.openConnection(rfConsumer, 300);
		
		for (int run = 0; run < 20; run++) {
			
			List<Filter> filters = new ArrayList<>();

			int totalLen = 0;
			int usrFilterLen = 255;
			int epcFilterLen = 255;
			int tidFilterLen = 256;
			for (int i = 0; i < 2; i++) {
				
				
				Filter usrFilter = new Filter();
				usrFilter.setBank(RFUtils.BANK_USR);
				usrFilter.setBitOffset(s(usrFilterLen * i));
				usrFilter.setBitLength(s(usrFilterLen));   
				usrFilter.setData(RFUtils.hexToBytes("000000000000000000000000000000000000000000000000000000000000000000"));
				usrFilter.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
				usrFilter.setMatch(true);
				
				Filter epcFilter = new Filter();
				epcFilter.setBank(RFUtils.BANK_EPC);
				epcFilter.setBitOffset(s(epcFilterLen * i));
				epcFilter.setBitLength(s(epcFilterLen));   
				epcFilter.setData(RFUtils.hexToBytes("000000000000000000000000000000000000000000000000000000000000000000"));
				epcFilter.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
				epcFilter.setMatch(true);
				
				Filter tidFilter = new Filter();
				tidFilter.setBank(RFUtils.BANK_EPC);
				tidFilter.setBitOffset(s(tidFilterLen * i));
				tidFilter.setBitLength(s(tidFilterLen));   
				tidFilter.setData(RFUtils.hexToBytes("000000000000000000000000000000000000000000000000000000000000000000"));
				tidFilter.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
				tidFilter.setMatch(true);
				
				totalLen += usrFilter.getBitLength();
				
				filters.add(usrFilter);
				filters.add(epcFilter);
				filters.add(tidFilter);
			}
			System.out.println("TOTAL len = " + totalLen);
					
			Filter usrFilter1 = new Filter();
			usrFilter1.setBank(RFUtils.BANK_USR);
			usrFilter1.setBitOffset(s(0));
			usrFilter1.setBitLength(s(200));   
			usrFilter1.setData(RFUtils.hexToBytes("000000000000000000000000000000000000000000000000000000000000000000"));
			usrFilter1.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
			usrFilter1.setMatch(true);
			
			Filter usrFilter2 = new Filter();
			usrFilter2.setBank(RFUtils.BANK_USR);
			usrFilter2.setBitOffset(s(200));
			usrFilter2.setBitLength(s(200));   
			usrFilter2.setData(RFUtils.hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"));
			usrFilter2.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
			usrFilter2.setMatch(true);
			
			Filter usrFilter3 = new Filter();
			usrFilter3.setBank(RFUtils.BANK_USR);
			usrFilter3.setBitOffset(s(400));
			usrFilter3.setBitLength(s(100));   
			usrFilter3.setData(RFUtils.hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"));
			usrFilter3.setMask(RFUtils.hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
			usrFilter3.setMatch(true);
			
			List<Short> antennas = new ArrayList<>();
			List<TagOperation> operations = new ArrayList<>();
			
			antennas.add((short)0);		
			
			RFUtils.printResult(rfDevice.execute(antennas, filters, operations), false);
		}
		
		rfDevice.closeConnection();
	}
	
	private int getBankLength(int antenna, short bank) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {		
		int length = 0;
		int offset = 0;
		boolean overrun = false;
		boolean firstTry = true;
		switch(bank) {
			case RFUtils.BANK_PSW: length = 8; break;
			case RFUtils.BANK_TID: length = 24; break;
			case RFUtils.BANK_EPC: length = 20; break;
			case RFUtils.BANK_USR: length = 64; break;
			default: return 0;
		}
		
		while (length > 1) {
			Result res = readSingleTag(antenna, bank, s(offset), length).getResult();
			
			switch (res) {
			case SUCCESS:
				if (!overrun) {					
					/* Optimization: On first try, increase length only by one word. 
					 * If read then fails, the correct length was already set. */
					if (firstTry && readSingleTag(antenna, bank, s(offset), length + 2).getResult() == Result.MEMORY_OVERRUN_ERROR) {
						return length;
					} 					
					firstTry = false;
					length *= 2;
				}
				else {
					offset = offset + length;
					length = (int)length / 2;
				}
				break;
			case MEMORY_OVERRUN_ERROR:
				overrun = true;
				length = (int)length / 2;
				break;
			default: 
				return 0;
			}
			
			if (length > 1 && length % 2 != 0) length++;
		}		
		return offset;
	}
			
	private ReadResult readSingleTag(int antenna, short bank, int offsetBytes, int lengthBytes) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		return readSingleTag(antenna, bank, offsetBytes, lengthBytes, null, null);
	}
		
	private ReadResult readSingleTag(int antenna, short bank, int offsetBytes, int lengthBytes, String operationId) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		return readSingleTag(antenna, bank, offsetBytes, lengthBytes, operationId, null);
	}
		
	private ReadResult readSingleTag(int antenna, short bank, int offsetBytes, int lengthBytes, String operationId, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		ReadOperation readOperation = new ReadOperation();
		readOperation.setBank(bank);
		readOperation.setOffset(bytesToWords(offsetBytes));
		readOperation.setLength(bytesToWords(lengthBytes));
		readOperation.setOperationId(operationId);
		if (password != null)
			readOperation.setPassword(RFUtils.bytesToInt(RFUtils.hexToBytes(password)));
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		
		antennas.add(s(antenna));
		operations.add(readOperation);
				
		result = rfDevice.execute(antennas, filters, operations);
		
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).getResultList().size());
		assertEquals(ReadResult.class, result.get(0).getResultList().get(0).getClass());
		assertEquals(operationId, ((ReadResult)result.get(0).getResultList().get(0)).getOperationId());
		return (ReadResult)result.get(0).getResultList().get(0);
	}
	
	private WriteResult writeSingleTag(int antenna, short bank, int offsetBytes, String hexStr) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException { 
		return writeSingleTag(antenna, bank, offsetBytes, hexStr, null, null);
	}
	
	private WriteResult writeSingleTag(int antenna, short bank, int offsetBytes, String hexStr, String operationId) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException { 
		return writeSingleTag(antenna, bank, offsetBytes, hexStr, operationId, null);
	}
	
	private WriteResult writeSingleTag(int antenna, short bank, int offsetBytes, String hexStr, String operationId, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		WriteOperation writeOperation = new WriteOperation();
		writeOperation.setBank(bank);
		writeOperation.setOffset(bytesToWords(offsetBytes));
		writeOperation.setData(RFUtils.hexToBytes(hexStr));
		writeOperation.setOperationId(operationId);
		if (password != null) 
			writeOperation.setPassword(RFUtils.bytesToInt(RFUtils.hexToBytes(password)));
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		
		antennas.add(s(antenna));
		operations.add(writeOperation);
				
		result = rfDevice.execute(antennas, filters, operations);
		
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).getResultList().size());
		assertEquals(WriteResult.class, result.get(0).getResultList().get(0).getClass());
		assertEquals(operationId, ((WriteResult)result.get(0).getResultList().get(0)).getOperationId());
		return (WriteResult)result.get(0).getResultList().get(0);
	}

	private LockResult lockSingleTag(int antenna, Field field, Privilege privilege, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		return lockSingleTag(antenna, field, privilege, null, password);
	}
	
	private LockResult lockSingleTag(int antenna, Field field, Privilege privilege, String operationId, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		LockOperation lockOperation = new LockOperation();
		lockOperation.setField(field);
		lockOperation.setPrivilege(privilege);
		lockOperation.setOperationId(operationId);
		lockOperation.setPassword(RFUtils.bytesToInt(RFUtils.hexToBytes(password)));
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		
		antennas.add(s(antenna));
		operations.add(lockOperation);
				
		result = rfDevice.execute(antennas, filters, operations);
		
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).getResultList().size());
		assertEquals(LockResult.class, result.get(0).getResultList().get(0).getClass());
		assertEquals(operationId, ((LockResult)result.get(0).getResultList().get(0)).getOperationId());
		return (LockResult)result.get(0).getResultList().get(0);
	}
	
	private KillResult killSingleTag(int antenna, String operationId, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		
		KillOperation killOperation = new KillOperation();
		killOperation.setOperationId(operationId);
		
		if (password != null)
			killOperation.setKillPassword(RFUtils.bytesToInt(RFUtils.hexToBytes(password)));
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;		

		antennas.add(s(antenna));
		operations.add(killOperation);
				
		result = rfDevice.execute(antennas, filters, operations);
		
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).getResultList().size());
		assertEquals(KillResult.class, result.get(0).getResultList().get(0).getClass());
		assertEquals(operationId, ((KillResult)result.get(0).getResultList().get(0)).getOperationId());
		return (KillResult)result.get(0).getResultList().get(0);
	}

	private OperationResult[] readETBv2(int antenna, String hexStr, int bitCount) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {	
		return readETBv2(antenna, hexStr, bitCount, null, null);
	}
	
	private OperationResult[] readETBv2(int antenna, String hexStr, int bitCount, String operationId) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {	
		return readETBv2(antenna, hexStr, bitCount, operationId, null);
	}
	
	private OperationResult[] readETBv2(int antenna, String hexStr, int bitCount, String operationId, String password) 
			throws ParameterException, CommunicationException, ImplementationException, ConnectionException {
		CustomOperation customOperation = new CustomOperation();
		customOperation.setData(RFUtils.hexToBytes(hexStr));
		customOperation.setLength(s(bitCount));
		customOperation.setOperationId(operationId);
		
		ReadOperation readEpc = new ReadOperation();
		readEpc.setBank(RFUtils.BANK_EPC);
		readEpc.setOffset(s(2));
		readEpc.setLength(s(6));
		
		ReadOperation readTid = new ReadOperation();
		readTid.setBank(RFUtils.BANK_TID);
		readTid.setOffset(s(0));
		readTid.setLength(s(6));
		
		if (password != null) {
			int psw = RFUtils.bytesToInt(RFUtils.hexToBytes(password));
			customOperation.setPassword(psw);
			readEpc.setPassword(psw);
			readTid.setPassword(psw);
		}
		
		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		List<Filter> filters = new ArrayList<>();
		List<TagData> result;
		
		antennas.add(s(antenna));
		operations.add(customOperation);
		operations.add(readEpc);
		operations.add(readTid);
							
		result = rfDevice.execute(antennas, filters, operations);
		
		try {			
			return new OperationResult[] { 
				(CustomResult)result.get(0).getResultList().get(0),
				(ReadResult)result.get(0).getResultList().get(1),
				(ReadResult)result.get(0).getResultList().get(2)
			};			
			
		} catch (Exception e) {
			return null;
		}
	}
	
	private short s(int i) {
		return (short)i;
	}
	
	private Short S(int i) {
		return new Short(s(i));
	}
	
	private byte[] get1Mask(int byteCount) {
		boolean[] byteMask = new boolean[byteCount];
		for (int i = 0; i < byteMask.length; i++)
			byteMask[i] = true;
		return getByteMask(byteMask);
	}
	
	private byte[] get0Mask(int byteCount) {
		boolean[] byteMask = new boolean[byteCount];
		for (int i = 0; i < byteMask.length; i++)
			byteMask[i] = false;
		return getByteMask(byteMask);
	}
	
	private byte[] getByteMask(boolean[] maskBytes) {
		byte[] result = new byte[maskBytes.length];
		for (int i = 0; i < maskBytes.length; i++)
			result[i] = maskBytes[i] ? (byte)0xff : 0x00;		
		return result;
	}
	
	private short bytesToWords(int bytes) {
		if (bytes % 2 != 0) 
			throw new IllegalArgumentException("'bytes' must be an even number.");
		return (short)(bytes/2);
	}
	
	private void anyKey() {
		System.out.print("Press ENTER to continue.");		
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void printTestHeader(String text) {
		System.out.printf("\n%s\n", "************************************************************");
		System.out.printf("%s%"+(29+text.length()/2)+"s%"+(30-text.length()/2)+"s\n", "*", text.toUpperCase(), "*"); 
		System.out.printf("%s\n\n", "************************************************************");		
	}
}
