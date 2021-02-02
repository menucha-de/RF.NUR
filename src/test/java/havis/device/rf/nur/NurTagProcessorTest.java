package havis.device.rf.nur;

import static mockit.Deencapsulation.setField;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import havis.device.rf.common.Environment;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.nur.NurTagProcessor.EpcBankData;
import havis.device.rf.nur.NurTagProcessor.Singulation;
import havis.device.rf.nur.NurTagProcessor.Singulation.SingulationStrategy;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.CustomOperation;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.LockOperation.Field;
import havis.device.rf.tag.operation.LockOperation.Privilege;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import com.nordicid.nurapi.CustomExchangeParams;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiErrors;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurRespCustomExchange;
import com.nordicid.nurapi.NurTag;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

@SuppressWarnings("unused")
public class NurTagProcessorTest {	
	private static final Logger log = Logger.getLogger(NurTagProcessor.class.getName()); 

	@Before
	public void setup() {
		log.setLevel(Level.ALL);
	}
	
	@Test
	public void readCompleteBank(@Mocked final NurApi nurApi,
			@Mocked final NurTag nurTag, /*@Mocked final ReadOperation ro,*/
			@Mocked final TagData tagData, @Mocked final Singulation sing
			/*@Mocked final ReadResult rdRes*/)
			throws Exception {

		final byte[] epcData1 = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe };
		final byte[] epcData2 = new byte[] { (byte) 0xda, (byte) 0xed, (byte) 0xbe, (byte) 0xef };
		final byte[] tidDataNoXTid = new byte[] { (byte) 0xe2, (byte) 0x00, (byte) 0x18, (byte) 0x01, (byte) 0xaa, (byte) 0xbb };
		final byte[] tidDataNoXTidResult = new byte[] { (byte) 0xe2, (byte) 0x00, (byte) 0x18, (byte) 0x01 };
		final byte[] tidDataWithXTid = RFUtils.hexToBytes("E28011052000AABBCCDDEEFF");
		
		final byte[] crc = new byte[] { (byte)0xbe, (byte)0xef };
		final byte[] pc = new byte[] { (byte)0xaf, (byte)0xfe };
		
		final ReadOperation ro = new ReadOperation();
		ro.setBank((short)NurApi.BANK_EPC);
		ro.setOperationId("Rd-Test1");
		
		/* test complete read of EPC bank */
		new NonStrictExpectations() { {				
				tagData.getEpc();
				result = epcData1;

				tagData.getPc();
				result = RFUtils.bytesToShort(pc);

				tagData.getCrc();
				result = RFUtils.bytesToShort(crc);								
		} };

		final ReadResult rdRes1 = new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag,
				nurTag.getIrData());

		new Verifications() {
			{
				assertArrayEquals(crc, Arrays.copyOfRange(rdRes1.getReadData(), 0, 2));				
				assertArrayEquals(pc, Arrays.copyOfRange(rdRes1.getReadData(), 2, 4));
				assertArrayEquals(epcData1, Arrays.copyOfRange(rdRes1.getReadData(), 4, rdRes1.getReadData().length));
				
				assertEquals("Rd-Test1", rdRes1.getOperationId());					
			}
		};
		
		/* test complete read of EPC bank with changed EPC in userData */
		new NonStrictExpectations() {
			{
				nurTag.getUserdata();
				result = new byte[] { (byte) 0xda, (byte) 0xed, (byte) 0xbe, (byte) 0xef };
			}
		};

		final ReadResult rdRes2 = new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag,
				nurTag.getIrData());

		new Verifications() {
			{
				assertArrayEquals(crc, Arrays.copyOfRange(rdRes2.getReadData(), 0, 2));				
				assertArrayEquals(pc, Arrays.copyOfRange(rdRes2.getReadData(), 2, 4));
				assertArrayEquals(epcData2, Arrays.copyOfRange(rdRes2.getReadData(), 4, rdRes2.getReadData().length));
				assertEquals("Rd-Test1", rdRes2.getOperationId());	
			}
		};

		/* test complete read of TID bank with present IR data */		
		ro.setBank((short)NurApi.BANK_TID);
		
		new NonStrictExpectations() {{
			nurTag.getIrData();
			result = tidDataNoXTid;				
		}};		
		
		final ReadResult rdRes3 = new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag, nurTag.getIrData());		
		new Verifications() {
			{
				assertArrayEquals(tidDataNoXTidResult, rdRes3.getReadData());
				assertEquals("Rd-Test1", rdRes3.getOperationId());				
				nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, anyInt, anyInt, anyInt);
				times = 0;
			}
		};
		
		/* test complete read of TID bank with missing IR data */		
		new NonStrictExpectations() {
			{
				nurTag.getIrData();
				result = null;				
				nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK * 2);
				result = tidDataNoXTid;
			}
		};		
		final ReadResult rdRes4 = new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag, nurTag.getIrData());		
		
		new Verifications() {
			{
				assertArrayEquals(tidDataNoXTidResult, rdRes4.getReadData());
				assertEquals("Rd-Test1", rdRes4.getOperationId());
			}
		};
		
		/* test complete read of TID bank with missing IR data and TID with XTID header and serialization */		
		new NonStrictExpectations() {
			{
				nurTag.getIrData();
				result = null;				
				
				nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK * 2);
				result = Arrays.copyOf(tidDataWithXTid, 6);
				
				nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, NurApi.BANK_TID, 0, tidDataWithXTid.length);
				result = tidDataWithXTid;
			}
		};		
		final ReadResult rdRes5 = new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag, nurTag.getIrData());		
		
		new Verifications() {
			{
				assertArrayEquals(tidDataWithXTid, rdRes5.getReadData());
				assertEquals("Rd-Test1", rdRes5.getOperationId());
			}
		};
		
		/* test complete read of USER bank */
		ro.setBank((short)NurApi.BANK_USER);
		ro.setOffset((short)2);		
		new NonStrictExpectations() {
			{
				setField(Environment.class, "COMPLETE_USERBANK_WORD_COUNT", (short)32);
			}
		};
		new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag, nurTag.getIrData());
		new Verifications() {{			
			nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, ro.getBank(), ro.getOffset(), (32-ro.getOffset()) * 2);
			times = 1;
		}};

		/* test complete read of PASSWD bank */		
		ro.setBank((short)NurApi.BANK_PASSWD);
		ro.setOffset((short)0);
		new NonStrictExpectations() {
			{
				
			}
		};
		new NurTagProcessor(nurApi).readCompleteBank(sing, ro, tagData, nurTag, nurTag.getIrData());
		new Verifications() {
			{
				nurApi.readTag(anyInt, anyInt, anyInt, (byte[])any, ro.getBank(), ro.getOffset(), 8);
				times = 1;
			}
		};
		
	}

	@Test
	public void read(@Mocked final Singulation sing,
			@Mocked final NurApi nurApi, @Mocked final ReadOperation ro,
			@Mocked final ReadResult res, @Mocked final Environment env)
			throws Exception {

		final byte[] data = { (byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe };
		
		/* test read w/o password */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 0;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				result = data;
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro, null);

		new Verifications() {
			{
				byte[] rdData;
				res.setReadData(rdData = withCapture());
				assertArrayEquals(data, rdData);
			}
		};

		/* test read w/ password */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 1234;

				nurApi.readTag(withEqual(1234), anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				result = data;
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro, null);

		new Verifications() {
			{
				byte[] rdData;
				res.setReadData(rdData = withCapture());
				assertArrayEquals(data, rdData);
			}
		};

		/* test read w/ NurApiException */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 0;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				result = new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN);
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro, null);

		new Verifications() {
			{				
				ReadResult.Result rdRes;
				res.setResult(rdRes = withCapture());
				assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, rdRes);				
			}
		};

		/* test read w/ Exception */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 0;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				result = new Exception();
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro, null);

		new Verifications() {
			{
				ReadResult.Result rdRes;
				res.setResult(rdRes = withCapture());
				assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, rdRes);				
			}
		};

		/* test read from irData */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 0;

				ro.getBank();
				result = RFUtils.BANK_TID;

				ro.getLength();
				result = 2;

				ro.getOffset();
				result = 0;
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro,
				RFUtils.hexToBytes("CAFEBABE"));

		new Verifications() {
			{
				res.setReadData(with(new Delegate<byte[]>() {
					public void setReadData(byte[] data) {
						assertEquals(RFUtils.bytesToHex(data), "CAFEBABE");
					}
				}));
				times = 1;
			}
		};

		/*
		 * test attempt to read from irData but read tag data because irData has
		 * insufficient length
		 */
		new NonStrictExpectations() {
			{
				ro.getPassword();
				result = 0;

				ro.getBank();
				result = RFUtils.BANK_TID;

				ro.getLength();
				result = 2;

				ro.getOffset();
				result = 2;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				result = RFUtils.hexToBytes("DEADBEEF");
			}
		};

		new NurTagProcessor(nurApi).read(sing, ro,
				RFUtils.hexToBytes("CAFEBABE"));

		new Verifications() {
			{
				res.setReadData(with(new Delegate<byte[]>() {
					public void setReadData(byte[] data) {
						assertEquals(RFUtils.bytesToHex(data), "DEADBEEF");
					}
				}));
				times = 1;
			}
		};

	}

	@Test
	public void write(@Mocked final Singulation sing, @Mocked final NurApi nurApi, 
			@Mocked final WriteOperation wo, @Mocked final WriteResult res, @Mocked final Environment env) throws Exception {

		/* test write w/o password */
		new NonStrictExpectations() {
			{
				wo.getPassword();
				result = 0;

				wo.getData();
				result = RFUtils.hexToBytes("CAFEBABE");

				nurApi.writeTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));

			}
		};

		new NurTagProcessor(nurApi).write(sing, wo);

		new Verifications() {{
			short words;
			res.setWordsWritten(words = withCapture());
			assertEquals(2, words);

			WriteResult.Result wrRes;				
			res.setResult(wrRes = withCapture());
			assertEquals(WriteResult.Result.SUCCESS, wrRes);				
		}};

		/* test write w/ password */
		new NonStrictExpectations() {
			{
				wo.getPassword();
				result = 1234;

				wo.getData();
				result = RFUtils.hexToBytes("CAFEBABE");

				nurApi.writeTag(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
			}
		};

		new NurTagProcessor(nurApi).write(sing, wo);

		new Verifications() {{
			short words;
			res.setWordsWritten(words = withCapture());
			assertEquals(2, words);

			WriteResult.Result wrRes;				
			res.setResult(wrRes = withCapture());
			assertEquals(WriteResult.Result.SUCCESS, wrRes);
		}};

		/* test write w/ NurApiException */
		new NonStrictExpectations() {
			{
				wo.getPassword();
				result = 0;

				wo.getData();
				result = RFUtils.hexToBytes("CAFEBABE");

				nurApi.writeTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
				result = new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED);
			}
		};

		new NurTagProcessor(nurApi).write(sing, wo);

		new Verifications() {{
			WriteResult.Result wrRes;				
			res.setResult(wrRes = withCapture());
			assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, wrRes);
		}};

		/* test write w/ Exception */
		new NonStrictExpectations() {{
			wo.getPassword();
			result = 0;

			wo.getData();
			result = RFUtils.hexToBytes("CAFEBABE");

			nurApi.writeTag(anyInt, anyInt, anyInt,
					withInstanceOf(byte[].class), anyInt, anyInt, anyInt,
					withInstanceOf(byte[].class));
			result = new Exception();
		}};

		new NurTagProcessor(nurApi).write(sing, wo);

		new Verifications() { {
			WriteResult.Result wrRes;				
			res.setResult(wrRes = withCapture());
			assertEquals(WriteResult.Result.NON_SPECIFIC_TAG_ERROR, wrRes);
		} };
	}

	@Test
	public void lock(@Mocked final Singulation sing, @Mocked final NurApi nurApi, 
			@Mocked final LockOperation lo, @Mocked final LockResult res, @Mocked final Environment env)
			throws Exception {

		/* test successful lock */
		new NonStrictExpectations() {
			{
				lo.getPrivilege();
				result = Privilege.LOCK;

				lo.getField();
				result = Field.USER_MEMORY;

				nurApi.setLock(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt);
			}
		};

		new NurTagProcessor(nurApi).lock(sing, lo);

		new Verifications() {{				
			LockResult.Result lkRes;				
			res.setResult(lkRes = withCapture());
			assertEquals(LockResult.Result.SUCCESS, lkRes);							
		}};

		/*
		 * test behavior when impl exception occurrs during call of getPrivilege
	 * (simulates non-existing enum constant)
		 */
		new NonStrictExpectations() {{
			lo.getPrivilege();
			result = new ParameterException();

			lo.getField();
			result = Field.USER_MEMORY;

			nurApi.setLock(anyInt, anyInt, anyInt, anyInt,
					withInstanceOf(byte[].class), anyInt, anyInt);
		}};

		try {
			new NurTagProcessor(nurApi).lock(sing, lo);
			fail("Exception expected.");
		} catch (ParameterException e) { }
		

		/*
		 * test behavior when impl exception occurrs during call of getField
		 * (simulates non-existing enum constant)
		 */
		new NonStrictExpectations() {{
			lo.getPrivilege();
			result = Privilege.UNLOCK;

			lo.getField();
			result = new ParameterException();

			nurApi.setLock(anyInt, anyInt, anyInt, anyInt,
					withInstanceOf(byte[].class), anyInt, anyInt);
		}};

		try {
			new NurTagProcessor(nurApi).lock(sing, lo);
			fail("Exception expected.");
		} catch (ParameterException e) { }

		/* test behavior when setLock method throws an NurApiException */
		new NonStrictExpectations() { {
				lo.getPrivilege();
				result = Privilege.UNLOCK;

				lo.getField();
				result = Field.TID_MEMORY;

				nurApi.setLock(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt);
				result = new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED);
			} };

		new NurTagProcessor(nurApi).lock(sing, lo);

		new Verifications() {
			{
				LockResult.Result lkRes;				
				res.setResult(lkRes = withCapture());
				assertEquals(LockResult.Result.MEMORY_LOCKED_ERROR, lkRes);					
			}
		};

		/* test behavior when setLock method throws an exception */
		new NonStrictExpectations() {
			{
				lo.getPrivilege();
				result = Privilege.UNLOCK;

				lo.getField();
				result = Field.TID_MEMORY;

				nurApi.setLock(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt);
				result = new Exception();
			}
		};

		new NurTagProcessor(nurApi).lock(sing, lo);

		new Verifications() {
			{
				LockResult.Result lkRes;				
				res.setResult(lkRes = withCapture());
				assertEquals(LockResult.Result.NON_SPECIFIC_TAG_ERROR, lkRes);
			}
		};
	}

	@Test
	public void kill(@Mocked final Singulation sing,
			@Mocked final NurApi nurApi, @Mocked final KillOperation ko,
			@Mocked final KillResult res, @Mocked final Environment env)
			throws Exception {

		/* test kill with empty password */
		new NonStrictExpectations() {
			{
				nurApi.killTag(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
				result = null;
				
				ko.getKillPassword();
				result = 0;
			}
		};

		new NurTagProcessor(nurApi).kill(sing, ko);

		new Verifications() {
			{
				KillResult.Result kRes;
				res.setResult(kRes = withCapture());
				assertEquals(KillResult.Result.ZERO_KILL_PASSWORD_ERROR, kRes);
			}
		};
		
		/* test successful kill */
		new NonStrictExpectations() {
			{
				nurApi.killTag(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
				result = null;
				
				ko.getKillPassword();
				result = 0x1111;
			}
		};

		new NurTagProcessor(nurApi).kill(sing, ko);

		new Verifications() {
			{
				KillResult.Result kRes;
				res.setResult(kRes = withCapture());
				assertEquals(KillResult.Result.SUCCESS, kRes);
			}
		};
		
		/* test behavior when killTag method throws an NurApiException */
		new NonStrictExpectations() {
			{
				nurApi.killTag(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
				result = new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER);
			}
		};

		new NurTagProcessor(nurApi).kill(sing, ko);

		new Verifications() {
			{
				KillResult.Result kRes;
				res.setResult(kRes = withCapture());
				assertEquals(KillResult.Result.INSUFFICIENT_POWER, kRes);
			}
		};

		/* test behavior when killTag method throws an exception */
		new NonStrictExpectations() {
			{
				nurApi.killTag(anyInt, anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class));
				result = new Exception();
			}
		};

		new NurTagProcessor(nurApi).kill(sing, ko);

		new Verifications() {
			{
				KillResult.Result kRes;
				res.setResult(kRes = withCapture());
				assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, kRes);
			}
		};
	}

	@Test
	public void custom(final @Mocked Singulation sing, 
			@Mocked final NurApi nurApi,
			@Mocked final NurTag nurTag,
			@Mocked final NurRespCustomExchange nurRespCustomExchange, 
			@Mocked final Environment env) throws Exception {
		
		CustomOperation customOperation = new CustomOperation();
		NurTagProcessor nurTagProcessor = new NurTagProcessor(nurApi);
		
		/* 
		 * Test: 
		 * 	- call with uninitialized command input byte array
		 * Expected:
		 * 	- OP_NOT_POSSIBLE_ERROR
		 */		
		CustomResult res = nurTagProcessor.custom(nurTag, sing, customOperation);
		assertEquals(CustomResult.Result.OP_NOT_POSSIBLE_ERROR, res.getResult());
		
		/* 
		 * Test: 
		 * 	- call with empty command input byte array
		 *  - NUR API return INVALID_COMMAND
		 * Expected:
		 * 	- OP_NOT_POSSIBLE_ERROR
		 */		
		new NonStrictExpectations() {{			
			nurApi.customExchange(
				anyInt, anyBoolean, anyInt, 
				anyInt, anyInt, (byte[])any, 
				withInstanceOf(CustomExchangeParams.class));			
			result = new NurApiException(NurApiErrors.INVALID_COMMAND);
			nurTag.getIrData();
			result = new byte[] { (byte) 0xE2, (byte) 0x80, (byte) 0xB0, 0x40 };
		}};
		
		// ETB sensor
		customOperation.setData(new byte[] { 0x00, 0x0B, 0x00, 0x40, (byte) 0xE0, 0x02, 0x48, 0x00, 0x00, 0x00 });
		customOperation.setLength((short) 76);
		res = nurTagProcessor.custom(nurTag, sing, customOperation);		
		assertEquals(CustomResult.Result.NON_SPECIFIC_READER_ERROR, res.getResult());
		
		/* 
		 * Test: 
		 * 	- call with non-empty command input byte array
		 *  - NUR API call throws Exception
		 * Expected:
		 * 	- NON_SPECIFIC_TAG_ERRROR
		 */		
		new NonStrictExpectations() {{			
			nurApi.customExchange(
				anyInt, anyBoolean, anyInt, 
				anyInt, anyInt, (byte[])any, 
				withInstanceOf(CustomExchangeParams.class));			
			result = new Exception("Expected exception");
			nurTag.getIrData();
			result = new byte[] { (byte) 0xE2, (byte) 0x80, (byte) 0xB0, 0x40 };
		}};
		
		// ETB sensor
		customOperation.setData(new byte[] { 0x00, 0x0B, 0x00, 0x40, (byte) 0xE0, 0x02, 0x48, 0x00, 0x00, 0x00 });
		customOperation.setLength((short) 76);
		res = nurTagProcessor.custom(nurTag, sing, customOperation);		
		assertEquals(CustomResult.Result.NON_SPECIFIC_TAG_ERROR, res.getResult());
		
		new NonStrictExpectations() {{			
			nurApi.customExchange(
				anyInt, anyBoolean, anyInt, 
				anyInt, anyInt, (byte[])any, 
				withInstanceOf(CustomExchangeParams.class));			
			result = nurRespCustomExchange;
		}};
		
		/* 
		 * Test: 
		 * 	- call with ETB sensor input
		 * 
		 * Expected:
		 * 	- SUCCESS
		 */						
		// ETB sensor
		customOperation.setData(new byte[] { 0x00, 0x0B, 0x00, 0x40, (byte) 0xE0, 0x02, 0x48, 0x00, 0x00, 0x0F });
		customOperation.setLength((short) 76);
		res = nurTagProcessor.custom(nurTag, sing, customOperation);
		assertEquals(CustomResult.Result.SUCCESS, res.getResult());
		new Verifications() {{			
			CustomExchangeParams params;
			nurApi.customExchange(anyInt, anyBoolean, anyInt, 
				anyInt, anyInt, (byte[])any, params = withCapture());			
			assertArrayEquals(new byte[] { (byte) 0xE0, 0x02, 0x48, 0x00, 0x00, 0x00 }, params.bitBuffer);
			assertEquals(44, params.txLen);			
		}};	
	}
	
	@Test
	public void readEpcBankData(@Mocked final Singulation sing,
			@Mocked final NurApi nurApi, @Mocked final NurTag tag,
			@Mocked final Environment env, @Mocked final EpcBankData data)
			throws Exception {

		/* test behavior with xi == 0 and no exception */
		final short pc0 = (short) generatePC(6, false, true);
		final byte[] epcData0 = generateEpcData(6, false, true);
		final byte[] epc0 = RFUtils.hexToBytes(generateEpcStr(6));

		new NonStrictExpectations() {
			{
				tag.getPC();
				result = pc0;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class),
						withEqual(NurApi.BANK_EPC), withEqual(0x0),
						withEqual((2 + (pc0 >> 11)) * 2));
				result = epcData0;
			}
		};

		try {
			new NurTagProcessor(nurApi).readEpcBankData(sing, tag, true);
		} catch (Exception e) {
			fail();
		}

		new Verifications() {
			{
				new EpcBankData(withEqual((short) 0xcccc), withEqual(pc0),
						withInstanceLike(epc0), withEqual((short) 0),
						withEqual((short) 1));
				times = 1;
			}
		};

		/* test behavior with xi == 0 and disabled crc reading */
		new NonStrictExpectations() {
			{
				tag.getPC();
				result = pc0;

				// nurApi.readTag(anyShort, anyShort, anyShort,
				// withInstanceOf(byte[].class), withEqual(NurApi.BANK_EPC),
				// withEqual(0x0), withEqual( (2 + (pc0 >> 11)) * 2 ));
				// result = epcData0;
			}
		};

		try {
			new NurTagProcessor(nurApi).readEpcBankData(sing, tag, false);
		} catch (Exception e) {
			fail();
		}

		new Verifications() {
			{
				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class), anyInt, anyInt, anyInt);
				times = 0;

				new EpcBankData(withEqual((short) 0), withEqual(pc0),
						withInstanceLike(epc0), withEqual((short) 0),
						withEqual((short) 1));
				times = 1;
			}
		};

		/* test behavior with xi == 1 and no exception */
		final short pc1 = (short) generatePC(6, true, true);
		final byte[] epcData1 = generateEpcData(6, true, true);
		final byte[] epc1 = Arrays.copyOfRange(epcData1, 4, 16);
		final short xpc1 = (short) (epcData1[42] << 8 | epcData1[43]);
		new NonStrictExpectations() {
			{
				tag.getPC();
				result = pc1;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class),
						withEqual(NurApi.BANK_EPC), withEqual(0x0), 44);
				result = epcData1;
			}
		};

		try {
			new NurTagProcessor(nurApi).readEpcBankData(sing, tag, true);
		} catch (Exception e) {
			fail();
		}

		new Verifications() {
			{
				new EpcBankData(withEqual((short) 0xcccc), withEqual(pc1),
						withInstanceLike(epc1), withEqual(xpc1),
						withEqual((short) 1));
				times = 1;
			}
		};

		/* test behavior when NurApiException is thrown */
		final short pc2 = (short) generatePC(6, false, false);
		final byte[] epcData2 = generateEpcData(6, false, false);

		new NonStrictExpectations() {
			{
				tag.getPC();
				result = pc2;

				// nurApi.readTag(anyShort, anyShort, anyShort,
				// withInstanceOf(byte[].class), withEqual(NurApi.BANK_EPC),
				// withEqual(0x0), withEqual(6*2));
				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class),
						withEqual(NurApi.BANK_EPC), withEqual(0x0),
						withEqual((2 + (pc2 >> 11)) * 2));
				result = new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN);
			}
		};

		try {
			new NurTagProcessor(nurApi).readEpcBankData(sing, tag, true);
		} catch (Exception e) {
			fail();
		}

		new Verifications() {
			{
				new EpcBankData(
						withInstanceLike(ReadResult.Result.MEMORY_OVERRUN_ERROR));
				times = 1;
			}
		};

		/* test behavior when Exception is thrown */
		final short pc3 = (short) generatePC(6, false, false);
		final byte[] epcData3 = generateEpcData(6, false, false);

		new NonStrictExpectations() {
			{

				tag.getPC();
				result = pc3;

				nurApi.readTag(anyInt, anyInt, anyInt,
						withInstanceOf(byte[].class),
						withEqual(NurApi.BANK_EPC), withEqual(0x0),
						withEqual((2 + (pc3 >> 11)) * 2));
				result = new Exception();
			}
		};

		try {
			new NurTagProcessor(nurApi).readEpcBankData(sing, tag, true);
		} catch (Exception e) {
			fail();
		}

		new Verifications() {
			{
				new EpcBankData(
						withInstanceLike(ReadResult.Result.NON_SPECIFIC_TAG_ERROR));
				times = 1;
			}
		};
	}

	@Test
	public void getOptimalSingulation(@Mocked final NurApi nurApi,
			@Mocked Singulation singulation) throws Exception {
		List<Filter> filters;

		Filter filter = new Filter() {
			{
				setMatch(true);
				setBitOffset((short) 0);
				setBank((short) NurApi.BANK_TID);
				setData(RFUtils.hexToBytes("ffffeeee2dddccccbbbbaaaa"));
				setMask(RFUtils.hexToBytes("ffffffffffffffffffffffff"));
				setBitLength((short) (getData().length * 8));
			}
		};

		final byte[] filterBytes = RFUtils.applyMask(filter.getData(), filter.getMask());
		
		/* test behavior with TID filter w/ serial data */
		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			byte[] bytes;
			Singulation.getTIDInstance(bytes = withCapture());
			times = 1;
			assertArrayEquals(filterBytes, bytes);
		}};

		/* test behavior with TID filter w/o serial data */
		filter.setData(RFUtils.hexToBytes("ffffeeee0dddccccbbbbaaaa"));

		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			Singulation.getEPCInstance((byte[])withNull());
			times = 1;
		}};

		/* test behavior with TID filter w/ serial data and wrong offset */
		filter.setData(RFUtils.hexToBytes("ffffeeee2dddccccbbbbaaaa"));
		filter.setBitOffset((short) 1);

		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			Singulation.getEPCInstance((byte[])withNull());
			times = 2;
		}};

		/*
		 * test behavior with TID filter w/ serial data and wrong filter type
		 * (exclusive)
		 */
		filter.setMatch(false);
		filter.setBitOffset((short) 0);

		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() { {
			Singulation.getEPCInstance((byte[]) withNull());
			times = 3;
		} };

		/*
		 * test behavior with TID filter w/ serial data and wrong filter type
		 * (exclusive)
		 */
		filter.setMatch(false);

		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			Singulation.getEPCInstance((byte[]) withNull());
			times = 4;
		}};

		/* test behavior with EPC filter */
		filter.setMatch(true);
		filter.setBank((short) NurApi.BANK_EPC);

		filters = Arrays.asList(new Filter[] { filter });
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			Singulation.getEPCInstance((byte[]) withNull());
			times = 5;
		}};

		/* test behavior with empty filters list */
		filters = Arrays.asList(new Filter[] {});
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
				Singulation.getEPCInstance((byte[]) withNull());
				times = 6;
		}};

		/* test behavior with null filters list */
		filters = null;
		new NurTagProcessor(nurApi).getOptimalSingulation(filters);
		new Verifications() {{
			Singulation.getEPCInstance((byte[]) withNull());
			times = 7;
		}};

	}

	@Test
	public void epcBankDataModelTest() throws Exception {
		final short crc = (short) 0xcccc;
		final short pc = (short) 0xbbbb;
		final short umi = (short) 1;
		final short xpc = (short) 0xeeee;
		final byte[] epc = hexStrToBytes(generateEpcStr(6));

		final NurTagProcessor.EpcBankData ebd1 = new EpcBankData(crc, pc, epc,
				xpc, umi);

		final ReadResult.Result res = Result.MEMORY_OVERRUN_ERROR;
		final NurTagProcessor.EpcBankData ebd2 = new EpcBankData(res);

		new Verifications() {
			{
				assertEquals(ebd1.getCrc(), crc);
				assertEquals(ebd1.getPc(), pc);
				assertArrayEquals(ebd1.getEpc(), epc);
				assertEquals(ebd1.getXpc(), xpc);
				assertEquals(ebd1.getUmi(), umi);
				assertEquals(ebd1.getResult(), Result.SUCCESS);

				assertEquals(ebd2.getCrc(), 0);
				assertEquals(ebd2.getPc(), 0);
				assertArrayEquals(ebd2.getEpc(), new byte[] {});
				assertEquals(ebd2.getXpc(), 0);
				assertEquals(ebd2.getUmi(), 0);
				assertEquals(ebd2.getResult(), res);
			}
		};
	}

	@Test
	public void singulationModelTest(@Mocked final NurTag tag)
			throws ParameterException {
		new NonStrictExpectations() {
			{
				tag.getEpc();
				result = hexStrToBytes(generateEpcStr(6));

				tag.getIrData();
				result = generateTidData();
			}
		};
		final Singulation singEpc1 = Singulation.getInstance(
				SingulationStrategy.EPC, tag);
		final Singulation singEpc2 = Singulation.getEPCInstance(tag.getEpc());
		final Singulation singEpc3 = Singulation.getEPCInstance(null);
		final Singulation singEpc4 = Singulation.getEPCInstance(new byte[] {});

		final Singulation singTid1 = Singulation.getInstance(
				SingulationStrategy.TID, tag);
		final Singulation singTid2 = Singulation
				.getTIDInstance(tag.getIrData());
		final Singulation singTid3 = Singulation.getTIDInstance(null);
		final Singulation singTid4 = Singulation.getTIDInstance(new byte[] {});

		new Verifications() {
			{
				assertNotNull(singEpc1);
				assertNotNull(singEpc2);
				assertNotNull(singEpc3);
				assertNotNull(singEpc4);

				assertNotNull(singTid1);
				assertNotNull(singTid2);
				assertNotNull(singTid3);
				assertNotNull(singTid4);

				assertEquals(singEpc3.getBitCount(), 0);
				assertEquals(singEpc4.getBitCount(), 0);
				assertEquals(singTid3.getBitCount(), 0);
				assertEquals(singTid4.getBitCount(), 0);

				assertTrue(singEpc1.getBitCount() != 0);
				assertTrue(singEpc2.getBitCount() != 0);
				assertEquals(singEpc1.getBank(), singEpc2.getBank());
				assertEquals(singEpc1.getAddr(), singEpc2.getAddr());
				assertEquals(singEpc1.getData(), singEpc2.getData());
				assertEquals(singEpc1.getBitCount(), singEpc2.getBitCount());
				assertEquals(singEpc1.getStrategy(), singEpc2.getStrategy());

			}
		};

	}

	@Test
	public void epcChangedTest(@Mocked NurApi nurApi, @Mocked final NurTag tag)
			throws Exception {

		final byte[] oldEpc = hexStrToBytes("aaaabbbbccccddddeeeeffff"); // example
																			// EPC
																			// with
																			// 6
																			// words.

		new NonStrictExpectations() {
			{
				tag.getEpc();
				result = oldEpc;
			}
		};

		WriteOperation wrOp = new WriteOperation();
		NurTagProcessor tagProc = new NurTagProcessor(nurApi);
		Singulation sing = NurTagProcessor.Singulation.getEPCInstance(null);

		/**
		 * Test 1: Method returns null because memory bank to be written is not
		 * the EPC bank
		 **/
		wrOp.setBank(RFUtils.BANK_TID);
		byte[] newEpc = sing.epcChanged(tag, wrOp);
		assertNull(newEpc);

		/**
		 * Test 2: Method returns null because EPC is not influenced by write
		 * operation
		 **/
		wrOp.setBank(RFUtils.BANK_EPC);
		wrOp.setData(hexStrToBytes("00001111"));

		wrOp.setOffset((short) 0);
		newEpc = sing.epcChanged(tag, wrOp);
		assertNull(newEpc);

		wrOp.setOffset((short) 8);
		newEpc = sing.epcChanged(tag, wrOp);
		assertNull(newEpc);

		/** Test 3: Replaces first word of EPC with 0000 **/
		wrOp.setData(hexStrToBytes("0000"));
		wrOp.setOffset((short) 2);
		newEpc = sing.epcChanged(tag, wrOp);
		assertEquals("0000bbbbccccddddeeeeffff", RFUtils.bytesToHex(newEpc)
				.toLowerCase());

		/**
		 * Test 4: Replaces first word bytes of EPC with 3rd word of write
		 * operation data
		 **/
		wrOp.setData(hexStrToBytes("000011112222"));
		wrOp.setOffset((short) 0);
		newEpc = sing.epcChanged(tag, wrOp);
		assertEquals("2222bbbbccccddddeeeeffff", RFUtils.bytesToHex(newEpc)
				.toLowerCase());

		/** Test 5: Replaces last word bytes of EPC with 0000 **/
		wrOp.setData(hexStrToBytes("0000"));
		wrOp.setOffset((short) 7); // skip two words for PC and CRC and five
									// words of EPC
		newEpc = sing.epcChanged(tag, wrOp);
		assertEquals("aaaabbbbccccddddeeee0000", RFUtils.bytesToHex(newEpc)
				.toLowerCase());

		/**
		 * Test 6: Replaces last word bytes of EPC with first two words of write
		 * operation data (third word will be skipped)
		 **/
		wrOp.setData(hexStrToBytes("000011112222"));
		wrOp.setOffset((short) 6); // skip two words for PC and CRC and four
									// words of EPC
		newEpc = sing.epcChanged(tag, wrOp);
		assertEquals("aaaabbbbccccdddd00001111", RFUtils.bytesToHex(newEpc)
				.toLowerCase());

		/**
		 * Test 7: Test multiple calls if the method, asserting that the user
		 * data field is used during 2nd call.
		 */
		wrOp.setData(hexStrToBytes("0000"));
		wrOp.setOffset((short) 2);
		final byte[] newEpc1 = sing.epcChanged(tag, wrOp);
		assertEquals("0000bbbbccccddddeeeeffff", RFUtils.bytesToHex(newEpc1)
				.toLowerCase());

		new NonStrictExpectations() {
			{
				tag.getUserdata();
				result = newEpc1;
			}
		};

		wrOp.setData(hexStrToBytes("1111"));
		wrOp.setOffset((short) 3);
		final byte[] newEpc2 = sing.epcChanged(tag, wrOp);
		assertEquals("00001111ccccddddeeeeffff", RFUtils.bytesToHex(newEpc2)
				.toLowerCase());

	}

	private String generateEpcStr(int epcWdCnt) {
		String epcStr = "";
		for (int i = 0; i < epcWdCnt; i++)
			epcStr += "eeee";
		assertTrue(epcStr.length() >= 4);
		return epcStr;
	}

	private byte[] generateEpcData(int epcWdCnt, boolean xi, boolean umi) {
		int pc = generatePC(epcWdCnt, xi, umi);
		String pcStr = Integer.toHexString(pc);
		while (pcStr.length() < 4)
			pcStr = "0" + pcStr;

		String crcStr = "cccc";
		String epcStr = generateEpcStr(epcWdCnt);

		String dataStr = "cccc" + pcStr + epcStr;
		if (xi) // make sure that word 22 is set
		{
			while (dataStr.length() / 4 < 21)
				dataStr += "0000";
			if (dataStr.length() / 4 > 21)
				dataStr = dataStr.substring(0, 21 * 4) + "ffff"
						+ dataStr.substring(22 * 4);
			else
				dataStr += "ffff";
		}

		return hexStrToBytes(dataStr);
	}

	private byte[] generateTidData() {
		String tidStr = "e2aaabbbccccddddeeeeffff";
		return hexStrToBytes(tidStr);
	}

	private int generatePC(int epcWdCnt, boolean xi, boolean umi) {
		int pc = 0;
		pc |= epcWdCnt << 11; // //0b^^^^_^000_0000_0000;
		pc |= xi ? 0x0200 : 0x0; // 0b0000_0010_0000_0000;
		pc |= umi ? 0x0400 : 0x0; // 0b0000_0100_0000_0000;
		return pc;
	}

	private static byte[] hexStrToBytes(String hexStr) {
		hexStr = hexStr.toLowerCase();
		byte[] data = new byte[hexStr.length() / 2];
		for (int i = 0; i < hexStr.length(); i += 2)
			data[i / 2] = Integer.decode(
					(hexStr.startsWith("0x") ? "" : "0x") + hexStr.charAt(i)
							+ hexStr.charAt(i + 1)).byteValue();

		return data;
	}
}
