package com.diozero.internal.provider.sysfs;

/*
 * #%L
 * Device I/O Zero - Java Sysfs provider
 * %%
 * Copyright (C) 2016 mattjlewis
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


import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import com.diozero.internal.spi.AbstractDevice;
import com.diozero.internal.spi.DeviceFactoryInterface;
import com.diozero.internal.spi.PwmOutputDeviceInterface;
import com.diozero.util.RuntimeIOException;

/**
 * <p><a href="http://odroid.com/dokuwiki/doku.php?id=en:c2_hardware_pwm">Setting up</a>:
 * 1 PWM Channel (GPIO 234; Pin 33):</p>
 * <pre>{@code
 *sudo modprobe pwm-meson
 *sudo modprobe pwm-ctrl
 *}</pre>
 * <p>2 PWM Channels (GPIO 234 &amp; 235; Pins 33 / 19):</p>
 * <pre>{@code
 *sudo modprobe pwm-meson npwm=2
 *sudo modprobe pwm-ctrl
 *}</pre>
 */
public class OdroidC2SysFsPwmOutputDevice extends AbstractDevice implements PwmOutputDeviceInterface {
	private static Path PWM_ROOT = FileSystems.getDefault().getPath("/sys/devices/platform/pwm-ctrl");
	
	private int gpio;
	private int range;
	private int pwmNum;
	private RandomAccessFile dutyFile;

	public OdroidC2SysFsPwmOutputDevice(String key, DeviceFactoryInterface deviceFactory, int gpio,
			int frequency, float initialValue) {
		super(key, deviceFactory);
		
		this.gpio = gpio;
		this.range = 1023;
		pwmNum = gpio - 234;

		try {
			dutyFile = new RandomAccessFile(PWM_ROOT.resolve("duty" + pwmNum).toFile(), "rw");
		} catch (IOException e) {
			throw new RuntimeIOException("Error opening duty file for gpio " + gpio, e);
		}
		
		setEnabled(pwmNum, true);
		setFrequency(pwmNum, frequency * 1000);

		setValue(initialValue);
	}

	@Override
	public void closeDevice() {
		try {
			dutyFile.close();
		} catch (IOException e) {
			// Ignore
		}
		setEnabled(pwmNum, false);
	}

	@Override
	public int getGpio() {
		return gpio;
	}

	@Override
	public int getPwmNum() {
		return pwmNum;
	}
	
	@Override
	public float getValue() throws RuntimeIOException {
		try {
			dutyFile.seek(0);
			return Integer.parseInt(dutyFile.readLine()) / range;
		} catch (IOException e) {
			closeDevice();
			throw new RuntimeIOException("Error setting duty for PWM #" + pwmNum, e);
		}
	}
	
	@Override
	public void setValue(float value) throws RuntimeIOException {
		if (value < 0 || value > 1) {
			throw new IllegalArgumentException("Invalid value, must be 0..1");
		}
		
		try {
			dutyFile.seek(0);
			dutyFile.writeBytes(Integer.toString(((int) (value * range))));
		} catch (IOException e) {
			closeDevice();
			throw new RuntimeIOException("Error setting duty for PWM #" + pwmNum, e);
		}
	}

	private static void setEnabled(int pwmNum, boolean enabled) {
		File f = PWM_ROOT.resolve("enable" + pwmNum).toFile();
		try (FileWriter writer = new FileWriter(f)) {
			writer.write(enabled ? "1" : "0");
		} catch (IOException e) {
			throw new RuntimeIOException("Error enabling PWM on #" + pwmNum, e);
		}
	}

	static void setFrequency(int pwmNum, int frequency) {
		File f = PWM_ROOT.resolve("freq" + pwmNum).toFile();
		try (FileWriter writer = new FileWriter(f)) {
			writer.write(Integer.toString(frequency));
		} catch (IOException e) {
			throw new RuntimeIOException("Error setting frequency (" + frequency + ") for PWM #" + pwmNum, e);
		}
	}

	static int getFrequency(int pwmNum) {
		File f = PWM_ROOT.resolve("freq" + pwmNum).toFile();
		try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
			return Integer.parseInt(reader.readLine());
		} catch (IOException | NumberFormatException e) {
			throw new RuntimeIOException("Error getting frequency for PWM #" + pwmNum, e);
		}
	}
}
