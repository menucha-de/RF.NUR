package havis.device.rf.nur.firmware;

import static org.junit.Assert.*;

import org.junit.Test;

import havis.device.rf.exception.ParameterException;

public class ExecutionExceptionTest {

	@Test
	public void testExecutionException() {
		try { throw new ExecutionException(); } 
		catch (ExecutionException e) { }
		
		try { throw new ExecutionException("error message"); } 
		catch (ExecutionException e) { 
			assertEquals("error message", e.getMessage());
		}
		
		try { throw new ExecutionException(new ParameterException()); } 
		catch (ExecutionException e) { 			
			assertEquals(ParameterException.class, e.getCause().getClass());
		}
		
		try { throw new ExecutionException("error message", new ParameterException()); } 
		catch (ExecutionException e) { 
			assertEquals("error message", e.getMessage());
			assertEquals(ParameterException.class, e.getCause().getClass());
		}
		
		try { throw new ExecutionException("error message", new ParameterException(), true, true); } 
		catch (ExecutionException e) { 
			assertEquals("error message", e.getMessage());
			assertEquals(ParameterException.class, e.getCause().getClass());			
		}
	}
}
