package havis.device.rf.nur;

import havis.device.rf.common.Environment;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.nur.NurErrorMap.RFCError;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.CustomOperation;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nordicid.nurapi.CustomExchangeParams;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurRespCustomExchange;
import com.nordicid.nurapi.NurTag;

/**
 * This class implements C1G2 commands (read, write, lock, kill) hardware
 * specifically for the Nordic ID NUR API and provides several low-level tools
 * for singulation and EPC-bank reading.
 * 
 */
class NurTagProcessor {

	private static final byte[] ETB_SENSOR_BYTES_DEPRECATED = new byte[] { (byte) 0xE0, 0x02, 0x48, 0x00, 0x00, 0x00 };
	private static final byte[] ETB_SENSOR_VENDOR_ID = new byte[] { 0x00, 0x0B };
	private static final byte[] ETB_SENSOR_MODEL_ID = new byte[] { 0x00, 0x40 };

	private static final Logger log = Logger.getLogger(NurTagProcessor.class.getName());

	private NurApi nurApi;

	/**
	 * least number of words that can be reliably read from TID bank (48 bit = 6
	 * bytes = 3 words)
	 */
	protected static final int WORD_COUNT_TID_BANK = 3;

	/**
	 * least number of words a TID bank with XTID and serial part contains (96
	 * bit = 12 byte = 6 words)
	 */
	private static final int WORD_COUNT_TID_BANK_WITH_SERIAL = 6;

	/**
	 * number of words of the reserved bank (2 * 32 bit = 2 * 2 words = 4 words)
	 */
	private static final short WORD_COUNT_RSV_BANK = 4;
	private static short USR_BANK_WORD_COUNT = 32;

	/**
	 * Creates an instance of this class.
	 * 
	 * @param nurApi
	 *            a NurApi instance
	 * @throws ImplementationException
	 *             if reading the {@link Environment} properties fails.
	 */
	public NurTagProcessor(NurApi nurApi) throws ImplementationException {
		super();
		this.nurApi = nurApi;
		if (Environment.COMPLETE_USERBANK_WORD_COUNT != null)
			USR_BANK_WORD_COUNT = Environment.COMPLETE_USERBANK_WORD_COUNT;

		log.finer("NurTagProcessor instanciated.");
	}

	/**
	 * Reads a complete memory bank.
	 * 
	 * @param sing
	 *            a {@link Singulation} instance
	 * @param ro
	 *            a {@link ReadOperation} instance
	 * @param tagData
	 *            a {@link TagData} instance. This is only required if read
	 *            operation reads EPC bank.
	 * @param nurTag
	 *            a {@link NurTag} instance. This is only required if read
	 *            operation reads EPC bank.
	 * 
	 * @param irData
	 *            a byte array containing the data having been read during
	 *            inventory (IR data). This is only required if read operation
	 *            reads TID bank.
	 * 
	 * @return a {@link ReadResult} instance
	 */
	protected ReadResult readCompleteBank(Singulation sing, ReadOperation ro, TagData tagData, NurTag nurTag, byte[] irData) {

		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "readCompleteBank", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(ro), RFUtils.serialize(tagData),
					RFUtils.serialize(irData) });

		if (ro.getBank() == NurApi.BANK_EPC) {
			ReadResult rdRes = new ReadResult();

			byte[] crc = RFUtils.shortToBytes(tagData.getCrc());
			byte[] pc = RFUtils.shortToBytes(tagData.getPc());
			byte[] epc = nurTag.getUserdata() != null ? (byte[]) nurTag.getUserdata() : tagData.getEpc();
			byte[] rdData = new byte[crc.length + pc.length + epc.length];

			for (int i = 0; i < crc.length; i++)
				rdData[i] = crc[i];
			for (int i = 0; i < pc.length; i++)
				rdData[crc.length + i] = pc[i];
			for (int i = 0; i < epc.length; i++)
				rdData[crc.length + pc.length + i] = epc[i];

			if (ro.getOffset() > 0)
				rdData = Arrays.copyOfRange(rdData, ro.getOffset() * 2, rdData.length);

			rdRes.setReadData(rdData);
			rdRes.setOperationId(ro.getOperationId());
			rdRes.setResult(Result.SUCCESS);

			if (log.isLoggable(Level.FINER))
				log.exiting(getClass().getName(), "readCompleteBank", RFUtils.serialize(rdRes));

			return rdRes;
		}

		else if (ro.getBank() == NurApi.BANK_TID) {

			byte[] tidData;

			/*
			 * if irData is null or contains less than the securely readable
			 * words
			 */
			if (irData == null || irData.length < WORD_COUNT_TID_BANK) {

				/*
				 * read the securely readable amount of words from the TID bank
				 * to tidData array
				 */
				ReadOperation tidRo = RFUtils.newReadOperation(ro.getOperationId() + "-tid", RFUtils.BANK_TID, 0, WORD_COUNT_TID_BANK, 0);
				ReadResult tidRes = read(sing, tidRo, null);

				/* if successful, save result in tidData */
				if (tidRes.getResult() == Result.SUCCESS && tidRes.getReadData().length > 0) {
					tidData = tidRes.getReadData();
				}

				/* if not, return read result containing the error state */
				else {
					ReadResult rdRes = new ReadResult();
					rdRes.setOperationId(ro.getOperationId());
					rdRes.setResult(tidRes.getResult());
					rdRes.setReadData(tidRes.getReadData());
					return rdRes;
				}
			}

			/* else initialize tidData from irData */
			else
				tidData = irData;

			/* calculate the length of the TID bank from irData */
			int tidLenBits = 48;

			boolean hasXtidHeader = tidData.length > 0 && (tidData[1] & 0x80) == 0x80;
			if (hasXtidHeader) {
				int serialLenBits = (tidData[4] & 0b1110_0000) >> 5;
				int serialLen = 0;
				if (serialLenBits > 0) {
					serialLen = 48 + 16 * (serialLenBits - 1);
					tidLenBits += serialLen;
				}
			} else {
				// truncate
				tidLenBits = 32;
				tidData = Arrays.copyOf(tidData, tidLenBits / 8);
			}

			/*
			 * if length is greater than the amount of data already in tidData
			 * array
			 */
			if (tidLenBits / 8 > tidData.length) {
				/* additionally read the calculated amount of words */
				ReadOperation tidRo = RFUtils.newReadOperation(ro.getOperationId() + "-tid", RFUtils.BANK_TID, 0, tidLenBits / 16, 0);
				tidData = read(sing, tidRo, null).getReadData();
			}

			ReadResult rdRes = new ReadResult();
			rdRes.setOperationId(ro.getOperationId());
			byte[] rdData = tidData;
			if (ro.getOffset() > 0)
				rdData = Arrays.copyOfRange(rdData, ro.getOffset() * 2, rdData.length);
			rdRes.setReadData(rdData);
			rdRes.setResult(Result.SUCCESS);

			if (log.isLoggable(Level.FINER))
				log.exiting(getClass().getName(), "readCompleteBank", RFUtils.serialize(rdRes));

			return rdRes;
		}

		else if (ro.getBank() == NurApi.BANK_USER) {
			ReadOperation newRdOp = RFUtils.newReadOperation(ro.getOperationId(), ro.getBank(), ro.getOffset(), USR_BANK_WORD_COUNT - ro.getOffset(),
					ro.getPassword());

			ReadResult rdRes = read(sing, newRdOp, null);

			if (rdRes.getResult() == Result.MEMORY_OVERRUN_ERROR) {
				// likely the user bank is smaller, try again with half the size
				newRdOp.setLength((short) ((USR_BANK_WORD_COUNT / 2) - ro.getOffset()));
				rdRes = read(sing, newRdOp, null);
			}

			if (log.isLoggable(Level.FINER))
				log.exiting(getClass().getName(), "readCompleteBank", RFUtils.serialize(rdRes));

			return rdRes;
		}

		else if (ro.getBank() == NurApi.BANK_PASSWD) {
			ReadOperation newRdOp = RFUtils.newReadOperation(ro.getOperationId(), ro.getBank(), ro.getOffset(), WORD_COUNT_RSV_BANK - ro.getOffset(),
					ro.getPassword());

			ReadResult rdRes = read(sing, newRdOp, null);

			if (log.isLoggable(Level.FINER))
				log.exiting(getClass().getName(), "readCompleteBank", RFUtils.serialize(rdRes));

			return rdRes;
		}

		// complete read of non-existing memory bank => return result with
		// non_spec_tag_err
		ReadResult res = new ReadResult();
		res.setReadData(new byte[] {});
		res.setOperationId(ro.getOperationId());
		res.setResult(ReadResult.Result.NON_SPECIFIC_TAG_ERROR);

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "readCompleteBank", RFUtils.serialize(res));

		return res;
	}

	/**
	 * Reads a tag based on a specific {@link ReadOperation} instance
	 * 
	 * @param sing
	 *            a {@link Singulation} instance containing the data to
	 *            singulate the tag
	 * @param ro
	 *            a {@link ReadOperation} instance
	 * @param irData
	 *            a byte array containing the data having been read during
	 *            inventory. This is optional but allows very quick TID reading
	 *            because instead of reading from tag the IR data is used for
	 *            the read operation. If this parameter is null, the read
	 *            operation will access the tag's memory.
	 * 
	 * @return a {@link ReadResult} instance
	 */
	protected ReadResult read(Singulation sing, ReadOperation ro, byte[] irData) {
		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "read", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(ro), RFUtils.serialize(irData) });

		ReadResult res = new ReadResult();
		res.setOperationId(ro.getOperationId());

		try {
			byte[] rdData = null;

			/*
			 * if TID bank is to be read, try to use IR data array, unless it is
			 * null
			 */
			if (ro.getBank() == RFUtils.BANK_TID && irData != null) {
				if ((ro.getLength() + ro.getOffset()) * 2 <= irData.length) {
					if (log.isLoggable(Level.FINER))
						log.finer("Using optimized TID bank reading for the read operation.");

					rdData = Arrays.copyOfRange(irData, 2 * ro.getOffset(), 2 * (ro.getOffset() + ro.getLength()));
				}
			}

			/*
			 * if the above did not work, rdData is still null and we will have
			 * to read from the tag
			 */
			if (rdData == null) {
				if (log.isLoggable(Level.FINER))
					log.finer("Optimized TID bank reading disabled or unsuccessful. Reading from tag.");

				if (ro.getPassword() == 0)
					rdData = nurApi.readTag(sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), ro.getBank(), ro.getOffset(),
							ro.getLength() * 2);
				else {
					rdData = nurApi.readTag(ro.getPassword(), sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), ro.getBank(), ro.getOffset(),
							ro.getLength() * 2);
				}
			}

			res.setReadData(rdData);
			res.setResult(ReadResult.Result.SUCCESS);

		} catch (NurApiException e) {
			res.setReadData(new byte[] {});
			res.setResult(NurErrorMap.nurApiExceptionToReadResult(e, ro.getPassword() != 0));
			log.log(Level.FINE, "Error occured during read: {0}", e);
		} catch (Exception e) {
			res.setReadData(new byte[] {});
			res.setResult(ReadResult.Result.NON_SPECIFIC_TAG_ERROR);
			log.log(Level.FINE, "Error occured during read: {0}", e);
		}
		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "read", RFUtils.serialize(res));
		return res;

	}

	/**
	 * Writes data to a tag based on a {@link WriteOperation} instance.
	 * 
	 * @param sing
	 *            a {@link Singulation} instance containing the singulation
	 *            data.
	 * @param wo
	 *            a {@link WriteOperation} instance.
	 * @return a {@link WriteResult} instance.
	 */
	protected WriteResult write(Singulation sing, WriteOperation wo) {
		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "write", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(wo) });

		WriteResult res = new WriteResult();
		res.setOperationId(wo.getOperationId());
		try {

			if (wo.getPassword() == 0)
				nurApi.writeTag(sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), wo.getBank(), wo.getOffset(), wo.getData().length,
						wo.getData());
			else
				nurApi.writeTag(wo.getPassword(), sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), wo.getBank(), wo.getOffset(),
						wo.getData().length, wo.getData());

			res.setResult(WriteResult.Result.SUCCESS);
			res.setWordsWritten((short) (wo.getData().length / 2));

		} catch (NurApiException e) {
			log.log(Level.FINE, "Error occured during write: {0}", e);
			res.setResult(NurErrorMap.nurApiExceptionToWriteResult(e, wo.getPassword() != 0));

		} catch (Exception e) {
			log.log(Level.FINE, "Error occured during write: {0}", e);
			res.setResult(WriteResult.Result.NON_SPECIFIC_TAG_ERROR);
		}

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "write", RFUtils.serialize(res));

		return res;
	}

	/**
	 * Locks a tag based on a {@link LockOperation} instance.
	 * 
	 * @param sing
	 *            a {@link Singulation} instance containing the singulation
	 *            data.
	 * @param lo
	 *            a {@link LockOperation} instance.
	 * @return a {@link LockResult} instance.
	 * @throws ParameterException
	 *             if the lock operation contains an unexpected privilege or
	 *             field.
	 */
	protected LockResult lock(Singulation sing, LockOperation lo) throws ParameterException {

		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "lock", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(lo) });

		LockResult res = new LockResult();
		res.setOperationId(lo.getOperationId());

		try {
			int lockAction;
			switch (lo.getPrivilege()) {
			case UNLOCK:
				lockAction = NurApi.LOCK_ACTION_OPEN;
				break;
			case LOCK:
				lockAction = NurApi.LOCK_ACTION_SECURED;
				break;
			case PERMAUNLOCK:
				lockAction = NurApi.LOCK_ACTION_PERMAWRITE;
				break;
			case PERMALOCK:
				lockAction = NurApi.LOCK_ACTION_PERMALOCK;
				break;
			default:
				throw new ParameterException("Unrecognized enum constant for privilege received: " + lo.getPrivilege());
			}

			int lockMemory;
			switch (lo.getField()) {
			case ACCESS_PASSWORD:
				lockMemory = NurApi.LOCK_MEMORY_ACCESSPWD;
				break;
			case KILL_PASSWORD:
				lockMemory = NurApi.LOCK_MEMORY_KILLPWD;
				break;
			case EPC_MEMORY:
				lockMemory = NurApi.LOCK_MEMORY_EPCMEM;
				break;
			case TID_MEMORY:
				lockMemory = NurApi.LOCK_MEMORY_TIDMEM;
				break;
			case USER_MEMORY:
				lockMemory = NurApi.LOCK_MEMORY_USERMEM;
				break;
			default:
				throw new ParameterException("Unrecognized enum constant for field received: " + lo.getField());
			}

			nurApi.setLock(lo.getPassword(), sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), lockMemory, lockAction);

			res.setResult(LockResult.Result.SUCCESS);

		} catch (ParameterException ie) {
			throw ie;

		} catch (NurApiException e) {
			log.log(Level.FINE, "Error occured during lock: {0}", e);
			res.setResult(NurErrorMap.nurApiExceptionToLockResult(e));

		} catch (Exception e) {
			log.log(Level.FINE, "Error occured during lock: {0}", e);
			res.setResult(LockResult.Result.NON_SPECIFIC_TAG_ERROR);

		}

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "lock", RFUtils.serialize(res));

		return res;
	}

	/**
	 * Kills a tag based on a {@link KillOperation} instance.
	 * 
	 * @param sing
	 *            a {@link Singulation} instance containing singulation data.
	 * @param ko
	 *            a {@link KillOperation} instance.
	 * @return a {@link KillResult} instance.
	 */
	protected KillResult kill(Singulation sing, KillOperation ko) {
		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "kill", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(ko) });

		KillResult res = new KillResult();
		res.setOperationId(ko.getOperationId());

		try {
			if (ko.getKillPassword() == 0) {
				res.setResult(NurErrorMap.rfcErrorToKillResult(RFCError.ZeroKillPasswordError));

			} else {
				nurApi.killTag(ko.getKillPassword(), sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData());
				res.setResult(KillResult.Result.SUCCESS);
			}

		} catch (NurApiException e) {
			log.log(Level.FINE, "Error occured during kill: {0}", e);
			res.setResult(NurErrorMap.nurApiExceptionToKillResult(e));
		} catch (Exception e) {
			log.log(Level.FINE, "Error occured during kill: {0}", e);
			res.setResult(KillResult.Result.NON_SPECIFIC_TAG_ERROR);
		}

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "kill", RFUtils.serialize(res));

		return res;
	}

	/**
	 * 
	 * @param sing
	 * @param cOp
	 * @return
	 */
	protected CustomResult custom(NurTag tag, Singulation sing, CustomOperation cOp) {
		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "custom", new Object[] { RFUtils.serialize(sing), RFUtils.serialize(cOp) });

		CustomResult res = new CustomResult();
		res.setOperationId(cOp.getOperationId());
		CustomOperation op = extractCustomData(tag, cOp);

		if (op != null) {
			try {
				short bitCount = op.getLength();
				byte[] cmdInput = op.getData();

				if (bitCount == 0)
					bitCount = (short) (cmdInput.length * 8);
				int byteBufferSize = bitCount / 8;
				if (bitCount % 8 > 0)
					byteBufferSize += 1;
				ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize);

				int bitsInBuffer = 0;
				for (int iByte = 0; bitCount - iByte * 8 > 0; iByte++) {
					/*
					 * remaining bit count contains another full byte, add full
					 * byte to the buffer
					 */
					if (bitCount - iByte * 8 > 8) {
						byteBuffer.put(cmdInput[iByte]);
						bitsInBuffer += 8;
					}
					/* else add the remaining bits and break */
					else {
						/*
						 * mask the remaining bits from left to right, (e.g. for
						 * 6 bit: 10110101 & 11111100 = 10110100) and add
						 * resulting byte to the buffer.
						 */
						int remainingBits = bitCount - iByte * 8;
						byte mask = (byte) (255 << 8 - remainingBits);
						byte data = (byte) (cmdInput[iByte] & mask);
						byteBuffer.put(data);
						bitsInBuffer += remainingBits;
						break;
					}

				}
				byteBuffer.flip();

				byte[] cmdData = byteBuffer.array();
				if (log.isLoggable(Level.FINER))
					log.log(Level.FINER, "Sending custom command: {0}, bit count: {1}", new Object[] { RFUtils.bytesToHex(cmdData), bitsInBuffer });

				CustomExchangeParams customExchangeParams = new CustomExchangeParams();
				customExchangeParams.appendHandle = true;
				customExchangeParams.rxStripHandle = true;
				customExchangeParams.asWrite = true;
				customExchangeParams.bitBuffer = cmdData;
				customExchangeParams.txLen = bitsInBuffer;
				customExchangeParams.rxLenUnknown = true;

				NurRespCustomExchange resp = nurApi.customExchange(op.getPassword(), op.getPassword() > 0, sing.getBank(), sing.getAddr(),
						sing.getBitCount(), sing.getData(), customExchangeParams);

				byte[] tagBytes = resp.getTagBytes();
				res.setResult(CustomResult.Result.SUCCESS);
				res.setResultData(tagBytes);

			} catch (NurApiException e) {
				log.log(Level.FINE, "Error occured during custom command: {0}", e);
				res.setResult(NurErrorMap.nurApiExceptionToCustomResult(e, op.getPassword() != 0));

			} catch (Exception e) {
				log.log(Level.FINE, "Error occured during custom command: {0}", e);
				res.setResult(CustomResult.Result.NON_SPECIFIC_TAG_ERROR);
			}

		} else {
			res.setResult(CustomResult.Result.OP_NOT_POSSIBLE_ERROR);
		}

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "custom", RFUtils.serialize(res));

		return res;
	}

	private CustomOperation extractCustomData(NurTag tag, CustomOperation operation) {
		if (operation.getLength() == 44 && RFUtils.equal(operation.getData(), ETB_SENSOR_BYTES_DEPRECATED, 44)) {
			// Support deprecated bytes for ETB sensor tag
			return operation;
		}

		byte[] tid = tag.getIrData();
		if (operation.getLength() > 32 && operation.getData() != null && operation.getData().length > 4 && tid != null && tid.length >= 4) {
			// ETB sensor
			if (operation.getData()[0] == ETB_SENSOR_VENDOR_ID[0] && operation.getData()[1] == ETB_SENSOR_VENDOR_ID[1]
					&& operation.getData()[2] == ETB_SENSOR_MODEL_ID[0] && operation.getData()[3] == ETB_SENSOR_MODEL_ID[1]
					&& matchesVendorAndModel(tid, ETB_SENSOR_VENDOR_ID, ETB_SENSOR_MODEL_ID)) {
				byte[] data = new byte[operation.getData().length - 4];
				for (int i = 0; i < data.length; i++)
					data[i] = operation.getData()[i + 4];
				return new CustomOperation(operation.getOperationId(), (short) (operation.getLength() - 32), data, operation.getPassword());
			}
		}

		return null;
	}

	private boolean matchesVendorAndModel(byte[] tid, byte[] vendor, byte[] model) {
		return /* vendor */RFUtils.equal(vendor, RFUtils.shift(new byte[] { tid[1], tid[2] }, -4), 9, 7)
				&& /* model */RFUtils.equal(model, new byte[] { tid[2], tid[3] }, 12, 4);
	}

	/**
	 * Reads the EPC bank data of a {@link NurTag} instance. If readCrc is
	 * false, this is done very quickly without reading from the tag.
	 * 
	 * @param sing
	 *            a {@link Singulation} instance containing singulation data.
	 * @param tag
	 *            a {@link NurTag} instance.
	 * @param readCrc
	 *            true if CRC is to be read (leads to additional read operation
	 *            on tag and will cost performance), false otherwise
	 * @return an {@link EpcBankData} instance.
	 */
	protected EpcBankData readEpcBankData(Singulation sing, NurTag tag, boolean readCrc) {

		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "readEpcBankData", new Object[] { RFUtils.serialize(sing), tag, readCrc });

		EpcBankData res = null;

		final short pc = (short) tag.getPC();
		final short crcWdCnt = 1;
		final short pcWdCnt = 1;

		/* value of bits 10h-14h (as short) */
		final short epcWdCnt = (short) (pc >> 11);

		/* value of XI bit (16h); */
		final short xi = (short) ((pc & 0x200) >> 9);

		/*
		 * to get the UMI flag from the current pc instead of using the
		 * tag.getPC() method because user-bank content could habe changed the
		 * UMI flag and the result of the tag.getPC() method might not be
		 * up-to-date, use this: final short pc = (short)((data[2] & 0xff) << 8
		 * | data[3] & 0xff);
		 */

		try {
			byte[] data = null;
			short crc = 0;
			short xpc = 0;

			/* no xpc: only read number of words determined by crc, pc and epc */
			if (xi == 0) {
				if (readCrc) {
					final short totalWdCnt = (short) (crcWdCnt + pcWdCnt + epcWdCnt);
					data = nurApi.readTag(sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), NurApi.BANK_EPC, 0x0, totalWdCnt * 2);

					/* crc: first word of EPC bank */
					crc = (short) ((data[0] & 0xff) << 8 | data[1] & 0xff);
				} else
					crc = 0;
			}

			/* xpc exists: read 22 words (0x000..0x21f) */
			else {
				final short totalWdCnt = (short) 22;
				data = nurApi.readTag(sing.getBank(), sing.getAddr(), sing.getBitCount(), sing.getData(), NurApi.BANK_EPC, 0x0, totalWdCnt * 2);

				/* get 22nd word of EPC bank (0x210..0x21f) */
				xpc = (short) (data[42] << 8 | data[43]);

				/* crc: first word of EPC bank */
				crc = (short) ((data[0] & 0xff) << 8 | data[1] & 0xff);
			}

			/* value of UMI bit (15h) */
			final short umi = (short) ((pc & 0x400) >> 10);

			res = new EpcBankData(crc, pc, tag.getEpc(), xpc, umi);

		} catch (NurApiException e) {
			log.log(Level.FINE, "Error occured during reading EPC bank: {0}", e);
			res = new EpcBankData(NurErrorMap.nurApiExceptionToReadResult(e, false));
		} catch (Exception e) {
			log.log(Level.FINE, "Error occured during reading EPC bank: {0}", e);
			res = new EpcBankData(ReadResult.Result.NON_SPECIFIC_TAG_ERROR);
		}
		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "readEpcBankData", RFUtils.serialize(res));

		return res;
	}

	/**
	 * Calculates the optimal sinuglation (i.e. strategy and data) based on the
	 * filters passed.
	 * 
	 * @param filters
	 *            a List of {@link Filter} objects.
	 * @return a {@link Singulation} instance containing optimal singulation
	 *         data.
	 */
	protected Singulation getOptimalSingulation(List<Filter> filters) {

		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "getOptimalSingulation", RFUtils.serializeList(filters, Filter.class));

		if (filters == null || filters.size() == 0) {
			Singulation res = Singulation.getEPCInstance(null);

			if (log.isLoggable(Level.FINER))
				log.exiting(getClass().getName(), "getOptimalSingulation", RFUtils.serialize(res));

			return res;
		}

		else {
			filterLoop: for (Filter filter : filters) {
				if (filter.getBank() != NurApi.BANK_TID)
					continue;

				if (filter.getData() != null && filter.getData().length >= WORD_COUNT_TID_BANK_WITH_SERIAL * 2
						&& filter.getMask().length >= WORD_COUNT_TID_BANK_WITH_SERIAL * 2 && filter.getBitOffset() == 0 && filter.isMatch()
						&& filter.getBitLength() >= WORD_COUNT_TID_BANK_WITH_SERIAL * 16) {

					/* skip if mask contains bytes other than 0xff */
					for (int i = 0; i < filter.getData().length; i++)
						if (filter.getMask()[i] != (byte) 0xff)
							continue filterLoop;

					/* skip if filter has not XTID bit set */
					if ((filter.getData()[1] & 0x80) != 0x80)
						continue;

					/* skip if serialization bits are 0 */
					if (((filter.getData()[4] & 0xe0) >> 5) == 0)
						continue;

					/*
					 * if none of the above was true, the filter data suffices
					 * for singulation by TID
					 */
					Singulation res = Singulation.getTIDInstance(filter.getData());

					if (log.isLoggable(Level.FINER))
						log.exiting(getClass().getName(), "getOptimalSingulation", RFUtils.serialize(res));

					return res;
				}
			}
		}

		Singulation res = Singulation.getEPCInstance(null);
		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "getOptimalSingulation", RFUtils.serialize(res));
		return res;
	}

	/**
	 * Class to hold data of the EPC bank in a structured way.
	 * 
	 */
	public static class EpcBankData {
		private short crc;
		private short pc;
		private byte[] epc;
		private short xpc;
		private short umi;
		private ReadResult.Result result;

		/**
		 * Creates an instance of this class. This instance's read result will
		 * automaticly set to ReadResult.SUCCESS when created with this
		 * constructor.
		 * 
		 * @param crc
		 *            the tag's CRC
		 * @param pc
		 *            the tag's PC
		 * @param epc
		 *            the tag's EPC
		 * @param xpc
		 *            the tag's XPC
		 * @param umi
		 *            the tag's UMI
		 */
		public EpcBankData(short crc, short pc, byte[] epc, short xpc, short umi) {
			super();
			this.crc = crc;
			this.pc = pc;
			this.epc = epc;
			this.xpc = xpc;
			this.umi = umi;
			this.result = ReadResult.Result.SUCCESS;

			if (log.isLoggable(Level.FINER))
				log.log(Level.FINER, "EpcBankData instanciated with crc={0}, pc={1}, epc={2}, xpc={3}, umi={4}, result={5}",
						new Object[] { crc, pc, RFUtils.serialize(epc), xpc, umi, this.result });
		}

		/**
		 * Creates an instance of this class. This constructor is to be used to
		 * create an instance indicating an error result. Therefore, this
		 * constructor does not save any data of the EPC bank but only forms a
		 * wrapper instance for a specific {@link ReadResult} instance that is
		 * not ReadResult.SUCCESS.
		 * 
		 * @param result
		 *            a {@link ReadResult} instance.
		 */
		public EpcBankData(ReadResult.Result result) {
			super();
			this.result = result;
			this.epc = new byte[] {};
			if (log.isLoggable(Level.FINER))
				log.log(Level.FINER, "EpcBankData instanciated with read result={0}", RFUtils.serialize(result));
		}

		/**
		 * Returns the tag's CRC.
		 * 
		 * @return the tag's CRC.
		 */
		public short getCrc() {
			return crc;
		}

		/**
		 * Returns the tag's PC.
		 * 
		 * @return the tag's PC.
		 */
		public short getPc() {
			return pc;
		}

		/**
		 * Returns the tag's EPC.
		 * 
		 * @return the tag's EPC.
		 */
		public byte[] getEpc() {
			return epc;
		}

		/**
		 * Returns the tag's XPC.
		 * 
		 * @return the tag's XPC.
		 */
		public short getXpc() {
			return xpc;
		}

		/**
		 * Returns the tag's UMI.
		 * 
		 * @return the tag's UMI.
		 */
		public short getUmi() {
			return umi;
		}

		/**
		 * Returns the {@link ReadResult} instance of the EPC bank read
		 * operation.
		 * 
		 * @return the {@link ReadResult} instance of the EPC bank read
		 *         operation.
		 */
		public ReadResult.Result getResult() {
			return result;
		}
	}

	/**
	 * This class provides functionality for tag singulation, i.e. to identify a
	 * transponder uniquely.
	 * 
	 */
	public static class Singulation {
		private static final int EPC_SING_ADDR = 0x20;
		private static final int TID_SING_ADDR = 0x00;
		private static final int MAX_SING_LEN = 0xFF;

		/**
		 * Enum for tag singulation strategies.
		 * 
		 */
		enum SingulationStrategy {
			/**
			 * Strategy for tag singulation by EPC.
			 */
			EPC,
			/**
			 * Strategy for tag singulation by TID.
			 */
			TID
		}

		private int sBank = 0;
		private int sAddr = 0x00;
		private byte[] sData = null;
		private SingulationStrategy strategy;

		/**
		 * Creates an instance of this class.
		 * 
		 * @param strategy
		 *            a {@link SingulationStrategy} instance.
		 * @param sBank
		 *            the memory bank to be used for singulation.
		 * @param sAddr
		 *            the address of the singulation data.
		 * @param sData
		 *            the singulation data to be used to singulate the tag.
		 */
		private Singulation(SingulationStrategy strategy, int sBank, int sAddr, byte[] sData) {
			super();
			this.sBank = sBank;
			this.sAddr = sAddr;
			this.sData = sData;
			this.strategy = strategy;

			if (log.isLoggable(Level.FINER))
				log.log(Level.FINER, "Singulation instanciated with strategy={0}, sBank={1}, sAddr={2}, sData={3}", new Object[] { strategy, sBank, sAddr,
						RFUtils.serialize(sData) });
		}

		/**
		 * Creates a new instance of this class based on a given singulation
		 * strategy and a NurTag object.
		 * 
		 * @param singStrategy
		 *            an instance of {@link SingulationStrategy}
		 * @param tag
		 *            a NurTag object
		 * @return a {@link Singulation} instance
		 */
		public static Singulation getInstance(SingulationStrategy singStrategy, NurTag tag) {
			if (singStrategy == SingulationStrategy.TID)
				return new Singulation(singStrategy, NurApi.BANK_TID, TID_SING_ADDR, tag.getIrData());

			else
				return new Singulation(SingulationStrategy.EPC, NurApi.BANK_EPC, EPC_SING_ADDR, tag.getUserdata() != null
						&& tag.getUserdata() instanceof byte[] ? (byte[]) tag.getUserdata() : tag.getEpc());
		}

		/**
		 * Creates a {@link Singulation} instance for singulation by TID using
		 * the given byte array.
		 * 
		 * @param tid
		 *            a TID as byte array
		 * @return a {@link Singulation} instance
		 */
		public static Singulation getTIDInstance(byte[] tid) {
			return new Singulation(SingulationStrategy.TID, NurApi.BANK_TID, TID_SING_ADDR, tid);
		}

		/**
		 * Creates a {@link Singulation} instance for singulation by EPC using
		 * the given byte array.
		 * 
		 * @param epc
		 *            an EPC as byte array
		 * @return a {@link Singulation} instance
		 */
		public static Singulation getEPCInstance(byte[] epc) {
			return new Singulation(SingulationStrategy.EPC, NurApi.BANK_EPC, EPC_SING_ADDR, epc);
		}

		/**
		 * Method to update a NurTag's EPCs that has been changed by a given
		 * {@link WriteOperation}. Goal of this method is to allow the tag still
		 * to be identified (singulated) once the EPC has been changed.
		 * 
		 * @param nurTag
		 *            a NurTag instance
		 * @param wrOp
		 *            a {@link WriteOperation} instance
		 * @return the new EPC with the applied changes from the
		 *         {@link WriteOperation}
		 */
		protected byte[] epcChanged(NurTag nurTag, WriteOperation wrOp) {
			/*
			 * If the write operation does not write to EPC bank it won't change
			 * the EPC, so return null
			 */
			if (wrOp.getBank() != RFUtils.BANK_EPC)
				return null;

			/*
			 * Get the starting word address of the write operation, i.e. the
			 * offset
			 */
			int srcStartAddr = wrOp.getOffset();

			/* Calculate the end word address of the write operation */
			int srcEndAddr = wrOp.getOffset() + wrOp.getData().length / 2;

			/*
			 * If the write operation only writes data before or after the EPC
			 * bytes, the EPC won't be changed, thus return null
			 */
			if (srcEndAddr <= 2 || srcStartAddr >= 8)
				return null;

			/*
			 * Get the current EPC from the NUR tag object (use EPC field if
			 * user data is empty and use user data field otherwise, which means
			 * that EPC of this tag object has been changed before.)
			 */
			byte[] oldEpc = (nurTag.getUserdata() != null && nurTag.getUserdata() instanceof byte[]) ? (byte[]) nurTag.getUserdata() : nurTag.getEpc();

			/* Copy the old EPC to a new array */
			byte[] newEpc = Arrays.copyOf(oldEpc, oldEpc.length);

			/*
			 * Calculate the offset from which to start reading the source
			 * bytes. If for instance the offset of the write op. is 0 the first
			 * two words (4 bytes) are skipped and reading begins at word 3
			 * because words 1 and 2 are CRC and PC. If the offset is greater
			 * than 2 however, the source data will be read from beginning
			 * (srcByteOffset = 0)
			 */
			int srcByteOffset = wrOp.getOffset() <= 2 ? 4 - wrOp.getOffset() * 2 : 0;

			/*
			 * Calculate the offset from which to start writing the target
			 * bytes. If the offset of the write operation is greater than two,
			 * this offset (decremented by 2 words [CRC and PC]) is applied to
			 * the target array when writing the bytes.
			 */
			int trgByteOffset = wrOp.getOffset() > 2 ? wrOp.getOffset() * 2 - 4 : 0;

			/*
			 * Copy the bytes of the new EPC using the offsets calculated into
			 * to the old EPC. This method makes sure that the EPC bytes do not
			 * overflow, i.e. the new bytes are only written until the length of
			 * the old EPC is reached, so the length of the EPC is not changed.
			 */
			for (int i = 0; i < wrOp.getData().length - srcByteOffset && i + trgByteOffset < newEpc.length; i++)
				newEpc[i + trgByteOffset] = wrOp.getData()[i + srcByteOffset];

			/*
			 * Store the changed EPC in the user data field of the NUR tag since
			 * the EPC field itself is read-only.
			 */
			nurTag.setUserdata(newEpc);

			/* Update the singulation data if singulation strategy is EPC. */
			if (this.strategy == SingulationStrategy.EPC)
				this.sData = newEpc;

			if (log.isLoggable(Level.FINER))
				log.log(Level.FINER, "New EPC {0} attached to NurTag object {1}", new Object[] { RFUtils.serialize(newEpc), nurTag });

			/* Return the new EPC bytes. */
			return newEpc;
		}

		/**
		 * Returns the singulation memory bank.
		 * 
		 * @return the singulation memory bank.
		 */
		public int getBank() {
			return sBank;
		}

		/**
		 * Returns the singulation address.
		 * 
		 * @return the singulation address.
		 */
		public int getAddr() {
			return sAddr;
		}

		/**
		 * Returns the singulation data.
		 * 
		 * @return the singulation data.
		 */
		public byte[] getData() {
			return sData;
		}

		/**
		 * Returns the number of bits of the singulation data.
		 * 
		 * @return the number of bits of the singulation data.
		 */
		public int getBitCount() {
			return sData == null ? 0 : Math.min(sData.length * 8, MAX_SING_LEN);
		}

		/**
		 * Returns the {@link SingulationStrategy} of this singulation.
		 * 
		 * @return the {@link SingulationStrategy} of this singulation.
		 */
		public SingulationStrategy getStrategy() {
			return strategy;
		}
	}
}
