package com.diozero.internal.provider.sysfs;

import java.nio.ByteBuffer;

import com.diozero.api.SpiClockMode;
import com.diozero.internal.provider.spi.NativeSpiDevice;
import com.diozero.internal.spi.AbstractDevice;
import com.diozero.internal.spi.DeviceFactoryInterface;
import com.diozero.internal.spi.SpiDeviceInterface;
import com.diozero.util.RuntimeIOException;

public class SysFsSpiDevice extends AbstractDevice implements SpiDeviceInterface {
	private NativeSpiDevice device;
	
	public SysFsSpiDevice(DeviceFactoryInterface deviceFactory, String key, int controller,
			int chipSelect, int frequency, SpiClockMode spiClockMode) {
		super(key, deviceFactory);
		
		device = new NativeSpiDevice(controller, chipSelect, frequency, spiClockMode);
	}

	@Override
	protected void closeDevice() throws RuntimeIOException {
		device.close();
	}

	@Override
	public int getController() {
		return device.getController();
	}

	@Override
	public int getChipSelect() {
		return device.getChipSelect();
	}

	@Override
	public ByteBuffer writeAndRead(ByteBuffer txBuffer) throws RuntimeIOException {
		return device.transfer(txBuffer, 0);
	}
}
