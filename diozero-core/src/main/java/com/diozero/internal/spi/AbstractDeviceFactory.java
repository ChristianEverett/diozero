package com.diozero.internal.spi;

/*
 * #%L
 * Device I/O Zero - Core
 * %%
 * Copyright (C) 2016 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.pmw.tinylog.Logger;

import com.diozero.internal.DeviceStates;

public abstract class AbstractDeviceFactory implements DeviceFactoryInterface {
	private String pinPrefix;
	protected DeviceStates deviceStates;
	protected boolean shutdown;
	
	public AbstractDeviceFactory(String pinPrefix) {
		this.pinPrefix = pinPrefix;
		deviceStates = new DeviceStates();
	}
	
	@Override
	public final String createPinKey(int gpio) {
		return pinPrefix + gpio;
	}
	
	@Override
	public void shutdown() {
		Logger.debug("shutdown()");
		deviceStates.closeAll();
		shutdown = true;
	}
	
	@Override
	public final boolean isShutdown() {
		return shutdown;
	}
	
	@Override
	public final void deviceOpened(DeviceInterface device) {
		deviceStates.opened(device);
	}
	
	@Override
	public final void deviceClosed(DeviceInterface device) {
		deviceStates.closed(device);
	}
	
	@Override
	public final boolean isDeviceOpened(String key) {
		return deviceStates.isOpened(key);
	}

	@SuppressWarnings("unchecked")
	public final <T extends DeviceInterface> T getDevice(String key, Class<T> clz) {
		return (T) deviceStates.getDevice(key);
	}
}
