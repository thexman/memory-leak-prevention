package com.a9ski.catalina;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;

/**
 * Copyright (C) 2014 Kiril Arabadzhiyski
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
public class MemoryLeakPreventionListener implements LifecycleListener {
	
	private boolean keepAlivePreventionDisabled = false;
	private boolean cleanThreadLocalsDisabled = false;
	
	private boolean cleanThreadLocalsInNewThread = false;
	private int cleanThreadLocalsWaitTime = 0;
	private String lifecycleEventType = "destroy";
	
	private List<Thread> cleanThreads = new CopyOnWriteArrayList<Thread>();
	
	public String getLifecycleEventType() {
		return lifecycleEventType;
	}
	
	public void setLifecycleEventType(String lifecycleEventType) {
		this.lifecycleEventType = lifecycleEventType;
	}
	
	public boolean isCleanThreadLocalsInNewThread() {
		return cleanThreadLocalsInNewThread;
	}
	
	public void setCleanThreadLocalsInNewThread(boolean cleanThreadLocalsInNewThread) {
		this.cleanThreadLocalsInNewThread = cleanThreadLocalsInNewThread;
	}
	
	public int getCleanThreadLocalsWaitTime() {
		return cleanThreadLocalsWaitTime;
	}
	
	public void setCleanThreadLocalsWaitTime(int cleanThreadLocalsWaitTime) {
		this.cleanThreadLocalsWaitTime = cleanThreadLocalsWaitTime;
	}
	
	public boolean isKeepAlivePreventionDisabled() {
		return keepAlivePreventionDisabled; 
	}
	
	public void setKeepAlivePreventionDisabled(boolean keepAlivePreventionDisabled) {
		this.keepAlivePreventionDisabled = keepAlivePreventionDisabled;
	}
	
	
	public boolean isCleanThreadLocalsDisabled() {
		return cleanThreadLocalsDisabled;
	}
	
	public void setCleanThreadLocalsDisabled(boolean cleanThreadLocalsDisabled) {
		this.cleanThreadLocalsDisabled = cleanThreadLocalsDisabled;
	}
	
	public List<Thread> getCleanThreads() {
		return cleanThreads;
	}

	
	@Override
	public void lifecycleEvent(final LifecycleEvent e) {
		if (lifecycleEventType.equalsIgnoreCase(e.getType())) {
			if (!isKeepAlivePreventionDisabled()) {
				stopHttpClientKeepAliveThread();
			}

			if (!isCleanThreadLocalsDisabled()) {
				cleanThreadLocals();
			}
		}
		
	}

	

	private void cleanThreadLocals() {		
		Class<?> threadLocalMapClass = null;
		try {
			threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap", false, getClass().getClassLoader());				
		} catch (final ClassNotFoundException ex) {
			try {
				threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap", false, ClassLoader.getSystemClassLoader());
			} catch (final ClassNotFoundException ex2) {
				ex2.printStackTrace();
			}
		}
		if (threadLocalMapClass != null) {
			cleanThreadLocalsForLoader(threadLocalMapClass, getClass().getClassLoader());
		}
	}

	private void stopHttpClientKeepAliveThread() {
		try {
			try {
				stopHttpClientKeepAliveThread(getClass().getClassLoader());
			} catch (final ClassNotFoundException ex) {
				stopHttpClientKeepAliveThread(ClassLoader.getSystemClassLoader());
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Stops Keep Alive thread started by HttpClient
	 * See JBOSS bug report {@link https://issues.jboss.org/browse/AS7-3732}
	 * 
	 * @param loader
	 * 
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("deprecation")
	private void stopHttpClientKeepAliveThread(ClassLoader loader) throws ClassNotFoundException {		
		try {			
			final Class<?> httpClientClass = Class.forName("sun.net.www.http.HttpClient", false, loader);			
			final Field kacField = httpClientClass.getDeclaredField("kac");
			kacField.setAccessible(true);
			final Object kac = kacField.get(null);
			if (kac != null) {
				final Class<?> keepAliveCacheClass = Class.forName("sun.net.www.http.KeepAliveCache", false, loader);
				final Field f = keepAliveCacheClass.getDeclaredField("keepAliveTimer");
				f.setAccessible(true);
				final Thread t = (Thread)f.get(kac);
				f.set(kac, null);
				if (t != null) {
					t.interrupt();
					try {
						Thread.sleep(300);
					} finally {
						if (t.isAlive()) {
							t.stop();
						}
					}
				}					
			}
		} catch (final InterruptedException ex) {
			ex.printStackTrace();
		} catch (final NoSuchFieldException ex) {			
			ex.printStackTrace();
		} catch (final IllegalAccessException ex) {			
			ex.printStackTrace();
		}		
	}
	
	private ThreadGroup getRootThreadGroup() {	    
	    ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
	    ThreadGroup parentThreadGroup;
	    while ( (parentThreadGroup = threadGroup.getParent( )) != null ) {
	        threadGroup = parentThreadGroup;
	    }
	    return threadGroup;
	}
	
	private Thread[] getAllThreads() {
		final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();	    		
		final Thread[] threads = new Thread[threadBean.getThreadCount() * 2];
		//Thread.enumerate(threads);
		final ThreadGroup group = getRootThreadGroup();
		group.enumerate(threads, true);
		return threads;
	}
	
	private void cleanThreadLocalsForLoader(final Class<?> threadLocalMapClass, final ClassLoader loaderToCheck) {				
		try {
			final int waitTime = getCleanThreadLocalsWaitTime();
			
			final Runnable r = createThreadLocalsCleaner(threadLocalMapClass, loaderToCheck, waitTime);
			
			if (isCleanThreadLocalsInNewThread()) {
				final Thread executionThread = new Thread(r, "cleanThreadLocalsForLoader");
				executionThread.setDaemon(true);
				executionThread.start();
				cleanThreads.add(executionThread);
			} else {
				r.run();
			}			
		} catch (final NoSuchFieldException ex) {
			ex.printStackTrace();
		} catch (final IllegalArgumentException ex) {			
			ex.printStackTrace();
		}
	}

	private Runnable createThreadLocalsCleaner(
			final Class<?> threadLocalMapClass,
			final ClassLoader loaderToCheck, final int waitTime)
			throws NoSuchFieldException {
		final Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
		threadLocalsField.setAccessible(true);
		
		final Field tableField = threadLocalMapClass.getDeclaredField("table");
		tableField.setAccessible(true);
		
		final Thread[] threads = getAllThreads();
		final Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(waitTime); // sleep for 3 seconds, so all uniitializations are done.
					final Thread currThread = Thread.currentThread();
					for (int i = 0; i < threads.length; i++) {
						if (threads[i] != null && threads[i] != currThread) {
							cleanThreadLocalsForLoader(threads[i], threadLocalsField, tableField, loaderToCheck);
						}
					}
				} catch (final Exception ex) {
					ex.printStackTrace();
				}
			}				
		};
		return r;
	}

	

	private void cleanThreadLocalsForLoader(Thread thread, final Field threadLocalsField, final Field tableField, final ClassLoader loaderToCheck) throws IllegalArgumentException, IllegalAccessException, SecurityException, NoSuchFieldException {		
		final Object fieldLocal = threadLocalsField.get(thread);
		if (fieldLocal == null) {
			return;
		}
		final Object table = tableField.get(fieldLocal);

		final int threadLocalCount = Array.getLength(table);
		
		for (int i = 0; i < threadLocalCount; i++) {
			final Object entry = Array.get(table, i);
			if (entry != null) {
				final Field valueField = entry.getClass().getDeclaredField("value");
				valueField.setAccessible(true);
				final Object value = valueField.get(entry);
				if (value != null && value.getClass() != null && loaderToCheck.equals(value.getClass().getClassLoader())) {											
					valueField.set(entry, null);					
				}
			}
		}
	}

}
