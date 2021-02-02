package havis.device.rf.nur.firmware;

public class ExecutionException extends Exception {
	private static final long serialVersionUID = 1568756602286771822L;

	public ExecutionException() {
		super();
	}

	public ExecutionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	public ExecutionException(String message) {
		super(message);
	}

	public ExecutionException(Throwable cause) {
		super(cause);
	}
}
