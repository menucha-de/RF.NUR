package havis.device.rf.nur;

import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.CustomResult.Result;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.WriteResult;

import com.nordicid.nurapi.NurApiErrors;
import com.nordicid.nurapi.NurApiException;

/**
 * This class maps NUR API error codes to predefined RFC errors and allow to
 * transform those to operation results. The internal error map is defined as
 * follows.
 * 
 * <p>
 * <b><u>NonSpecificReaderError</u></b>
 * </p>
 * <ul>
 * <li>NurApiErrors.INVALID_COMMAND (Invalid command sent to module.)</li>
 * <li>NurApiErrors.INVALID_LENGTH (Invalid packet length sent to module.)</li>
 * <li>NurApiErrors.PARAMETER_OUT_OF_RANGE (Command parametr(s) out of range.)</li>
 * <li>NurApiErrors.RECEIVE_TIMEOUT (Data receive timeout.)</li>
 * <li>NurApiErrors.INVALID_PARAMETER (Invalid command parameter(s); Invalid
 * function parameter(s).)</li>
 * <li>NurApiErrors.PROGRAM_FAILED (Programming failure.)</li>
 * <li>NurApiErrors.PARAMETER_MISMATCH (Parameter mismatch.)</li>
 * <li>NurApiErrors.PAGE_PROGRAM (Page programming failure.)</li>
 * <li>NurApiErrors.CRC_CHECK (Memory check failed.)</li>
 * <li>NurApiErrors.CRC_MISMATCH (CRC mismatch in parameter.)</li>
 * <li>NurApiErrors.NOT_READY (Device not ready or region that is being
 * programmed is not unlocked.)</li>
 * <li>NurApiErrors.APP_NOT_PRESENT (Module application not present.)</li>
 * <li>NurApiErrors.GENERAL (Generic, non-interpreted / unexpected error.)</li>
 * <li>NurApiErrors.READER_HW (HW error.)</li>
 * <li>NurApiErrors.INVALID_HANDLE (Invalid handle passed to function.)</li>
 * <li>NurApiErrors.TRANSPORT (Transport error.)</li>
 * <li>NurApiErrors.TR_NOT_CONNECTED (Transport not connected.)</li>
 * <li>NurApiErrors.TR_TIMEOU: (Transport timeout.)</li>
 * <li>NurApiErrors.BUFFER_TOO_SMALL (Buffer too small.)</li>
 * <li>NurApiErrors.NOT_SUPPORTED (Functionality not supported.)</li>
 * <li>NurApiErrors.NO_PAYLOAD (Packet contains no payload.)</li>
 * <li>NurApiErrors.INVALID_PACKET (Packet is invalid.)</li>
 * <li>NurApiErrors.PACKET_TOO_LONG (Packet too long.)</li>
 * <li>NurApiErrors.PACKET_CS_ERROR (Packet Checksum failure.)</li>
 * <li>NurApiErrors.NOT_WORD_BOUNDARY (Data not in WORD boundary.)</li>
 * <li>NurApiErrors.FILE_NOT_FOUND (File not found.)</li>
 * <li>NurApiErrors.FILE_INVALID (File error; not in NUR format.)</li>
 * <li>NurApiErrors.MCU_ARCH (NUR file and module's MCU architecture mismatch.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>NonSpecificTagError</u></b>
 * </p>
 * <ul>
 * <li>NurApiErrors.G2_SELECT (G2 select error.)</li>
 * <li>NurApiErrors.MISSING_SELDATA (G2 select data missing.)</li>
 * <li>NurApiErrors.G2_READ (G2 Read error, unspecified.)</li>
 * <li>NurApiErrors.G2_RD_PART (G2 Partially successful read.)</li>
 * <li>NurApiErrors.G2_WRITE (G2 Write error, unspecified.)</li>
 * <li>NurApiErrors.G2_WR_PART (G2 Partially successful write.)</li>
 * <li>NurApiErrors.G2_SPECIAL (Special error; Some additional debug data is
 * returned with this error.)</li>
 * <li>NurApiErrors.G2_TAG_NON_SPECIFIC (The tag does not support error-specific
 * codes.)</li>
 * </ul>
 * 
 * 
 * <p>
 * <b><u>IncorrectPasswordError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.G2_ACCESS (G2 access error.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>MemoryOverrunError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.G2_TAG_MEM_OVERRUN (The specified memory location does not
 * exists or the EPC length field is not supported by the tag.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>MemoryLockedError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.G2_TAG_MEM_LOCKED (The specified memory location is locked
 * and/or permalocked and is either not writeable or not readable.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>InsufficientPowerError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.G2_TAG_INSUF_POWER (The tag has insufficient power to
 * perform the memory-write operation.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>NoResponseFromTagError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.G2_TAG_RESP (G2 Tag read responded w/ error.)</li>
 * </ul>
 * 
 * <p>
 * <b><u>NonSpecificReaderError
 * </p>
 * </b></u>
 * <ul>
 * <li>NurApiErrors.NO_TAG (No tag(s) found.)</li>
 * <li>NurApiErrors.RESP_AIR (Air error (not used.)</li>
 * <li>NurApiErrors.RESERVED1 (Reserved error code for later use.)</li>
 * </ul>
 * 
 */

class NurErrorMap {

	/**
	 * Defines a set of expected RFC errors.
	 * 
	 */
	protected enum RFCError {
		NonSpecificTagError, 
		NonSpecificReaderError, 
		NoResponseFromTagError, 
		MemoryOverrunError, 
		MemoryLockedError, 
		IncorrectPasswordError, 
		InsufficientPowerError, 
		ZeroKillPasswordError
	}

	/**
	 * Maps a given {@link RFCError} instance to a {@link ReadResult}.
	 * 
	 * <ul>
	 * <li>NonSpecificTagError &rarr; ReadResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * <li>NonSpecificReaderError &rarr; ReadResult.Result.NO_RESPONSE_FROM_TAG</li>
	 * <li>NoResponseFromTagError &rarr;
	 * ReadResult.Result.NON_SPECIFIC_READER_ERROR</li>
	 * <li>MemoryOverrunError &rarr; ReadResult.Result.MEMORY_OVERRUN_ERROR</li>
	 * <li>MemoryLockedError &rarr; ReadResult.Result.MEMORY_LOCKED_ERROR</li>
	 * <li>IncorrectPasswordError &rarr;
	 * ReadResult.Result.INCORRECT_PASSWORD_ERROR</li>
	 * <li>Default &rarr; IncorrectPasswordError</li>
	 * </ul>
	 * 
	 * @param rfcErr
	 *            an instance of {@link RFCError}
	 * @return an instance of {@link ReadResult}
	 */
	protected static ReadResult.Result rfcErrorToReadResult(RFCError rfcErr) {
		switch (rfcErr) {
		case NonSpecificTagError:
			return ReadResult.Result.NON_SPECIFIC_TAG_ERROR;
		case NoResponseFromTagError:
			return ReadResult.Result.NO_RESPONSE_FROM_TAG;
		case NonSpecificReaderError:
			return ReadResult.Result.NON_SPECIFIC_READER_ERROR;
		case MemoryOverrunError:
			return ReadResult.Result.MEMORY_OVERRUN_ERROR;
		case MemoryLockedError:
			return ReadResult.Result.MEMORY_LOCKED_ERROR;
		case IncorrectPasswordError:
			return ReadResult.Result.INCORRECT_PASSWORD_ERROR;
		default:
			return ReadResult.Result.NON_SPECIFIC_TAG_ERROR;
		}
	}

	/**
	 * Maps the error code of an {@link NurApiException} to a {@link ReadResult}
	 * 
	 * @param e
	 *            a {@link NurApiException} instance
	 * @return a {@link WriteResult} instance
	 */
	protected static ReadResult.Result nurApiExceptionToReadResult(NurApiException e, boolean passwordGiven) {
		switch (e.error) {
			case NurApiErrors.G2_ACCESS:
				return passwordGiven ? 
					rfcErrorToReadResult(RFCError.IncorrectPasswordError) : 
					rfcErrorToReadResult(RFCError.NonSpecificTagError);
			default:
				return rfcErrorToReadResult(nurApiErrorToRFCError(e.error));
		}		
	}

	/**
	 * Maps a given {@link RFCError} instance to a {@link WriteResult}.
	 * 
	 * <ul>
	 * <li>MemoryOverrunError &rarr; WriteResult.Result.MEMORY_OVERRUN_ERROR</li>
	 * <li>MemoryLockedError &rarr; WriteResult.Result.MEMORY_LOCKED_ERROR</li>
	 * <li>InsufficientPowerError &rarr; WriteResult.Result.INSUFFICIENT_POWER</li>
	 * <li>NonSpecificTagError &rarr; WriteResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * <li>NoResponseFromTagError &rarr; WriteResult.Result.NO_RESPONSE_FROM_TAG
	 * </li>
	 * <li>NonSpecificReaderError &rarr;
	 * WriteResult.Result.NON_SPECIFIC_READER_ERROR</li>
	 * <li>IncorrectPasswordError &rarr;
	 * WriteResult.Result.INCORRECT_PASSWORD_ERROR</li>
	 * <li>Default &rarr; WriteResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * </ul>
	 * 
	 * @param rfcErr
	 *            an {@link RFCError} instance
	 * @return an instance of {@link WriteResult}
	 */
	protected static WriteResult.Result rfcErrorToWriteResult(RFCError rfcErr) {
		switch (rfcErr) {
		case MemoryOverrunError:
			return WriteResult.Result.MEMORY_OVERRUN_ERROR;
		case MemoryLockedError:
			return WriteResult.Result.MEMORY_LOCKED_ERROR;
		case InsufficientPowerError:
			return WriteResult.Result.INSUFFICIENT_POWER;
		case NonSpecificTagError:
			return WriteResult.Result.NON_SPECIFIC_TAG_ERROR;
		case NoResponseFromTagError:
			return WriteResult.Result.NO_RESPONSE_FROM_TAG;
		case NonSpecificReaderError:
			return WriteResult.Result.NON_SPECIFIC_READER_ERROR;
		case IncorrectPasswordError:
			return WriteResult.Result.INCORRECT_PASSWORD_ERROR;
		default:
			return WriteResult.Result.NON_SPECIFIC_TAG_ERROR;
		}
	}

	/**
	 * Maps the error code of an {@link NurApiException} to a
	 * {@link WriteResult}
	 * 
	 * @param e
	 *            a {@link NurApiException} instance
	 * @return a {@link WriteResult} instance
	 */
	protected static WriteResult.Result nurApiExceptionToWriteResult(NurApiException e, boolean passwordGiven) {
		switch(e.error) {
			case NurApiErrors.G2_ACCESS: 
				return passwordGiven ? 
					rfcErrorToWriteResult(RFCError.IncorrectPasswordError) : 
					rfcErrorToWriteResult(RFCError.NonSpecificTagError);
			default: return rfcErrorToWriteResult(nurApiErrorToRFCError(e.error));
		}
	}

	/**
	 * Maps a given {@link RFCError} instance to a {@link LockResult}.
	 * 
	 * <ul>
	 * <li>InsufficientPowerError &rarr; LockResult.Result.INSUFFICIENT_POWER</li>
	 * <li>NonSpecificTagError &rarr; LockResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * <li>NoResponseFromTagError &rarr; LockResult.Result.NO_RESPONSE_FROM_TAG</li>
	 * <li>NonSpecificReaderError &rarr;
	 * LockResult.Result.NON_SPECIFIC_READER_ERROR</li>
	 * <li>IncorrectPasswordError &rarr;
	 * LockResult.Result.INCORRECT_PASSWORD_ERROR</li>
	 * <li>MemoryOverrunError &rarr; LockResult.Result.MEMORY_OVERRUN_ERROR</li>
	 * <li>MemoryLockedError &rarr; LockResult.Result.MEMORY_LOCKED_ERROR</li>
	 * <li>Default &rarr; LockResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * </ul>
	 * 
	 * @param rfcErr
	 *            an {@link RFCError} instance
	 * @return an instance of {@link LockResult}
	 */
	protected static LockResult.Result rfcErrorToLockResult(RFCError rfcErr) {
		switch (rfcErr) {
		case InsufficientPowerError:
			return LockResult.Result.INSUFFICIENT_POWER;
		case NonSpecificTagError:
			return LockResult.Result.NON_SPECIFIC_TAG_ERROR;
		case NoResponseFromTagError:
			return LockResult.Result.NO_RESPONSE_FROM_TAG;
		case NonSpecificReaderError:
			return LockResult.Result.NON_SPECIFIC_READER_ERROR;
		case IncorrectPasswordError:
			return LockResult.Result.INCORRECT_PASSWORD_ERROR;
		case MemoryOverrunError:
			return LockResult.Result.MEMORY_OVERRUN_ERROR;
		case MemoryLockedError:
			return LockResult.Result.MEMORY_LOCKED_ERROR;
		default:
			return LockResult.Result.NON_SPECIFIC_TAG_ERROR;
		}
	}

	/**
	 * Maps the error code of an {@link NurApiException} to a {@link LockResult}
	 * 
	 * @param e
	 *            a {@link NurApiException} instance
	 * @return a {@link LockResult} instance
	 */
	protected static LockResult.Result nurApiExceptionToLockResult(NurApiException e) {		
		switch (e.error) {
			case NurApiErrors.G2_WRITE: 
				return rfcErrorToLockResult(RFCError.IncorrectPasswordError);
			default:
				return rfcErrorToLockResult(nurApiErrorToRFCError(e.error));
		}		
		
	}
	
	/**
	 * 
	 * @param rfcErr
	 * @return
	 */
	protected static Result rfcErrorToCustomResult(RFCError rfcErr) {
		switch (rfcErr) {
			case MemoryOverrunError:
				return CustomResult.Result.MEMORY_OVERRUN_ERROR;
			case MemoryLockedError:
				return CustomResult.Result.MEMORY_LOCKED_ERROR;
			case InsufficientPowerError:
				return CustomResult.Result.INSUFFICIENT_POWER;
			case NonSpecificTagError:
				return CustomResult.Result.NON_SPECIFIC_TAG_ERROR;
			case NoResponseFromTagError:
				return CustomResult.Result.NO_RESPONSE_FROM_TAG;
			case NonSpecificReaderError:
				return CustomResult.Result.NON_SPECIFIC_READER_ERROR;
			case IncorrectPasswordError:
				return CustomResult.Result.INCORRECT_PASSWORD_ERROR;
			default:
				return CustomResult.Result.NON_SPECIFIC_TAG_ERROR;
		}
	}
	
	/**
	 * 
	 * @param e
	 * @return
	 */
	protected static CustomResult.Result nurApiExceptionToCustomResult(NurApiException e, boolean passwordGiven) {
		switch (e.error) {
		case NurApiErrors.G2_ACCESS: 
				return passwordGiven ? 
					rfcErrorToCustomResult(RFCError.IncorrectPasswordError) : 
						rfcErrorToCustomResult(RFCError.NonSpecificTagError);
			default:
				return rfcErrorToCustomResult(nurApiErrorToRFCError(e.error));
		}
	}
	
	/**
	 * Maps a given {@link RFCError} instance to a {@link KillResult}.
	 * 
	 * <ul>
	 * <li>ZeroKillPasswordError &rarr;
	 * KillResult.Result.ZERO_KILL_PASSWORD_ERROR</li>
	 * <li>InsufficientPowerError &rarr; KillResult.Result.INSUFFICIENT_POWER</li>
	 * <li>NonSpecificTagError &rarr; KillResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * <li>NoResponseFromTagError &rarr; KillResult.Result.NO_RESPONSE_FROM_TAG</li>
	 * <li>NonSpecificReaderError &rarr;
	 * KillResult.Result.NON_SPECIFIC_READER_ERROR</li>
	 * <li>IncorrectPasswordError &rarr;
	 * KillResult.Result.INCORRECT_PASSWORD_ERROR</li>
	 * <li>Default &rarr; KillResult.Result.NON_SPECIFIC_TAG_ERROR</li>
	 * </ul>
	 * 
	 * @param rfcErr
	 *            an {@link RFCError} instance
	 * @return an instance of {@link LockResult}
	 */
	protected static KillResult.Result rfcErrorToKillResult(RFCError rfcErr) {
		switch (rfcErr) {
		case ZeroKillPasswordError:
			return KillResult.Result.ZERO_KILL_PASSWORD_ERROR;
		case InsufficientPowerError:
			return KillResult.Result.INSUFFICIENT_POWER;
		case NonSpecificTagError:
			return KillResult.Result.NON_SPECIFIC_TAG_ERROR;
		case NoResponseFromTagError:
			return KillResult.Result.NO_RESPONSE_FROM_TAG;
		case NonSpecificReaderError:
			return KillResult.Result.NON_SPECIFIC_READER_ERROR;
		case IncorrectPasswordError:
			return KillResult.Result.INCORRECT_PASSWORD_ERROR;
		default:
			return KillResult.Result.NON_SPECIFIC_TAG_ERROR;
		}
	}

	/**
	 * Maps the error code of an {@link NurApiException} to a {@link KillResult}
	 * 
	 * @param e
	 *            a {@link NurApiException} instance
	 * @return a {@link KillResult} instance
	 */
	protected static KillResult.Result nurApiExceptionToKillResult(
			NurApiException e) {
		switch (e.error) {
			case NurApiErrors.G2_WRITE: 
				return rfcErrorToKillResult(RFCError.IncorrectPasswordError);
				
			case NurApiErrors.G2_TAG_RESP: 
				return rfcErrorToKillResult(RFCError.IncorrectPasswordError);
				
			default: 
				return rfcErrorToKillResult(nurApiErrorToRFCError(e.error)); 
		}
	}

	private static RFCError nurApiErrorToRFCError(int nurApiError) {
		switch (nurApiError) {
		/* NonSpecificReaderError */
			case NurApiErrors.INVALID_COMMAND: // Invalid command sent to module.
			case NurApiErrors.INVALID_LENGTH: // Invalid packet length sent to module.
			case NurApiErrors.PARAMETER_OUT_OF_RANGE: // Command parameter(s) out of range.
			case NurApiErrors.RECEIVE_TIMEOUT: // Data receive timeout.
			case NurApiErrors.INVALID_PARAMETER: // Invalid command parameter(s); Invalid function parameter(s).
			case NurApiErrors.PROGRAM_FAILED: // Programming failure.
			case NurApiErrors.PARAMETER_MISMATCH: // Parameter mismatch.			
			case NurApiErrors.PAGE_PROGRAM: // Page programming failure.
			case NurApiErrors.CRC_CHECK: // Memory check failed.
			case NurApiErrors.CRC_MISMATCH: // CRC mismatch in parameter.
			case NurApiErrors.NOT_READY: // Device not ready or region that is being programmed is not unlocked.
			case NurApiErrors.APP_NOT_PRESENT: // Module application not present.
			case NurApiErrors.GENERAL: // Generic, non-interpreted / unexpected error.
			case NurApiErrors.READER_HW: // HW error.
			case NurApiErrors.INVALID_HANDLE: // Invalid handle passed to function.
			case NurApiErrors.TRANSPORT: // Transport error.
			case NurApiErrors.TR_NOT_CONNECTED: // Transport not connected.
			case NurApiErrors.TR_TIMEOUT: // Transport timeout.
			case NurApiErrors.BUFFER_TOO_SMALL: // Buffer too small.
			case NurApiErrors.NOT_SUPPORTED: // Functionality not supported.
			case NurApiErrors.NO_PAYLOAD: // Packet contains no pay load.
			case NurApiErrors.INVALID_PACKET: // Packet is invalid.
			case NurApiErrors.PACKET_TOO_LONG: // Packet too long.
			case NurApiErrors.PACKET_CS_ERROR: // Packet Checksum failure.
			case NurApiErrors.NOT_WORD_BOUNDARY: // Data not in WORD boundary.
			case NurApiErrors.FILE_NOT_FOUND: // File not found.
			case NurApiErrors.FILE_INVALID: // File error; not in NUR format.
			case NurApiErrors.MCU_ARCH: // NUR file and module's MCU architecture mismatch.
			return RFCError.NonSpecificReaderError;

		/* NonSpecificTagError */
			case NurApiErrors.MISSING_SELDATA: // G2 select data missing.
			case NurApiErrors.G2_READ: // G2 Read error, unspecified.
			case NurApiErrors.G2_RD_PART: // G2 Partially successful read.
			case NurApiErrors.G2_WRITE: // G2 Write error, unspecified.
			case NurApiErrors.G2_WR_PART: // G2 Partially successful write.
			case NurApiErrors.G2_SPECIAL: // Special error; Some additional debug data is returned with this error.
			case NurApiErrors.G2_TAG_NON_SPECIFIC: // The tag does not support error-specific codes.
			return RFCError.NonSpecificTagError;

		/* IncorrectPasswordError */
			case NurApiErrors.G2_SELECT: // G2 select error.
			case NurApiErrors.G2_ACCESS: // G2 access error.
			return RFCError.IncorrectPasswordError;

		/* MemoryOverrunError */
			case NurApiErrors.G2_TAG_MEM_OVERRUN: // The specified memory location does not exists or the EPC
												  // length field is not supported by the tag.
			return RFCError.MemoryOverrunError;

		/* MemoryLockedError */
			case NurApiErrors.G2_TAG_MEM_LOCKED: // The specified memory location is locked and/or permalocked and
												 // is either not writable or not readable.
			return RFCError.MemoryLockedError;

		/* InsufficientPowerError */
			case NurApiErrors.G2_TAG_INSUF_POWER: // The tag has insufficient power to perform the memory-write operation.
			return RFCError.InsufficientPowerError;

		/* NoResponseFromTagError */
			case NurApiErrors.G2_TAG_RESP: // G2 Tag read responded w/ error.
			return RFCError.NoResponseFromTagError;

		/* default / unspecified => NonSpecificReaderError */
			case NurApiErrors.NO_TAG: // No tag(s) found.
			case NurApiErrors.RESP_AIR: // Air error (not used).
			case NurApiErrors.RESERVED1: // Reserved error code for later use.
			default: return RFCError.NonSpecificReaderError;
		}
	}

}
