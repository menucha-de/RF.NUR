package havis.device.rf.nur;

import static org.junit.Assert.assertEquals;
import havis.device.rf.nur.NurErrorMap.RFCError;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.WriteResult;

import org.junit.Test;

import com.nordicid.nurapi.NurApiErrors;
import com.nordicid.nurapi.NurApiException;

public class NurErrorMapTest {

	@Test
	public void rfcErrorToKillResult() {
		KillResult.Result killRes;

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.ZeroKillPasswordError);
		assertEquals(KillResult.Result.ZERO_KILL_PASSWORD_ERROR, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.InsufficientPowerError);
		assertEquals(KillResult.Result.INSUFFICIENT_POWER, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.NonSpecificTagError);
		assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.NoResponseFromTagError);
		assertEquals(KillResult.Result.NO_RESPONSE_FROM_TAG, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.NonSpecificReaderError);
		assertEquals(KillResult.Result.NON_SPECIFIC_READER_ERROR, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.IncorrectPasswordError);
		assertEquals(KillResult.Result.INCORRECT_PASSWORD_ERROR, killRes);

		killRes = NurErrorMap.rfcErrorToKillResult(RFCError.MemoryOverrunError); 
		assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, killRes);

	}

	@Test
	public void rfcErrorToLockResult() {
		LockResult.Result lockRes;

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.InsufficientPowerError);
		assertEquals(LockResult.Result.INSUFFICIENT_POWER, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.NonSpecificTagError);
		assertEquals(LockResult.Result.NON_SPECIFIC_TAG_ERROR, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.NoResponseFromTagError);
		assertEquals(LockResult.Result.NO_RESPONSE_FROM_TAG, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.NonSpecificReaderError);
		assertEquals(LockResult.Result.NON_SPECIFIC_READER_ERROR, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.IncorrectPasswordError);
		assertEquals(LockResult.Result.INCORRECT_PASSWORD_ERROR, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.MemoryOverrunError);
		assertEquals(LockResult.Result.MEMORY_OVERRUN_ERROR, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.MemoryLockedError);
		assertEquals(LockResult.Result.MEMORY_LOCKED_ERROR, lockRes);

		lockRes = NurErrorMap.rfcErrorToLockResult(RFCError.ZeroKillPasswordError);
		assertEquals(LockResult.Result.NON_SPECIFIC_TAG_ERROR, lockRes);
	}

	@Test
	public void rfcErrorToReadResult() {
		ReadResult.Result readRes;

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.NonSpecificTagError);
		assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.NoResponseFromTagError);
		assertEquals(ReadResult.Result.NO_RESPONSE_FROM_TAG, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.NonSpecificReaderError);
		assertEquals(ReadResult.Result.NON_SPECIFIC_READER_ERROR, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.MemoryOverrunError);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.MemoryLockedError);
		assertEquals(ReadResult.Result.MEMORY_LOCKED_ERROR, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.IncorrectPasswordError);
		assertEquals(ReadResult.Result.INCORRECT_PASSWORD_ERROR, readRes);

		readRes = NurErrorMap.rfcErrorToReadResult(RFCError.ZeroKillPasswordError); 
		assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, readRes);
				
	}

	@Test
	public void rfcErrorToWriteResult() {
		WriteResult.Result writeRes;

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.MemoryOverrunError);
		assertEquals(WriteResult.Result.MEMORY_OVERRUN_ERROR, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.MemoryLockedError);
		assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.InsufficientPowerError);
		assertEquals(WriteResult.Result.INSUFFICIENT_POWER, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.NonSpecificTagError);
		assertEquals(WriteResult.Result.NON_SPECIFIC_TAG_ERROR, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.NoResponseFromTagError);
		assertEquals(WriteResult.Result.NO_RESPONSE_FROM_TAG, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.NonSpecificReaderError);
		assertEquals(WriteResult.Result.NON_SPECIFIC_READER_ERROR, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.IncorrectPasswordError);
		assertEquals(WriteResult.Result.INCORRECT_PASSWORD_ERROR, writeRes);

		writeRes = NurErrorMap.rfcErrorToWriteResult(RFCError.ZeroKillPasswordError);
		assertEquals(WriteResult.Result.NON_SPECIFIC_TAG_ERROR, writeRes);
	}
	
	@Test
	public void rfcErrorToCustomResult() {
		CustomResult.Result custRes;

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.MemoryOverrunError);
		assertEquals(CustomResult.Result.MEMORY_OVERRUN_ERROR, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.MemoryLockedError);
		assertEquals(CustomResult.Result.MEMORY_LOCKED_ERROR, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.InsufficientPowerError);
		assertEquals(CustomResult.Result.INSUFFICIENT_POWER, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.NonSpecificTagError);
		assertEquals(CustomResult.Result.NON_SPECIFIC_TAG_ERROR, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.NoResponseFromTagError);
		assertEquals(CustomResult.Result.NO_RESPONSE_FROM_TAG, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.NonSpecificReaderError);
		assertEquals(CustomResult.Result.NON_SPECIFIC_READER_ERROR, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.IncorrectPasswordError);
		assertEquals(CustomResult.Result.INCORRECT_PASSWORD_ERROR, custRes);

		custRes = NurErrorMap.rfcErrorToCustomResult(RFCError.ZeroKillPasswordError);
		assertEquals(CustomResult.Result.NON_SPECIFIC_TAG_ERROR, custRes);
	}

	@Test
	public void nurApiExceptionToKillResult() {
		KillResult.Result killRes;

		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_WRITE));
		assertEquals(KillResult.Result.INCORRECT_PASSWORD_ERROR, killRes);
		
		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_TAG_RESP));
		assertEquals(KillResult.Result.INCORRECT_PASSWORD_ERROR, killRes);
		
		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN));
		assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, killRes);

		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER));
		assertEquals(KillResult.Result.INSUFFICIENT_POWER, killRes);

		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED));
		assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, killRes);

		killRes = NurErrorMap.nurApiExceptionToKillResult(new NurApiException(NurApiErrors.G2_TAG_NON_SPECIFIC));
		assertEquals(KillResult.Result.NON_SPECIFIC_TAG_ERROR, killRes);
	}

	@Test
	public void nurApiExceptionToLockResult() {
		LockResult.Result lockRes;

		lockRes = NurErrorMap.nurApiExceptionToLockResult(new NurApiException(NurApiErrors.G2_WRITE));
		assertEquals(LockResult.Result.INCORRECT_PASSWORD_ERROR, lockRes);
		
		lockRes = NurErrorMap.nurApiExceptionToLockResult(new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN));
		assertEquals(LockResult.Result.MEMORY_OVERRUN_ERROR, lockRes);

		lockRes = NurErrorMap.nurApiExceptionToLockResult(new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER));
		assertEquals(LockResult.Result.INSUFFICIENT_POWER, lockRes);

		lockRes = NurErrorMap.nurApiExceptionToLockResult(new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED));
		assertEquals(LockResult.Result.MEMORY_LOCKED_ERROR, lockRes);

		lockRes = NurErrorMap.nurApiExceptionToLockResult(new NurApiException(NurApiErrors.G2_TAG_NON_SPECIFIC));
		assertEquals(LockResult.Result.NON_SPECIFIC_TAG_ERROR, lockRes);
	}

	@Test
	public void nurApiExceptionToReadResult() {
		ReadResult.Result readRes;

		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_ACCESS), true);
		assertEquals(ReadResult.Result.INCORRECT_PASSWORD_ERROR, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_ACCESS), false);
		assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN), false);
		assertEquals(ReadResult.Result.MEMORY_OVERRUN_ERROR, readRes);

		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER), false);
		assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, readRes);

		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED), false);
		assertEquals(ReadResult.Result.MEMORY_LOCKED_ERROR, readRes);

		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_TAG_NON_SPECIFIC), false);
		assertEquals(ReadResult.Result.NON_SPECIFIC_TAG_ERROR, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.TRANSPORT), false);
		assertEquals(ReadResult.Result.NON_SPECIFIC_READER_ERROR, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_SELECT), false);
		assertEquals(ReadResult.Result.INCORRECT_PASSWORD_ERROR, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.G2_TAG_RESP), false);
		assertEquals(ReadResult.Result.NO_RESPONSE_FROM_TAG, readRes);
		
		readRes = NurErrorMap.nurApiExceptionToReadResult(new NurApiException(NurApiErrors.NO_TAG), false);
		assertEquals(ReadResult.Result.NON_SPECIFIC_READER_ERROR, readRes);
	}

	@Test
	public void nurApiExceptionToWriteResult() {
		WriteResult.Result writeRes;

		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_ACCESS), true);
		assertEquals(WriteResult.Result.INCORRECT_PASSWORD_ERROR, writeRes);
		
		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_ACCESS), false);
		assertEquals(WriteResult.Result.NON_SPECIFIC_TAG_ERROR, writeRes);
		
		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN), false);
		assertEquals(WriteResult.Result.MEMORY_OVERRUN_ERROR, writeRes);

		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER), false);
		assertEquals(WriteResult.Result.INSUFFICIENT_POWER, writeRes);

		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED), false);
		assertEquals(WriteResult.Result.MEMORY_LOCKED_ERROR, writeRes);

		writeRes = NurErrorMap.nurApiExceptionToWriteResult(new NurApiException(NurApiErrors.G2_TAG_NON_SPECIFIC), false);
		assertEquals(WriteResult.Result.NON_SPECIFIC_TAG_ERROR, writeRes);
	}
	
	@Test
	public void nurApiExceptionToCustomResult() {
		CustomResult.Result custRes;

		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_ACCESS), true);
		assertEquals(CustomResult.Result.INCORRECT_PASSWORD_ERROR, custRes);
		
		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_ACCESS), false);
		assertEquals(CustomResult.Result.NON_SPECIFIC_TAG_ERROR, custRes);
		
		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_TAG_MEM_OVERRUN), false);
		assertEquals(CustomResult.Result.MEMORY_OVERRUN_ERROR, custRes);

		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_TAG_INSUF_POWER), false);
		assertEquals(CustomResult.Result.INSUFFICIENT_POWER, custRes);

		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_TAG_MEM_LOCKED), false);
		assertEquals(CustomResult.Result.MEMORY_LOCKED_ERROR, custRes);

		custRes = NurErrorMap.nurApiExceptionToCustomResult(new NurApiException(NurApiErrors.G2_TAG_NON_SPECIFIC), false);
		assertEquals(CustomResult.Result.NON_SPECIFIC_TAG_ERROR, custRes);
	}
}
