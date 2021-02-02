package havis.device.rf.nur;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import havis.device.rf.RFConsumer;
import havis.device.rf.capabilities.FreqHopTable;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.capabilities.TransmitPowerTable;
import havis.device.rf.capabilities.TransmitPowerTableEntry;
import havis.device.rf.common.Environment;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.common.util.RFUtils.OperationListInspectionResult;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaConfigurationList;
import havis.device.rf.configuration.AntennaPropertyList;
import havis.device.rf.configuration.ConnectType;
import havis.device.rf.configuration.RFRegion;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.nur.Constants.Antenna;
import havis.device.rf.nur.Constants.Region;
import havis.device.rf.nur.Constants.TxLevel;
import havis.device.rf.nur.NurErrorMap.RFCError;
import havis.device.rf.nur.NurTagProcessor.EpcBankData;
import havis.device.rf.nur.NurTagProcessor.Singulation;
import havis.device.rf.nur.NurTagProcessor.Singulation.SingulationStrategy;
import havis.device.rf.nur.firmware.ExecutionException;
import havis.device.rf.nur.firmware.FirmwareUpdater;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.TagDataList;
import havis.device.rf.tag.operation.CustomOperation;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.LockOperation.Field;
import havis.device.rf.tag.operation.LockOperation.Privilege;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.junit.Before;
import org.junit.Test;

import com.nordicid.nativeserial.NativeSerialTransport;
import com.nordicid.nativeserial.SerialPort;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurGPIOConfig;
import com.nordicid.nurapi.NurIRConfig;
import com.nordicid.nurapi.NurInventoryExtended;
import com.nordicid.nurapi.NurInventoryExtendedFilter;
import com.nordicid.nurapi.NurRespInventory;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;
import com.nordicid.nurapi.NurTuneResponse;

public class NurHardwareManagerTest {

	private static final Logger log = Logger.getLogger(NurHardwareManager.class.getName());
	
	@Mocked
	NurApi nurApi;
	@Mocked
	SerialPort serialPort;
	@Mocked
	NativeSerialTransport natSerTrans;

	@Before
	public void setup() {
		log.setLevel(Level.ALL);
	}

	@Test
	public void testOpenCloseConnection(@Mocked final NurConfigurationHelper setup,
			@Mocked final NurRespReaderInfo info) throws Exception {
		class TestControl {
			boolean connected;
			boolean failOpen;
			boolean failClose;
		}

		final TestControl testCtrl = new TestControl();
		final Lock lock = new ReentrantLock();
		final Condition apiDisconnectCalled = lock.newCondition();
		final NurHardwareManager hwMgr = new NurHardwareManager();

		new NonStrictExpectations() {
			{
				nurApi.isConnected();
				result = new Delegate<Boolean>() {
					@SuppressWarnings("unused")
					boolean isConnected() {
						return testCtrl.connected;
					}
				};

				natSerTrans.isConnected();
				result = new Delegate<Boolean>() {
					@SuppressWarnings("unused")
					boolean isConnected() {
						return testCtrl.connected;
					}
				};

				nurApi.connect();
				result = new Delegate<Object>() {
					@SuppressWarnings("unused")
					void disconnect() throws Exception {
						if (testCtrl.failOpen)
							throw new Exception("NurApi Error");
					}
				};

				nurApi.disconnect();
				result = new Delegate<Object>() {
					@SuppressWarnings("unused")
					void disconnect() throws Exception {
						if (testCtrl.failClose)
							throw new Exception();
						else {
							try {
								lock.lock();
								apiDisconnectCalled.signal();
							} finally {
								lock.unlock();
							}
						}
					}
				};

				nurApi.getReaderInfo();
				result = info;				
			}
		};

		Runnable hwSimulator = new Runnable() {
			@Override
			public void run() {
				try {
					lock.lock();
					apiDisconnectCalled.await();
					((NurApiListener)getField(hwMgr, "nurApiListener")).disconnectedEvent();					

				} catch (InterruptedException e) {
				} finally {
					lock.unlock();
				}
			}
		};

		// tests connect / disconnect with connected API and without error
		// (expected case)
		testCtrl.connected = true;
		testCtrl.failClose = false;
		testCtrl.failOpen = false;
		hwMgr.openConnection();
		new Thread(hwSimulator).start();
		hwMgr.closeConnection();

		// tests connect / disconnect with connected API and with error on
		// disconnect
		testCtrl.connected = false;
		testCtrl.failClose = false;
		testCtrl.failOpen = true;
		try {
			hwMgr.openConnection();
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("NurApi Error"));
		}

		// tests connect / disconnect with connected API and with error on
		// disconnect
		testCtrl.connected = true;
		testCtrl.failClose = true;
		testCtrl.failOpen = false;
		hwMgr.openConnection();
		new Thread(hwSimulator).start();
		try {
			hwMgr.closeConnection();
			fail();
		} catch (Exception e) {
		}

		// tests disconnect with disconnected API and without error
		testCtrl.connected = false;
		testCtrl.failClose = false;
		hwMgr.openConnection();
		new Thread(hwSimulator).start();
		hwMgr.closeConnection();
		new Verifications() {
			{
				nurApi.connect();
				times = 3;
				setup.loadModuleSetup();
				times = 2;

				nurApi.disconnect();
				times = 2;
				nurApi.dispose();
				times = 3;
			}
		};
	}

	@Test
	@SuppressWarnings({ "unused", "unchecked" })
	public void testExecute(
			@Mocked final RFUtils rfcUtils,
			@Mocked final Environment env,
			@Mocked final NurTagProcessor tagProcessor,
			@Mocked final NurTag nurTag,
			@Mocked final NurTagStorage nurTagStorage,
			@Mocked final RFConsumer consumer,
			@Mocked final NurInventoryExtended nurInventoryExtended,
			@Mocked final NurInventoryExtendedFilter nurInventoryExtendedFilter,
			@Mocked final NurRespInventory nurRespInventory,
			@Mocked final NurIRConfig irConfig,
			@Mocked final NurRespReaderInfo info,
			@Mocked final NurConfigurationHelper config,
			@Mocked final Filter f0, @Mocked final Filter f1

	) throws Exception {
		class TestControl {

			final List<Short> connectedAntennas = Arrays.asList(new Short[] {(short) 1, (short) 2, (short) 3, (short) 4 });
			List<Short> antennas = Arrays.asList(new Short[] { (short) 1, (short) 3 });
			List<Short> antennasOverride = null;
			Singulation.SingulationStrategy singStrategy = SingulationStrategy.EPC;
			int numTagsFound = 1;
			ReadResult.Result epcBankDataResult = Result.SUCCESS;
			boolean failDuringInventory = false;
			boolean regionUnspecified = false;
			boolean optTidReading = false;
			short applyAntennaSelectionReturnVal = 0;
		}

		final TestControl testCtrl = new TestControl();

		new NonStrictExpectations() {
			{
				setField(Environment.class, "HARDWARE_MANAGER_ANTENNAS",testCtrl.antennasOverride);
				
				tagProcessor.getOptimalSingulation(withInstanceOf(List.class));
				result = new Delegate<Singulation>() {				
					Singulation getOptimalSingulation(List<Filter> filters) {
						return testCtrl.singStrategy == SingulationStrategy.EPC ? Singulation
								.getEPCInstance(new byte[] {}) : Singulation
								.getTIDInstance(new byte[] {});
					}
				};
				
				nurApi.inventoryExtended(
						withInstanceOf(NurInventoryExtended.class),
						withInstanceOf(NurInventoryExtendedFilter[].class),
						anyInt);
				result = new Delegate<NurRespInventory>() {					
					NurRespInventory inventoryExtended(
							NurInventoryExtended nie,
							NurInventoryExtendedFilter[] filters, int filterLen)
							throws Exception {
						if (testCtrl.failDuringInventory)
							throw new Exception("Expected exception");
						return nurRespInventory;
					}
				};

				nurApi.getStorage();
				result = nurTagStorage;

				nurTagStorage.size();
				result = new Delegate<Integer>() {
					int size() {
						return testCtrl.numTagsFound;
					}
				};

				tagProcessor.readEpcBankData(withInstanceOf(Singulation.class), withInstanceOf(NurTag.class), anyBoolean);
				result = new Delegate<EpcBankData>() {
					EpcBankData readEpcBankData(Singulation sing, NurTag tag, boolean readCrc) {
						
						if (testCtrl.epcBankDataResult != Result.SUCCESS)
							return new EpcBankData(testCtrl.epcBankDataResult);						
						return new EpcBankData((short)0xaaaa, (short)0xbbbb, 
							new byte[] { (byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe }, (short)0, (short)0);
					}
				};

				nurTagStorage.get(anyInt);
				result = nurTag;

				nurTag.getAntennaId();
				result = new Delegate<Integer>() {
					int getAntennaId() {
						return testCtrl.antennasOverride != null
								&& testCtrl.antennasOverride.size() > 0 ? testCtrl.antennasOverride
								.get(0) - 1 : testCtrl.antennas.get(0) - 1;
					}
				};

				setField(Environment.class, "OPTIMIZED_TID_BANK_READING", testCtrl.optTidReading); 					
				
				nurTag.getPC();
				result = null;

				nurApi.getReaderInfo();
				result = info;

				config.getRegion();
				result = new Delegate<Region>() {
					Region getReqion() {
						return testCtrl.regionUnspecified ? Region.RegionUnspecified
								: Region.RegionEU;
					}
				};

				config.applyAntennaSelection(withInstanceOf(List.class));
				result = new Delegate<List<Short>>() {
					short applyAntennaSelection(List<Short> antennas) {
						return testCtrl.applyAntennaSelectionReturnVal;
					}
				};				
			}
		};

		NurHardwareManager hwMgr = new NurHardwareManager();
		hwMgr.openConnection();
		setField(hwMgr, "connectedAntennas", testCtrl.connectedAntennas);

		List<TagOperation> tagOps = new ArrayList<TagOperation>();
		List<Filter> filters = new ArrayList<Filter>();

		/* *** TEST: region unspecified *** */
		testCtrl.numTagsFound = 0;
		testCtrl.failDuringInventory = false;
		testCtrl.regionUnspecified = true;
		testCtrl.applyAntennaSelectionReturnVal = 2;

		final TagDataList res1 = hwMgr.execute(new ArrayList<Short>(), filters, tagOps, consumer);

		new Verifications() {{
			assertEquals(0, res1.getEntryList().size());			
		}};

		/* *** TEST: empty antenna list *** */
		testCtrl.regionUnspecified = false;
		testCtrl.applyAntennaSelectionReturnVal = 0;

		final TagDataList res2 = hwMgr.execute(new ArrayList<Short>(), filters, tagOps, consumer);
		new Verifications() {{
			assertEquals(0, res2.getEntryList().size());
		}};

		/* *** TEST: non-empty antenna list *** */
		testCtrl.applyAntennaSelectionReturnVal = 2;

		hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);
		new Verifications() {{
			List<Short> effectiveAntennas;				
			config.applyAntennaSelection(effectiveAntennas = withCapture());
			times = 1;
			
			assertTrue(testCtrl != null);
			assertTrue(effectiveAntennas.containsAll(testCtrl.antennas));
		}};

		/* *** TEST: antenna override *** */		
		testCtrl.antennasOverride = Arrays.asList((short) 2, (short) 4);
		testCtrl.failDuringInventory = true;
		testCtrl.applyAntennaSelectionReturnVal = 0;

		setField(Environment.class, "HARDWARE_MANAGER_ANTENNAS",testCtrl.antennasOverride);
		hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {
			{
				List<Short> effectiveAntennas = null;
				config.applyAntennaSelection(effectiveAntennas = withCapture());
				times = 2;

				assertTrue(effectiveAntennas != null);
				assertTrue(effectiveAntennas.containsAll(testCtrl.antennasOverride));
			}
		};
		
		/* *** TEST: antenna 0 *** */
		testCtrl.antennasOverride = null;
		testCtrl.failDuringInventory = false;
		testCtrl.antennas = Arrays.asList((short) 0);

		setField(Environment.class, "HARDWARE_MANAGER_ANTENNAS",testCtrl.antennasOverride);
		hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {
			{
				List<Short> effectiveAntennas = null;
				config.applyAntennaSelection(effectiveAntennas = withCapture());
				times = 3;

				assertTrue(effectiveAntennas != null);
				assertTrue(effectiveAntennas.containsAll(testCtrl.connectedAntennas));
			}
		};
		
		/* *** TEST: exception during inventory *** */
		testCtrl.antennas = Arrays.asList((short) 1, (short) 3);
		testCtrl.failDuringInventory = true;
		testCtrl.applyAntennaSelectionReturnVal = 2;

		new NonStrictExpectations() {
			{
				config.applyAntennaSelection(withInstanceOf(List.class));
				result = new Delegate<List<Short>>() {
					short applyAntennaSelection(List<Short> antennas) {
						return testCtrl.applyAntennaSelectionReturnVal;
					}
				};
			}
		};

		final TagDataList res3 = hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {
			{
				List<Short> effectiveAntennas = null;
				config.applyAntennaSelection(effectiveAntennas = withCapture());
				times = 1;

				assertTrue(effectiveAntennas != null);
				assertTrue(effectiveAntennas.containsAll(testCtrl.antennas));

				assertEquals(0, res3.getEntryList().size());
			}
		};

		/* *** TEST: empty storage after inventory *** */
		testCtrl.failDuringInventory = false;

		new NonStrictExpectations() {
			{
				config.applyAntennaSelection(withInstanceOf(List.class));
				result = new Delegate<List<Short>>() {
					short applyAntennaSelection(List<Short> antennas) {
						return testCtrl.applyAntennaSelectionReturnVal;
					}
				};
			}
		};

		final TagDataList res4 = hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {{
			assertEquals(0, res4.getEntryList().size());
		}};

		/* *** TEST: unsuccessful epcDataRead *** */
		testCtrl.numTagsFound = 1;
		testCtrl.epcBankDataResult = Result.NON_SPECIFIC_TAG_ERROR;
		testCtrl.applyAntennaSelectionReturnVal = 2;

		final TagDataList res5 = hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {{
			assertEquals(1, res5.getEntryList().size());			
			TagData td = res5.getEntryList().get(0);
			assertEquals(0, td.getEpc().length);			
		}};

		/* *** TEST: successful epcDataRead *** */
		testCtrl.epcBankDataResult = Result.SUCCESS;		

		final TagDataList res6 = hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {{
			assertEquals(1, res5.getEntryList().size());			
			TagData td = res6.getEntryList().get(0);
			assertEquals(4, td.getEpc().length);
		}};

		/* *** TEST: optimized TID reading *** */
		testCtrl.optTidReading = true;
		setField(Environment.class, "OPTIMIZED_TID_BANK_READING", testCtrl.optTidReading); 
		hwMgr.execute(testCtrl.antennas, filters, tagOps, consumer);

		new Verifications() {{
			RFUtils.inspectOperationList(withInstanceOf(List.class));
			times = 1;
		}};
	}

	@Test
	public void testGetEffectiveAntennaList(@Mocked final Environment env)
			throws ImplementationException {
		final List<Short> connectedAntennas = Arrays.asList(new Short[] { 1, 2,
				3, 4 });
		final List<Short> antennasOverride = Arrays.asList(new Short[] { 1, 2,
				5, 6 });

		new NonStrictExpectations() {
			{
				setField(Environment.class, "HARDWARE_MANAGER_ANTENNAS", antennasOverride);				
			}
		};

		NurHardwareManager hwMgr = new NurHardwareManager();
		setField(hwMgr, "connectedAntennas",
				Arrays.asList(new Short[] { 1, 2, 3, 4 }));

		List<Short> antennas = Arrays.asList(new Short[] { 1, 2, 3, 4 });
		assertEquals(hwMgr.getEffectiveAntennaList(antennas),
				Arrays.asList(new Short[] { 1, 2 }));

		new NonStrictExpectations() {
			{
				setField(Environment.class, "HARDWARE_MANAGER_ANTENNAS", null);
			}
		};

		antennas = Arrays.asList(new Short[] {});
		assertEquals(hwMgr.getEffectiveAntennaList(antennas).size(), 0);

		antennas = Arrays.asList(new Short[] { 0 });
		assertEquals(hwMgr.getEffectiveAntennaList(antennas), connectedAntennas);
	}

	@Test
	public void testPerformOperation(@Mocked final NurConfigurationHelper config,
			@Mocked final NurTag nurTag, @Mocked final NurRespReaderInfo info,
			@Mocked final NurTagProcessor tagProcessor,
			@Mocked final Singulation sing,
			@Mocked final RFConsumer consumer) throws Exception {
		class TestControl {
			ReadResult.Result readResult = ReadResult.Result.SUCCESS;
			WriteResult.Result writeResult = WriteResult.Result.SUCCESS;
			KillResult.Result killResult = KillResult.Result.SUCCESS;
			LockResult.Result lockResult = LockResult.Result.SUCCESS;
			CustomResult.Result custResult = CustomResult.Result.SUCCESS;
		}

		final TestControl testCtrl = new TestControl();
		
		final ReadOperation reqOpResp = new ReadOperation();
		reqOpResp.setBank((short) 3);
		reqOpResp.setLength((short) 16);
		reqOpResp.setOffset((short) 2);
		reqOpResp.setOperationId("Req-Op-1");
		
		new NonStrictExpectations() {
			{
				tagProcessor.read(sing, withInstanceOf(ReadOperation.class),
						null);
				result = new Delegate<ReadResult>() {
					@SuppressWarnings("unused")
					ReadResult read(Singulation sing, ReadOperation rdOp,
							byte[] irData) {
						ReadResult res = new ReadResult();
						res.setOperationId(rdOp.getOperationId());
						if (testCtrl.readResult == ReadResult.Result.SUCCESS) {
							byte[] data = new byte[rdOp.getLength() * 2];
							for (int i = 0; i < data.length; i++)
								data[i] = (i % 2 == 0) ? (byte) 0xca
										: (byte) 0xfe;
							res.setReadData(data);
						}
						res.setResult(testCtrl.readResult);
						return res;
					}
				};

				tagProcessor.write(sing, withInstanceOf(WriteOperation.class));
				result = new Delegate<WriteResult>() {
					@SuppressWarnings("unused")
					WriteResult write(Singulation sing, WriteOperation wrOp) {
						WriteResult res = new WriteResult();
						res.setOperationId(wrOp.getOperationId());
						if (testCtrl.writeResult == WriteResult.Result.SUCCESS)
							res.setWordsWritten((short) (wrOp.getData().length / 2));
						res.setResult(testCtrl.writeResult);
						return res;
					}
				};

				tagProcessor.lock(sing, withInstanceOf(LockOperation.class));
				result = new Delegate<LockResult>() {
					@SuppressWarnings("unused")
					LockResult write(Singulation sing, LockOperation lkOp) {
						LockResult res = new LockResult();
						res.setOperationId(lkOp.getOperationId());
						res.setResult(testCtrl.lockResult);
						return res;
					}
				};

				tagProcessor.kill(sing, withInstanceOf(KillOperation.class));
				result = new Delegate<KillResult>() {
					@SuppressWarnings("unused")
					KillResult kill(Singulation sing, KillOperation klOp) {
						KillResult res = new KillResult();
						res.setOperationId(klOp.getOperationId());
						res.setResult(testCtrl.killResult);
						return res;
					}
				};
				
				tagProcessor.custom(nurTag, sing, withInstanceOf(CustomOperation.class));
				result = new Delegate<CustomResult>() {
					@SuppressWarnings("unused")
					CustomResult custom(NurTag tag, Singulation sing, CustomOperation cOp) {
						CustomResult res = new CustomResult();
						res.setOperationId(cOp.getOperationId());
						res.setResult(testCtrl.custResult);
						return res;
					}
				};

				tagProcessor.readCompleteBank(sing,
						withInstanceOf(ReadOperation.class),
						withInstanceOf(TagData.class),
						withInstanceOf(NurTag.class), null);
				result = new Delegate<KillResult>() {
					@SuppressWarnings("unused")
					ReadResult readCompleteBank(Singulation sing,
							ReadOperation ro, TagData tagData, NurTag nurTag, byte[] irData) {
						ReadResult res = new ReadResult();
						res.setOperationId(ro.getOperationId());
						if (testCtrl.readResult == ReadResult.Result.SUCCESS) {
							byte[] data = new byte[ro.getLength() * 2];
							for (int i = 0; i < data.length; i++)
								data[i] = (i % 2 == 0) ? (byte) 0xca
										: (byte) 0xfe;
							res.setReadData(data);
						}
						res.setResult(testCtrl.readResult);
						return res;
					}
				};

				consumer.getOperations(withInstanceOf(TagData.class));
				result = new Delegate<List<TagOperation>>() {
					@SuppressWarnings("unused")
					List<TagOperation> getOperations(TagData tagData) {
						return Arrays.asList((TagOperation) reqOpResp);
					}
				};

				nurApi.getReaderInfo();
				result = info;
			}
		};

		NurHardwareManager hwMgr = new NurHardwareManager();
		hwMgr.openConnection();

		RFCError tagError = null;
		final TagData tagData1 = new TagData();
		final List<TagOperation> operations = new ArrayList<TagOperation>();

		ReadOperation rdOp = new ReadOperation();
		rdOp.setBank((short) 1);
		rdOp.setLength((short) 6);
		rdOp.setOffset((short) 2);
		rdOp.setOperationId("rdOp-1");
		operations.add(rdOp);

		WriteOperation wrOp = new WriteOperation();
		wrOp.setBank((short) 1);
		wrOp.setOffset((short) 2);
		wrOp.setData(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba,
				(byte) 0xbe });
		wrOp.setOperationId("wrOp-1");
		operations.add(wrOp);

		LockOperation lkOp = new LockOperation();
		lkOp.setField(Field.USER_MEMORY);
		lkOp.setOperationId("lkOp-1");
		lkOp.setPassword(42);
		lkOp.setPrivilege(Privilege.LOCK);
		operations.add(lkOp);

		KillOperation klOp = new KillOperation();
		klOp.setOperationId("klOp-1");
		klOp.setKillPassword(42);
		operations.add(klOp);
		
		CustomOperation cOp = new CustomOperation();
		cOp.setOperationId("cOp-1");
		cOp.setData(RFUtils.hexToBytes("E00248000000"));
		cOp.setLength((short)44);
		operations.add(cOp);

		// Expected: all ops succeed, thus each op (read, write, lock, kill, cust) are
		// all called for the 1st time
		// Result list contains only success results
		for (TagOperation op : operations)
			tagError = hwMgr.performOperation(op, tagProcessor, tagData1, sing,
					tagError, nurTag, consumer);

		tagError = null;

		final TagData tagData2 = new TagData();
		testCtrl.readResult = ReadResult.Result.NON_SPECIFIC_TAG_ERROR;

		// Expected: first op (read) fails, thus all subsequent ops are skipped.
		// Only read is called for the 2nd time
		// Result list contains only error results
		for (TagOperation op : operations)
			tagError = hwMgr.performOperation(op, tagProcessor, tagData2, sing,
					tagError, nurTag, consumer);

		// Expected:
		// read op is not called because of preceding tag error
		TagData tagData3 = new TagData();
		hwMgr.performOperation(rdOp, tagProcessor, tagData3, sing,
				RFCError.NonSpecificTagError, nurTag, consumer);

		/* FAILURE tests */
		
		// Expected:
		// read op is called and fails, NonSpecificTagError is returned (read
		// called for 3rd time)
		testCtrl.readResult = ReadResult.Result.MEMORY_OVERRUN_ERROR;
		final RFCError rdError = hwMgr.performOperation(rdOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// write op is called and fails, NonSpecificTagError is returned (write
		// called for 2nd time)
		testCtrl.writeResult = WriteResult.Result.MEMORY_LOCKED_ERROR;
		final RFCError wrError = hwMgr.performOperation(wrOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// lock op is called and fails, NonSpecificTagError is returned (lock
		// called for 2nd time)
		testCtrl.lockResult = LockResult.Result.INSUFFICIENT_POWER;
		final RFCError lkError = hwMgr.performOperation(lkOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// kill op is called and fails, NonSpecificTagError is returned (kill
		// called for 2nd time)
		testCtrl.killResult = KillResult.Result.ZERO_KILL_PASSWORD_ERROR;
		final RFCError klError = hwMgr.performOperation(klOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// custom op is called and fails, NonSpecificTagError is returned (custom
		// called for 2nd time)
		testCtrl.custResult = CustomResult.Result.INSUFFICIENT_POWER;
		final RFCError custError = hwMgr.performOperation(cOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);
				
		/* SUCCESS tests */
		
		// Expected:
		// read op is called and succeeds, null is returned (read called for 4th
		// time)
		testCtrl.readResult = ReadResult.Result.SUCCESS;
		final RFCError rdSucc = hwMgr.performOperation(rdOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// write op is called and succeeds, null is returned (write called for
		// 3rd time)
		testCtrl.writeResult = WriteResult.Result.SUCCESS;
		final RFCError wrSucc = hwMgr.performOperation(wrOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// lock op is called and succeeds, null is returned (lock called for 3rd
		// time)
		testCtrl.lockResult = LockResult.Result.SUCCESS;
		final RFCError lkSucc = hwMgr.performOperation(lkOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// kill op is called and succeeds, null is returned (kill called for 3rd
		// time)
		testCtrl.killResult = KillResult.Result.SUCCESS;
		final RFCError klSucc = hwMgr.performOperation(klOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);
		
		// Expected:
		// cust op is called and succeeds, null is returned (custom called for 3rd
		// time)
		testCtrl.custResult = CustomResult.Result.SUCCESS;
		final RFCError custSucc = hwMgr.performOperation(cOp, tagProcessor,
				tagData3, sing, null, nurTag, consumer);

		// Expected:
		// because of length 0, readComplete bank is called on tag processor and
		// method return null as result (no error)
		final TagData tagData4 = new TagData();
		final ReadOperation rdOpCmpl = new ReadOperation();
		rdOpCmpl.setBank((short) 1);
		rdOpCmpl.setLength((short) 0);
		rdOpCmpl.setOffset((short) 2);
		rdOpCmpl.setOperationId("rdOp-2");

		testCtrl.readResult = ReadResult.Result.SUCCESS;
		final RFCError rdCompleteSucc = hwMgr.performOperation(rdOpCmpl,
				tagProcessor, tagData4, sing, null, nurTag, consumer);

		RequestOperation reqOp = new RequestOperation();
		reqOp.setOperationId("Req-Op");
		final RFCError reqOpSucc = hwMgr.performOperation(reqOp, tagProcessor,
				tagData4, sing, null, nurTag, consumer);

		new Verifications() {
			{

				tagProcessor.read(withEqual(sing),
						withEqual((ReadOperation) operations.get(0)), null);
				times = 4;
				tagProcessor.write(withEqual(sing),
						withEqual((WriteOperation) operations.get(1)));
				times = 3;
				tagProcessor.lock(withEqual(sing),
						withEqual((LockOperation) operations.get(2)));
				times = 3;
				tagProcessor.kill(withEqual(sing),
						withEqual((KillOperation) operations.get(3)));
				times = 3;
				
				tagProcessor.custom(withEqual(nurTag), withEqual(sing),
						withEqual((CustomOperation) operations.get(4)));
				times = 3;
				
				tagProcessor.readCompleteBank(withEqual(sing),
						withEqual(rdOpCmpl), withEqual(tagData4),
						withEqual(nurTag), null);
				times = 1;

				assertEquals(tagData1.getResultList().size(), operations.size());
				assertEquals(tagData1.getResultList().size(), 5);
				
				assertTrue(tagData1.getResultList().get(0) instanceof ReadResult);
				ReadResult rdRes = (ReadResult) tagData1.getResultList().get(0);
				assertTrue(rdRes.getResult() == ReadResult.Result.SUCCESS);

				assertTrue(tagData1.getResultList().get(1) instanceof WriteResult);
				WriteResult wrRes = (WriteResult) tagData1.getResultList().get(1);
				assertTrue(wrRes.getResult() == WriteResult.Result.SUCCESS);

				assertTrue(tagData1.getResultList().get(2) instanceof LockResult);
				LockResult lkRes = (LockResult) tagData1.getResultList().get(2);
				assertTrue(lkRes.getResult() == LockResult.Result.SUCCESS);

				assertTrue(tagData1.getResultList().get(3) instanceof KillResult);
				KillResult klRes = (KillResult) tagData1.getResultList().get(3);
				assertTrue(klRes.getResult() == KillResult.Result.SUCCESS);

				assertTrue(tagData1.getResultList().get(4) instanceof CustomResult);
				CustomResult custRes = (CustomResult) tagData1.getResultList().get(4);
				assertTrue(custRes.getResult() == CustomResult.Result.SUCCESS);
				
				assertEquals(tagData2.getResultList().size(), operations.size());
				assertEquals(tagData2.getResultList().size(), 5);
				
				assertTrue(tagData2.getResultList().get(0) instanceof ReadResult);
				rdRes = (ReadResult) tagData2.getResultList().get(0);
				assertTrue(rdRes.getResult() != ReadResult.Result.SUCCESS);

				assertTrue(tagData2.getResultList().get(1) instanceof WriteResult);
				wrRes = (WriteResult) tagData2.getResultList().get(1);
				assertTrue(wrRes.getResult() != WriteResult.Result.SUCCESS);

				assertTrue(tagData2.getResultList().get(2) instanceof LockResult);
				lkRes = (LockResult) tagData2.getResultList().get(2);
				assertTrue(lkRes.getResult() != LockResult.Result.SUCCESS);

				assertTrue(tagData2.getResultList().get(3) instanceof KillResult);
				klRes = (KillResult) tagData2.getResultList().get(3);
				assertTrue(klRes.getResult() != KillResult.Result.SUCCESS);
				
				assertTrue(tagData2.getResultList().get(4) instanceof CustomResult);
				custRes = (CustomResult) tagData2.getResultList().get(4);
				assertTrue(custRes.getResult() != CustomResult.Result.SUCCESS);

				assertEquals(rdError, RFCError.NonSpecificTagError);
				assertEquals(wrError, RFCError.NonSpecificTagError);
				assertEquals(lkError, RFCError.NonSpecificTagError);
				assertEquals(klError, RFCError.NonSpecificTagError);
				assertEquals(custError, RFCError.NonSpecificTagError);

				assertNull(rdSucc);
				assertNull(wrSucc);
				assertNull(lkSucc);
				assertNull(klSucc);
				assertNull(custSucc);

				assertNull(rdCompleteSucc);

				tagProcessor.read(withInstanceOf(Singulation.class),
						withInstanceLike(reqOpResp),
						withInstanceOf(byte[].class));
				times = 5;

				assertNull(reqOpSucc);
			}
		};
	}
	
	@Test	
	public void testInventory(
			@Mocked final NurConfigurationHelper config,
			@Mocked final NurTagProcessor nurTagProcessor,
			@Mocked final NurInventoryExtended nurInventoryExtended,
			@Mocked final NurInventoryExtendedFilter nurInventoryExtendedFilter,
			@Mocked final NurRespInventory nurRespInventory,
			@Mocked final NurTagStorage nurTagStorage,
			@Mocked final Singulation singulation,
			@Mocked final NurIRConfig irConfig,
			@Mocked final NurRespReaderInfo info) throws Exception {

		class TestControl {
			boolean singByTid;
		}

		final TestControl testCtrl = new TestControl();

		// Data (0x): C A F E B A B E
		// Data (0b): 1100 1010 1111 1110 1011 1010 1011 1110
		final byte[] data = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba,
				(byte) 0xbe };

		// Mask (0x): F F F F F F F F
		// Mask (0b): 1111 1111 1111 1111 1111 1111 1111 1111
		final byte[] mask = new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff,
				(byte) 0xff };

		Filter flt = new Filter();
		flt.setBank(RFUtils.BANK_EPC);
		flt.setBitLength((short) (data.length * 8));
		flt.setBitOffset((short) 0);
		flt.setMask(mask);
		flt.setData(data);
		flt.setMatch(true);

		final List<Filter> filters = new ArrayList<Filter>();
		filters.add(flt);

		new NonStrictExpectations() {
			{
				singulation.getStrategy();
				result = new Delegate<Singulation.SingulationStrategy>() {
					@SuppressWarnings("unused")
					Singulation.SingulationStrategy getStrategy() {
						return testCtrl.singByTid ? Singulation.SingulationStrategy.TID
								: Singulation.SingulationStrategy.EPC;
					}
				};

				nurApi.inventoryExtended(
						withInstanceOf(NurInventoryExtended.class),
						withInstanceOf(NurInventoryExtendedFilter[].class),
						anyInt);
				result = nurRespInventory;

				nurApi.getReaderInfo();
				result = info;
			}
		};

		NurHardwareManager hwMgr = new NurHardwareManager();

		try {
			hwMgr.openConnection();
		} catch (Exception ex) {
			fail(ex.getMessage());
		}

		// Test inventory with tags found and singulation by EPC
		nurRespInventory.numTagsFound = 5;
		testCtrl.singByTid = false;
		hwMgr.inventory(filters, singulation, null);

		new Verifications() {
			{
				nurApi.clearIdBuffer(true);
				times = 1;

				nurApi.fetchTags(anyBoolean);
				times = 1;

				nurApi.getStorage();
				times = 1;
			}
		};

		// Test inventory with no tags found and singulation by TID
		nurRespInventory.numTagsFound = 0;
		testCtrl.singByTid = true;
		hwMgr.inventory(filters, singulation, null);

		new Verifications() {
			{
				nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);
				times = 1;

				nurApi.clearIdBuffer(true);
				times = 2;

				nurApi.getStorage();
				times = 2;
			}
		};

		// Test inventory with tags found and singulation by EPC but oplist contains TID read op (with len+offs == ID_DATA_WORDS)
		nurRespInventory.numTagsFound = 3;
		testCtrl.singByTid = false;
		hwMgr.inventory(filters, singulation, 
			new OperationListInspectionResult(OperationListInspectionResult.LIST_INSPECTION_TID_READ_OPERATION,
				new ReadOperation() {{
					setLength((short) 4);
					setOffset((short) 2);
				}
			}));

		new Verifications() {
			{
				nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);
				times = 2;

				nurApi.clearIdBuffer(true);
				times = 3;

				nurApi.fetchTags(anyBoolean);
				times = 2;

				nurApi.getStorage();
				times = 3;								
			}
		};

		// Test inventory with tags found and singulation by EPC but oplist contains TID read op (with 0 < len+offs < ID_DATA_WORDS)
		nurRespInventory.numTagsFound = 3;
		testCtrl.singByTid = false;
		hwMgr.inventory(filters, singulation, 
			new OperationListInspectionResult(OperationListInspectionResult.LIST_INSPECTION_TID_READ_OPERATION,
				new ReadOperation() {{
					setLength((short) 2);
					setOffset((short) 2);
				}
			}));

		new Verifications() {
			{
				nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);
				times = 3;

				nurApi.clearIdBuffer(true);
				times = 4;

				nurApi.fetchTags(anyBoolean);
				times = 3;

				nurApi.getStorage();
				times = 4;								
			}
		};

		
		// Test inventory with tags found and singulaion by EPC but oplist containts TID read op (with len+offs == 0)
		nurRespInventory.numTagsFound = 3;
		testCtrl.singByTid = false;
		hwMgr.inventory(
				filters,
				singulation,
				new OperationListInspectionResult(
						OperationListInspectionResult.LIST_INSPECTION_TID_READ_OPERATION,
						new ReadOperation() {
							{
								setLength((short) 0);
							}
						}));

		new Verifications() {
			{
				nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);
				times = 4;

				nurApi.clearIdBuffer(true);
				times = 5;

				nurApi.fetchTags(anyBoolean);
				times = 4;

				nurApi.getStorage();
				times = 5;
			}
		};
	}
	
	@Test
	public void testBuildFilterArray(@Mocked NurInventoryExtendedFilter nieFilter, @Mocked NurInventoryExtended nie)
			throws Exception {
		// Data (0x): C A F E B A B E
		// Data (0b): 1100 1010 1111 1110 1011 1010 1011 1110
		final byte[] filterData = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe };

		// Mask (0x): F F F F F F F F
		// Mask (0b): 1111 1111 1111 1111 1111 1111 1111 1111
		final byte[] maskNoSplit = new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };

		// Mask (0x): F F 0 0 F F 0 0
		// Mask (0b): 1111 1111 0000 0000 1111 11111 0000 0000
		final byte[] maskSplit2 = new byte[] { (byte) 0xff, (byte) 0x00, (byte) 0xff, (byte) 0x00 };

		// Mask (0x): F 0 F 0 F 0 F 0
		// Mask (0b): 1111 0000 1111 0000 1111 0000 1111 0000
		final byte[] maskSplit4 = new byte[] { (byte) 0xf0, (byte) 0xf0, (byte) 0xf0, (byte) 0xf0 };

		// Mask (0x): 9 A C 4 5 F 0 3
		// Mask (0b): 1001 1010 1100 0100 0101 1111 0000 0011
		final byte[] maskSplit8 = new byte[] { (byte) 0x9a, (byte) 0xc4, (byte) 0x5f, (byte) 0x03 };

		NurHardwareManager hwMgr = new NurHardwareManager();

		/* test with single match filter with a mask having all bits set */
		Filter flt = new Filter();
		flt.setBank(RFUtils.BANK_EPC);
		flt.setBitLength((short) (filterData.length * 8));
		flt.setBitOffset((short) 0);
		flt.setMask(maskNoSplit);
		flt.setData(filterData);
		flt.setMatch(true);

		final NurInventoryExtendedFilter[] filters0 = hwMgr
				.buildFilterArray(Arrays.asList(flt), nie);

		new Verifications() {
			{
				new NurInventoryExtendedFilter();
				times = 3;
				assertEquals(filters0.length, 3);
				assertEquals(filters0[0].action, NurApi.FILTER_ACTION_0);
				assertEquals(filters0[1].action, NurApi.FILTER_ACTION_1);
				assertEquals(filters0[2].action, NurApi.FILTER_ACTION_6);
			}
		};

		/*
		 * test with single no-match filter with a mask having half of its bits
		 * set
		 */
		flt = new Filter();
		flt.setBank(RFUtils.BANK_EPC);
		flt.setBitLength((short) (filterData.length * 8));
		flt.setBitOffset((short) 0);
		flt.setMask(maskSplit2);
		flt.setData(filterData);
		flt.setMatch(false);

		final NurInventoryExtendedFilter[] filters1 = hwMgr
				.buildFilterArray(Arrays.asList(flt), nie);

		new Verifications() {
			{
				new NurInventoryExtendedFilter();
				times = 7;
				assertEquals(filters1.length, 4);
				assertEquals(filters1[0].action, NurApi.FILTER_ACTION_4);
				assertEquals(filters1[1].action, NurApi.FILTER_ACTION_5);
				assertEquals(filters1[2].action, NurApi.FILTER_ACTION_1);
				assertEquals(filters1[3].action, NurApi.FILTER_ACTION_6);
			}
		};

		/*
		 * test with single match filter with a mask having a quarter of its
		 * bits set
		 */
		flt = new Filter();
		flt.setBank(RFUtils.BANK_EPC);
		flt.setBitLength((short) (filterData.length * 8));
		flt.setBitOffset((short) 0);
		flt.setMask(maskSplit4);
		flt.setData(filterData);
		flt.setMatch(true);

		final NurInventoryExtendedFilter[] filters2 = hwMgr
				.buildFilterArray(Arrays.asList(flt), nie);

		new Verifications() {
			{
				new NurInventoryExtendedFilter();
				times = 13;
				assertEquals(filters2.length, 6);
				assertEquals(filters2[0].action, NurApi.FILTER_ACTION_0);
				assertEquals(filters2[1].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters2[2].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters2[3].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters2[4].action, NurApi.FILTER_ACTION_1);
				assertEquals(filters2[5].action, NurApi.FILTER_ACTION_6);
			}
		};

		/*
		 * test with two match filters with a mask having a multiple bits set
		 * and specific offsets and bit lengths
		 */
		flt = new Filter();
		flt.setBank(RFUtils.BANK_EPC);

		// 1001 1010 1100 0100 0101 1111 0000 0011
		// ^^^^ ^^^^ ^^^^ ^^ => sub-filters: 5

		flt.setBitLength((short) 14);
		flt.setBitOffset((short) 4);
		flt.setMask(maskSplit8);
		flt.setData(filterData);
		flt.setMatch(true);

		final NurInventoryExtendedFilter[] filters3 = hwMgr
				.buildFilterArray(Arrays.asList(flt), nie);

		new Verifications() {
			{
				new NurInventoryExtendedFilter();
				times = 20;
				assertEquals(filters3.length, 7);
				assertEquals(filters3[0].action, NurApi.FILTER_ACTION_0);
				assertEquals(filters3[1].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters3[2].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters3[3].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters3[4].action, NurApi.FILTER_ACTION_2);
				assertEquals(filters3[5].action, NurApi.FILTER_ACTION_1);
				assertEquals(filters3[6].action, NurApi.FILTER_ACTION_6);
			}
		};
	}
	
	@Test
	public void testSetRegion(@Mocked final RFRegion rfcRegion,
			@Mocked final RegulatoryCapabilities regCaps,
			@Mocked final AntennaConfigurationList antCfgList,
			@Mocked final AntennaConfiguration antCfg,
			@Mocked final TransmitPowerTable tpTbl,
			@Mocked final TransmitPowerTableEntry tpTblEntry,
			@Mocked final FreqHopTable freqHopTable,
			@Mocked final Environment env,
			@Mocked final NurRespReaderInfo info,
			@Mocked final NurConfigurationHelper setup) throws Exception {

		NurHardwareManager nurHwMgr = new NurHardwareManager();
		new NonStrictExpectations() {
			{				
				nurApi.getReaderInfo();
				result = info;
				rfcRegion.getId();
				result = "eu";
				rfcRegion.getRegulatoryCapabilities();
				result = regCaps;
				regCaps.getTransmitPowerTable();
				result = tpTbl;
				regCaps.getFreqHopTable();
				result = null;
				regCaps.isHopping();
				result = false;

				tpTbl.getEntryList();
				result = Arrays.asList(tpTblEntry);
				tpTblEntry.getTransmitPower();
				result = TxLevel.TxLevel17.dBm;

				antCfgList.getEntryList();
				result = Arrays.asList(antCfg);
				antCfg.getId();
				result = (short) 1;
				
				nurApi.tuneEUBand(anyInt, true);
				result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };
				
				nurApi.tuneFCCBands(anyInt, true);
				result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };


			}

		};
		nurHwMgr.openConnection();
		nurHwMgr.setRegion(rfcRegion, antCfgList);

		new Verifications() {
			{
				setup.setTxLevel(withInstanceOf(Antenna.class),
						withInstanceOf(TxLevel.class));
				times = 1;

				setup.setRegion(withInstanceOf(Region.class));
				times = 1;
				
			}
		};

		new NonStrictExpectations() {
			{
				nurApi.getReaderInfo();
				result = info;
				rfcRegion.getId();
				result = "eu";
				rfcRegion.getRegulatoryCapabilities();
				result = regCaps;
				regCaps.getTransmitPowerTable();
				result = tpTbl;
				regCaps.getFreqHopTable();
				result = freqHopTable;
				regCaps.isHopping();
				result = true;

				tpTbl.getEntryList();
				result = Arrays.asList(tpTblEntry);
				tpTblEntry.getTransmitPower();
				result = TxLevel.TxLevel17.dBm;

				antCfgList.getEntryList();
				result = new ArrayList<AntennaConfiguration>();
				antCfg.getId();
				result = (short) 1;
				
				nurApi.tuneEUBand(anyInt, true);
				result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };
				
				nurApi.tuneFCCBands(anyInt, true);
				result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };


			}

		};
		nurHwMgr.openConnection();
		nurHwMgr.setRegion(rfcRegion, antCfgList);

		new Verifications() {
			{
				setup.setTxLevel(withInstanceOf(Antenna.class),
						withInstanceOf(TxLevel.class));
				times = 0;

				setup.setRegion(withInstanceOf(Region.class));
				times = 1;
				
			}
		};
	}

	@Test
	public void testGetRegion(@Mocked final NurConfigurationHelper config,
			@Mocked final Environment env, @Mocked final NurRespReaderInfo info)
			throws Exception {
		new NonStrictExpectations() {
			{
				config.getRegion();
				result = Region.RegionEU;
				
				nurApi.getReaderInfo();
				result = info;
			}
		};

		NurHardwareManager hwMgr = new NurHardwareManager();
		hwMgr.openConnection();
		assertEquals(hwMgr.getRegion(), Region.RegionEU.regionCode);
	}

	@Test
	public void testSetAntennaConfiguration(
			@Mocked final RegulatoryCapabilities regulatoryCapabilities,
			@Mocked final TransmitPowerTable transmitPowerTable,
			@Mocked final TransmitPowerTableEntry transmitPowerTableEntry,					
			@Mocked final NurConfigurationHelper setup) throws Exception {

		NurHardwareManager nurHwMgr = new NurHardwareManager();
		final List<Short> connectedAntennas = new ArrayList<>();
		setField(nurHwMgr, "connectedAntennas", connectedAntennas);
		setField(nurHwMgr, "setup", setup);
		setField(nurHwMgr, "nurApi", nurApi);
		
		class TestParams {
			Region setupRegion;
		}
		
		final TestParams testParams = new TestParams();
		testParams.setupRegion = Region.RegionEU;
		
		new NonStrictExpectations() {{	
		
			regulatoryCapabilities.getTransmitPowerTable();
			result = transmitPowerTable;

			transmitPowerTable.getEntryList();
			result = Arrays.asList(transmitPowerTableEntry);
			
			transmitPowerTableEntry.getTransmitPower();
			result = TxLevel.TxLevel17.dBm;

			setup.autoDetect(anyShort);
			result = new Delegate<short[]>() {
				@SuppressWarnings("unused")
				boolean autoDetect(short antenna) {
					return antenna == 1;						
				}
			};
			
			nurApi.tuneEUBand(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };
			
			nurApi.tuneFCCBands(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };
			
			setup.getRegion();
			result = new Delegate<Region>() {			
				@SuppressWarnings("unused")
				Region getRegion() {
					return testParams.setupRegion; 
				}
			};
		}};

		final AntennaConfiguration antennaConfiguration1 = new AntennaConfiguration();
		antennaConfiguration1.setId((short)1);
		antennaConfiguration1.setTransmitPower((short)0);
		
		final AntennaConfiguration antennaConfiguration2 = new AntennaConfiguration();
		antennaConfiguration2.setId((short)2);
		antennaConfiguration2.setTransmitPower((short)0);
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 1 
		 *  - connect type is true
		 * 	- force tune param is false 
		 * Expected:
		 * 	- antenna 1 is added to connectedAntennas list
		 * 	- antenna 1 is tuned for EU band
		 */
		
		antennaConfiguration1.setConnect(ConnectType.TRUE);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration1, regulatoryCapabilities, false);
		
		new Verifications() {{
			assertTrue(connectedAntennas.contains(antennaConfiguration1.getId()));
			nurApi.tuneEUBand(Antenna.fromId(antennaConfiguration1.getId()).nurApiAntId, true);
			times = 1;
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 1 
		 *  - connect type is false
		 * 	- force tune param is false 
		 * Expected:
		 * 	- antenna 1 is removed from connectedAntennas list
		 */
		
		antennaConfiguration1.setConnect(ConnectType.FALSE);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration1, regulatoryCapabilities, false);
		
		new Verifications() {{
			assertTrue(!connectedAntennas.contains(antennaConfiguration1.getId()));			
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 1 
		 *  - connect type is auto
		 * 	- force tune param is false 
		 * Expected:
		 * 	- antenna 1 is added from connectedAntennas list
		 *  - antenna 1 is tuned for FCC band
		 */
		
		testParams.setupRegion = Region.RegionFCC;		
		antennaConfiguration1.setConnect(ConnectType.AUTO);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration1, regulatoryCapabilities, false);
		
		new Verifications() {{
			assertTrue(connectedAntennas.contains(antennaConfiguration1.getId()));
			nurApi.tuneFCCBands(Antenna.fromId(antennaConfiguration1.getId()).nurApiAntId, true);
			times = 1;
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 2 
		 *  - connect type is true
		 * 	- force tune param is false
		 * Expected:
		 * 	- antenna 2 is added to connectedAntennas list
		 * 	- antenna 2 is tuned for EU band 
		 */
		
		new NonStrictExpectations() {{
			
			nurApi.tuneEUBand(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };

		}};
		
		testParams.setupRegion = Region.RegionEU;		
		antennaConfiguration2.setConnect(ConnectType.TRUE);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration2, regulatoryCapabilities, true);
		
		new Verifications() {{
			assertTrue(connectedAntennas.contains(antennaConfiguration2.getId()));
			nurApi.tuneEUBand(Antenna.fromId(antennaConfiguration2.getId()).nurApiAntId, true);
			times = 1;
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 2 
		 *  - connect type is true
		 * 	- force tune param is true
		 * Expected:
		 * 	- antenna 2 remains in connectedAntennas list
		 * 	- antenna 2 is tuned to EU band again 
		 */
		
		new NonStrictExpectations() {{
			
			nurApi.tuneEUBand(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };

		}};
		
		antennaConfiguration2.setConnect(ConnectType.TRUE);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration2, regulatoryCapabilities, true);
		
		new Verifications() {{
			assertTrue(connectedAntennas.contains(antennaConfiguration2.getId()));
			nurApi.tuneEUBand(Antenna.fromId(antennaConfiguration2.getId()).nurApiAntId, true);
			times = 1;
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 2 
		 *  - connect type is auto
		 * 	- force tune param is false
		 * Expected:
		 * 	- antenna 2 is removed from connectedAntennas list 
		 */
		
		antennaConfiguration2.setConnect(ConnectType.AUTO);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration2, regulatoryCapabilities, false);
		
		new Verifications() {{
			assertTrue(!connectedAntennas.contains(antennaConfiguration2.getId()));			
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 2 
		 *  - connect type is true
		 * 	- force tune param is true
		 * 	- region is Unspecified
		 * Expected:
		 * 	- antenna 2 is added to connectedAntennas list
		 * 	- no tuning is done due to unspecified region 
		 */
		
		new NonStrictExpectations() {{
			
			nurApi.tuneEUBand(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };
			
			nurApi.tuneFCCBands(anyInt, true);
			result = new NurTuneResponse[] { new NurTuneResponse(0, 0, 0, 0, 0) };

		}};
		
		testParams.setupRegion = Region.RegionUnspecified;
		antennaConfiguration2.setConnect(ConnectType.TRUE);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration2, regulatoryCapabilities, true);
		
		new Verifications() {{
			assertTrue(connectedAntennas.contains(antennaConfiguration2.getId()));
			nurApi.tuneEUBand(antennaConfiguration2.getId(), true);
			times = 0;
			
			nurApi.tuneFCCBands(antennaConfiguration2.getId(), true);
			times = 0;
		}};
		
		/* 
		 * Test:
		 * 	- set antenna configuration for antenna 2 
		 *  - connect type is null
		 * 	- force tune param is true
		 * Expected:
		 * 	- antenna 2 is removed from connectedAntennas list (since connect type null is treated as auto) 
		 */
				
		antennaConfiguration2.setConnect(null);
		nurHwMgr.setAntennaConfiguration(antennaConfiguration2, regulatoryCapabilities, true);
		
		new Verifications() {{
			assertTrue(!connectedAntennas.contains(antennaConfiguration2.getId()));
		}};
	}
	
	@Test
	@SuppressWarnings("unused")
	public void testGetAntennaProperties(@Mocked final NurConfigurationHelper setup) throws Exception {

		final NurHardwareManager nurHwMgr = new NurHardwareManager();
		setField(nurHwMgr, "setup", setup);
		final Map<Short, ConnectType> connectTypeMap = new HashMap<>();
		
		/*
		 * Test: 
		 * 	- two antennas, both having a connect type of FALSE
		 * Expected:
		 * 	- antenna property list with two antennas both being not connected  
		 */		
		connectTypeMap.put((short)1, ConnectType.FALSE);
		connectTypeMap.put((short)2, ConnectType.FALSE);
		AntennaPropertyList antennaProperties = nurHwMgr.getAntennaProperties(connectTypeMap);
		
		assertEquals(connectTypeMap.size(), antennaProperties.getEntryList().size());
		assertEquals(false, antennaProperties.getEntryList().get(0).isConnected());
		assertEquals(false, antennaProperties.getEntryList().get(1).isConnected());
		
		/*
		 * Test: 
		 * 	- two antennas, both having a connect type of TRUE
		 * Expected:
		 * 	- antenna property list with two antennas both being connected  
		 */		
		connectTypeMap.put((short)1, ConnectType.TRUE);
		connectTypeMap.put((short)2, ConnectType.TRUE);
		antennaProperties = nurHwMgr.getAntennaProperties(connectTypeMap);
		
		assertEquals(connectTypeMap.size(), antennaProperties.getEntryList().size());
		assertEquals(true, antennaProperties.getEntryList().get(0).isConnected());
		assertEquals(true, antennaProperties.getEntryList().get(1).isConnected());
		
		/*
		 * Test: 
		 * 	- two antennas, both having a connect type of AUTO
		 * Expected:
		 * 	- antenna property list with two antennas, one being connected 
		 *    and the other being not connected  
		 */
		
		new NonStrictExpectations() {{
			setup.autoDetect(anyShort);
			result = new Delegate<Boolean>() {
				boolean autoDetect(short antenna) {
					return antenna == 1;
				}
			};
		}};
		
		connectTypeMap.put((short)1, ConnectType.AUTO);
		connectTypeMap.put((short)2, ConnectType.AUTO);
		antennaProperties = nurHwMgr.getAntennaProperties(connectTypeMap);
		
		assertEquals(connectTypeMap.size(), antennaProperties.getEntryList().size());
		assertEquals(true, antennaProperties.getEntryList().get(0).isConnected());
		assertEquals(false, antennaProperties.getEntryList().get(1).isConnected());
		
		/*
		 * Test: 
		 * 	- two antennas, both having a connect type of null
		 * Expected:
		 * 	- same behavior as connect type AUTO for both antennas    
		 */		
		connectTypeMap.put((short)1, null);
		connectTypeMap.put((short)2, null);
		antennaProperties = nurHwMgr.getAntennaProperties(connectTypeMap);
		
		assertEquals(connectTypeMap.size(), antennaProperties.getEntryList().size());
		assertEquals(true, antennaProperties.getEntryList().get(0).isConnected());
		assertEquals(false, antennaProperties.getEntryList().get(1).isConnected());
	}

	@Test
	public void testGetFirmwareVersion(@Mocked final NurRespReaderInfo readerInfo) throws Exception {
		NurHardwareManager nurHwMgr = new NurHardwareManager();
		setField(nurHwMgr, "nurApi", nurApi);
		
		new NonStrictExpectations() {{			
			nurApi.getReaderInfo();
			result = readerInfo;			
		}};
		
		/*
		 * Test:
		 * 	- call with swVersion being 5.16-A
		 * Expected:
		 * 	- version 2.6 returned
		 */		
		setField(readerInfo, "swVersion", "5.16-A");
		assertEquals("2.6",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call with swVersion being 5.10-A
		 * Expected:
		 * 	- version 1.7 returned
		 */		
		setField(readerInfo, "swVersion", "5.10-A");
		assertEquals("1.7",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call with swVersion being 5.5-A
		 * Expected:
		 * 	- version 1.5 returned
		 */		
		setField(readerInfo, "swVersion", "5.5-A");
		assertEquals("1.5",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call with swVersion being 5.4-A
		 * Expected:
		 * 	- version 1.1 returned
		 */		
		setField(readerInfo, "swVersion", "5.4-A");
		assertEquals("1.1",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call with swVersion being 4.8.-
		 * Expected:
		 * 	- version 1.0 returned
		 */
		setField(readerInfo, "swVersion", "4.8-A");
		assertEquals("1.0",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call with swVersion being an invalid version string
		 * Expected:
		 * 	- version UNKNOWN returned
		 */
		setField(readerInfo, "swVersion", "");
		assertEquals("UNKNOWN",  nurHwMgr.getFirmwareVersion());
		
		/*
		 * Test:
		 * 	- call of getReaderInfo throwing an exception
		 * Expected:
		 * 	- ImplementationException thrown
		 */
		new NonStrictExpectations() {{			
			nurApi.getReaderInfo();
			result = new Exception();			
		}};
		
		try {
			nurHwMgr.getFirmwareVersion();
			fail("Exception expected.");
		} catch (ImplementationException e) {

		}
		
	}
	
	@Test
	public void testInstallFirmware(@Mocked final FirmwareUpdater fwUpdater) throws ConnectionException, ImplementationException, ExecutionException {
		final NurHardwareManager nurHwMgr = new NurHardwareManager();
		new NonStrictExpectations(nurHwMgr) {{
			nurHwMgr.openConnection();
			result = null;
			
			nurHwMgr.closeConnection();
			result = null;
		}};
		
		nurHwMgr.installFirmware();
		
		new Verifications() { 
			{
				nurHwMgr.closeConnection();
				times = 1;
				
				fwUpdater.execute();
				times = 1;
				
				nurHwMgr.openConnection();
				times = 1;
			}
		};
		
		new NonStrictExpectations() {{
			nurHwMgr.openConnection();
			result = null;
			
			nurHwMgr.closeConnection();
			result = null;
			
			fwUpdater.execute();
			result = new ExecutionException();
		}};
		
		try { 
			nurHwMgr.installFirmware();
			fail("Exception expected.");
		} catch (ImplementationException iex) { }
		
		new Verifications() { 
			{
				nurHwMgr.closeConnection();
				times = 1;
				
				fwUpdater.execute();
				times = 1;
				
				nurHwMgr.openConnection();
				times = 1;
			}
		};
		
	}
	
	@Test
	public void testGetMaxAntennas() throws Exception {
		NurHardwareManager nurHwMgr = new NurHardwareManager();
		setField(nurHwMgr, "nurApi", nurApi);
		
		new NonStrictExpectations() {{			
			nurApi.getGPIOConfigure();
			result = new Exception();			
		}};
				
		assertEquals(1, nurHwMgr.getMaxAntennas()); 
		
		new NonStrictExpectations() {{			
			nurApi.getGPIOConfigure();
			result = null;			
		}};
		
		assertEquals(1, nurHwMgr.getMaxAntennas());

		final NurGPIOConfig[] gpioConfigs1 = new NurGPIOConfig[1];
		
		final NurGPIOConfig cfg1 = new NurGPIOConfig();
		cfg1.type = NurGPIOConfig.GPIO_TYPE_ANTCTL1;
		
		final NurGPIOConfig cfg2 = new NurGPIOConfig();
		cfg2.type = NurGPIOConfig.GPIO_TYPE_ANTCTL2;
		
		new NonStrictExpectations() {{			
			nurApi.getGPIOConfigure();
			result = gpioConfigs1;			
		}};
		
		gpioConfigs1[0] = cfg1;
		assertEquals(2, nurHwMgr.getMaxAntennas());
		
		gpioConfigs1[0] = cfg2;
		assertEquals(2, nurHwMgr.getMaxAntennas());
		
		final NurGPIOConfig[] gpioConfigs2 = new NurGPIOConfig[2];
		
		new NonStrictExpectations() {{			
			nurApi.getGPIOConfigure();
			result = gpioConfigs2;
		}};
		
		gpioConfigs2[0] = cfg1;
		gpioConfigs2[1] = cfg2;
		assertEquals(4, nurHwMgr.getMaxAntennas());
		
		gpioConfigs2[0] = cfg2;
		gpioConfigs2[1] = cfg1;
		assertEquals(4, nurHwMgr.getMaxAntennas());
		
	}
	
	@Test
	public void testEmptyEventListeners() {
		NurApiListener nurApiListener = new NurApiListenerImpl();
		nurApiListener.connectedEvent();
		nurApiListener.IOChangeEvent(null);
		nurApiListener.bootEvent(null);
		nurApiListener.clientConnectedEvent(null);
		nurApiListener.clientDisconnectedEvent(null);
		nurApiListener.debugMessageEvent(null);
		nurApiListener.deviceSearchEvent(null);
		nurApiListener.epcEnumEvent(null);
		nurApiListener.frequencyHopEvent(null);
		nurApiListener.inventoryExtendedStreamEvent(null);
		nurApiListener.inventoryStreamEvent(null);
		nurApiListener.logEvent(0, null);
		nurApiListener.nxpEasAlarmEvent(null);
		nurApiListener.programmingProgressEvent(null);
		nurApiListener.traceTagEvent(null);
		nurApiListener.triggeredReadEvent(null);
	}
}
