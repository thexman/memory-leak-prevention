package com.a9ski.catalina;

import static org.junit.Assert.*;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.junit.Test;

public class MemoryLeakPreventionListenerTest {

	final ThreadLocal<Boolean> threadLocalBoolean = new ThreadLocal<Boolean>();
	final ThreadLocal<MemoryLeakPreventionListenerTest> threadLocalLeak = new ThreadLocal<MemoryLeakPreventionListenerTest>();
	
	final static Lifecycle lifecycle;
	
	@Test
	public void testCleanThreadLocalsWithNewThread() throws InterruptedException {
		final MemoryLeakPreventionListener listener = new MemoryLeakPreventionListener();
		listener.setCleanThreadLocalsInNewThread(true);
		listener.setCleanThreadLocalsDisabled(false);
		listener.setKeepAlivePreventionDisabled(true);
		listener.setLifecycleEventType("test");
		
		threadLocalBoolean.set(Boolean.TRUE);
		threadLocalLeak.set(new MemoryLeakPreventionListenerTest());
		
		assertEquals(0, listener.getCleanThreads().size());		
		
		listener.lifecycleEvent(new LifecycleEvent(lifecycle, "test"));
		assertEquals(1, listener.getCleanThreads().size());
		
		System.out.println("Thread:" + Thread.currentThread());
		System.out.println("Classloader:" + getClass().getClassLoader());
		
		int counter = 0;
		while(listener.getCleanThreads().get(0).isAlive() && counter < 100) {
			Thread.sleep(100);
			counter++;
		}
		
		assertFalse(listener.getCleanThreads().get(0).isAlive());
		
		assertTrue(threadLocalBoolean.get());
		assertNull(threadLocalLeak.get());
	}
	
	
	
	@Test
	public void testCleanThreadLocals() throws InterruptedException {
		final MemoryLeakPreventionListener listener = new MemoryLeakPreventionListener();
		listener.setCleanThreadLocalsInNewThread(false);
		listener.setCleanThreadLocalsDisabled(false);
		listener.setKeepAlivePreventionDisabled(true);
		listener.setLifecycleEventType("test");
		
		threadLocalBoolean.set(Boolean.TRUE);
		
		assertEquals(0, listener.getCleanThreads().size());		
		
		listener.lifecycleEvent(new LifecycleEvent(lifecycle, "test"));
		assertEquals(0, listener.getCleanThreads().size());				
	}

	static {
		lifecycle = new Lifecycle() {
			
			@Override
			public void stop() throws LifecycleException {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void start() throws LifecycleException {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void removeLifecycleListener(LifecycleListener listener) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void init() throws LifecycleException {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public LifecycleState getState() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public LifecycleListener[] findLifecycleListeners() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public void destroy() throws LifecycleException {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void addLifecycleListener(LifecycleListener listener) {
				// TODO Auto-generated method stub
				
			}
		};
	}

}
