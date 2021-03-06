package com.diozero.api;

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

import com.diozero.internal.DeviceFactoryHelper;
import com.diozero.internal.spi.PwmOutputDeviceFactoryInterface;
import com.diozero.internal.spi.PwmOutputDeviceInterface;
import com.diozero.util.DioZeroScheduler;
import com.diozero.util.RuntimeIOException;
import com.diozero.util.SleepUtil;

/**
 * Provide generic
 * <a href="https://en.wikipedia.org/wiki/Pulse-width_modulation">Pulse Width
 * Modulation (PWM)</a> output control. Note the following Raspberry Pi BCM GPIO
 * pins provide hardware PWM support: 12 (phys 32, wPi 26), 13 (phys 33, wPi
 * 23), 18 (phys 12, wPi 1), 19 (phys 35, wPi 24) Any other pin will revert to
 * software controlled PWM.
 */
public class PwmOutputDevice extends GpioDevice implements OutputDeviceInterface {
	public static final int INFINITE_ITERATIONS = -1;

	private PwmOutputDeviceInterface device;
	private boolean running;
	private Thread backgroundThread;

	/**
	 * @param gpio
	 *            GPIO to which the output device is connected.
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public PwmOutputDevice(int gpio) throws RuntimeIOException {
		this(gpio, 0);
	}

	/**
	 * @param gpio
	 *            GPIO to which the output device is connected.
	 * @param initialValue
	 *            Initial output value (0..1).
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public PwmOutputDevice(int gpio, float initialValue) throws RuntimeIOException {
		this(DeviceFactoryHelper.getNativeDeviceFactory(), gpio, initialValue);
	}

	/**
	 * @param pwmDeviceFactory
	 *            Device factory to use to provision this device.
	 * @param gpio
	 *            GPIO to which the output device is connected.
	 * @param initialValue
	 *            Initial output value (0..1).
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public PwmOutputDevice(PwmOutputDeviceFactoryInterface pwmDeviceFactory, int gpio, float initialValue)
			throws RuntimeIOException {
		super(gpio);
		this.device = pwmDeviceFactory.provisionPwmOutputPin(gpio, initialValue);
	}

	@Override
	public void close() {
		Logger.debug("close()");
		stopLoops();
		running = false;
		if (backgroundThread != null) {
			Logger.info("Interrupting background thread " + backgroundThread.getName());
			backgroundThread.interrupt();
		}
		Logger.info("Setting value to 0");
		try {
			device.setValue(0);
		} catch (RuntimeIOException e) {
		}
		device.close();
		Logger.debug("device closed");
	}

	protected void onOffLoop(float onTime, float offTime, int n, boolean background) throws RuntimeIOException {
		stopLoops();
		if (background) {
			DioZeroScheduler.getDaemonInstance().execute(() -> {
				backgroundThread = Thread.currentThread();
				onOffLoop(onTime, offTime, n);
				backgroundThread = null;
				Logger.info("Background on-off loop finished");
			});
		} else {
			onOffLoop(onTime, offTime, n);
		}
	}

	private void onOffLoop(float onTime, float offTime, int n) throws RuntimeIOException {
		if (n > 0) {
			running = true;
			for (int i = 0; i < n && running; i++) {
				onOff(onTime, offTime);
			}
			running = false;
		} else if (n == INFINITE_ITERATIONS) {
			running = true;
			while (running) {
				onOff(onTime, offTime);
			}
		}
	}

	protected void fadeInOutLoop(float fadeTime, int steps, int iterations, boolean background)
			throws RuntimeIOException {
		stopLoops();
		if (background) {
			DioZeroScheduler.getDaemonInstance().execute(() -> {
				backgroundThread = Thread.currentThread();
				fadeInOutLoop(fadeTime, steps, iterations);
				Logger.info("Background fade in-out loop finished");
				backgroundThread = null;
			});
		} else {
			fadeInOutLoop(fadeTime, steps, iterations);
		}
	}

	private void fadeInOutLoop(float fadeTime, int steps, int iterations) throws RuntimeIOException {
		float sleep_time = fadeTime / steps;
		float delta = 1f / steps;
		if (iterations > 0) {
			running = true;
			for (int i = 0; i < iterations && running; i++) {
				fadeInOut(sleep_time, delta);
			}
			running = false;
		} else if (iterations == INFINITE_ITERATIONS) {
			running = true;
			while (running) {
				fadeInOut(sleep_time, delta);
			}
		}
	}

	private void fadeInOut(float sleepTime, float delta) throws RuntimeIOException {
		float value = 0;
		while (value <= 1 && running) {
			setValueInternal(value);
			SleepUtil.sleepSeconds(sleepTime);
			value += delta;
		}
		value = 1;
		while (value >= 0 && running) {
			setValueInternal(value);
			SleepUtil.sleepSeconds(sleepTime);
			value -= delta;
		}
	}

	private void stopLoops() {
		running = false;
	}

	private void onOff(float onTime, float offTime) throws RuntimeIOException {
		setValueInternal(1);
		SleepUtil.sleepSeconds(onTime);
		setValueInternal(0);
		SleepUtil.sleepSeconds(offTime);
	}

	protected void setValueInternal(float value) throws RuntimeIOException {
		if (value < 0 || value > 1) {
			throw new IllegalArgumentException("Value must be 0..1, you requested " + value);
		}
		device.setValue(value);
	}

	// Exposed operations

	/**
	 * Get the current PWM output value (0..1).
	 * 
	 * @return Current PWM output value.
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public float getValue() throws RuntimeIOException {
		return device.getValue();
	}

	/**
	 * Set the PWM output value (0..1).
	 * 
	 * @param value
	 *            New PWM output value.
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	@Override
	public void setValue(float value) throws RuntimeIOException {
		stopLoops();
		setValueInternal(value);
	}

	/**
	 * Turn on the device (same as {@code setValue(1)}).
	 * 
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public void on() throws RuntimeIOException {
		stopLoops();
		setValueInternal(1);
	}

	/**
	 * Turn off the device (same as {@code setValue(0)}).
	 * 
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public void off() throws RuntimeIOException {
		stopLoops();
		setValueInternal(0);
	}

	/**
	 * Toggle the state of the device (same as {@code setValue(1 - getvalue())}
	 * ).
	 * 
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public void toggle() throws RuntimeIOException {
		stopLoops();
		setValueInternal(1 - device.getValue());
	}

	/**
	 * Get the device on / off status.
	 * 
	 * @return Returns true if the device currently has a value &gt; 0.
	 * @throws RuntimeIOException
	 *             If an I/O error occurred.
	 */
	public boolean isOn() throws RuntimeIOException {
		return device.getValue() > 0;
	}
}
