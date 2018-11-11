package org.junit.tests.experimental.interceptor;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.experimental.interceptor.Interceptor;
import org.junit.experimental.interceptor.TestName;

public class NameInterceptorTest {
	@Interceptor public TestName name = new TestName();
	
	@Test public void testA() {
		assertEquals("testA", name.getMethodName());
	}
	
	@Test public void testB() {
		assertEquals("testB", name.getMethodName());
	}
}
