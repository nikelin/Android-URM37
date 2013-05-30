package com.redshape.drivers.urm37;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *  Copyright 2013, Cyril A. Karpenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
public class URM37 implements Sensor {
    private static final Logger log = Logger.getLogger(URM37.class.getCanonicalName());

    private long timeRequested;

    public static final byte MODE_SERIAL = 0x00;
    public static final byte MODE_AUTO = 0x01;
    public static final byte MODE_PWM = 0x02;

    public static final int COMMAND_TEMP = 0x11;
    public static final int COMMAND_DISTANCE = 0x22;
    public static final int COMMAND_EEPROMREAD = 0x33;
    public static final int COMMAND_EEPROMWRITE = 0x44;

    public static final int RESP_HEADER = 0;
    public static final int RESP_HIGHBYTE = 1;
    public static final int RESP_LOWBYTE = 2;
    public static final int RESP_SUM = 3;

    private static final int CHUNK_SIZE = 4;

    private UsbSerialDriver driver;

    private volatile boolean started;

    private boolean temperatureRequested;
    private boolean distanceRequested;

    private UsbManager usbManager;

    private int timeout = 200;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void setup( Context context ) throws IOException {
        this.usbManager =  (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.driver = UsbSerialProber.findFirstDevice(usbManager);
    }

    public UsbDevice getDevice() {
        return this.driver.getDevice();
    }

    @Override
    public boolean isStopped() {
        return !this.started;
    }

    @Override
    public synchronized void start() throws IOException {
        driver.open(this.usbManager);
        driver.setParameters(9600, 8, 1, 0);
        this.started = true;
    }

    @Override
    public void stop() throws IOException {
        driver.close();
        this.started = false;
    }

    protected boolean hasSerialData() throws IOException {
        return true;
    }

    protected byte[] readSerial() throws IOException {
        byte[] data = new byte[CHUNK_SIZE];
        driver.read(data, timeout);
        return data;
    }

    @Override
    public synchronized SensorResult getMeasurement(SensorResultType type) throws IOException {
        try {
            return requestMeasurementOrTimeout(type);
        } catch (InterruptedException e) {
            return null;
        }
    }

    protected SensorResult readMeasurement() throws IOException {
        if ( !hasSerialData() ) {
            return new SensorResult(SensorResultType.NOT_READY);
        }

        byte[] serialData = readSerial();
        SensorResult result;
        switch ( serialData[RESP_HEADER] ) {
            case COMMAND_DISTANCE:
                result = processDistance(serialData);
                break;
            case COMMAND_TEMP:
                result = processTemperature(serialData);
                break;
            default:
                result = new SensorResult(SensorResultType.ERROR);
        }

        return result;
    }

    protected boolean requestMeasurement( SensorResultType type ) throws IOException {
        timeRequested = System.currentTimeMillis();
        byte[] ttlCommand = new byte[] { 0, 0, 0 };
        switch( type ) {
            case TEMPERATURE:
                ttlCommand[RESP_HEADER] = COMMAND_TEMP;
                sendCommand(ttlCommand);
                temperatureRequested = true;
                return true;
            case DISTANCE:
                ttlCommand[RESP_HEADER] = COMMAND_DISTANCE;
                sendCommand(ttlCommand);
                distanceRequested = true;
                return true;
            default:
                return false;
        }
    }

    protected SensorResult requestMeasurementOrTimeout( SensorResultType type )
            throws IOException, InterruptedException  {
        if ( !requestMeasurement(type) ) {
            return new SensorResult(SensorResultType.NOT_READY);
        }

        while( !isRequestTimeout() && !hasSerialData() ) {
            Thread.sleep(10);
        }

        if ( isRequestTimeout() ) {
            return new SensorResult(SensorResultType.TIMEOUT);
        }

        return readMeasurement();
    }

    protected boolean isRequestTimeout() {
        if ( distanceRequested || temperatureRequested ) {
            long delta = System.currentTimeMillis() - timeRequested;
            if ( delta > timeout || delta < 0 ) {
                return true;
            }
        }

        return false;
    }

    protected boolean write( byte command, byte data ) throws IOException {
        byte[] ttlCOMMAND = new byte[] { COMMAND_EEPROMWRITE, command, data };
        sendCommand(ttlCOMMAND);

        byte[] serialData = new byte[3];
        while(hasSerialData() ) {
            serialData = readSerial();
        }

        return  ttlCOMMAND[0] == serialData[0]
                &&	ttlCOMMAND[1] == serialData[1]
                &&	ttlCOMMAND[2] == serialData[2];
    }

    protected boolean hasError(byte[] serialData) {
        return ( serialData[RESP_HIGHBYTE] & serialData[RESP_LOWBYTE] ) == 255;
    }

    protected SensorResult processDistance( byte[] serialData ) throws IOException {
        if (hasError(serialData)) {
            return new SensorResult(SensorResultType.ERROR);
        }

        if(serialData[RESP_HEADER] != COMMAND_DISTANCE) {
            return new SensorResult(SensorResultType.ERROR_HEADER);
        }

        if ( serialData[RESP_SUM] != serialData[RESP_HEADER] + serialData[RESP_HIGHBYTE] + serialData[RESP_LOWBYTE] ) {
            return new SensorResult(SensorResultType.ERROR_CHECKSUM);
        }

        return new SensorResult(SensorResultType.DISTANCE,
                Math.abs(serialData[RESP_HIGHBYTE] * 255 + serialData[RESP_LOWBYTE]) );
    }

    protected SensorResult processTemperature( byte[] serialData ) {
        if (hasError(serialData)) {
            return new SensorResult(SensorResultType.ERROR);
        }

        return new SensorResult(SensorResultType.DISTANCE,
                serialData[RESP_HIGHBYTE] * 255 + serialData[RESP_LOWBYTE] );
    }

    byte read(byte command) throws IOException {
        byte[] ttlCOMMAND = new byte[] { COMMAND_EEPROMREAD, command, 0 };
        sendCommand(ttlCOMMAND);

        while(!hasSerialData()) {
            readSerial();
        }

        return ttlCOMMAND[RESP_LOWBYTE];
    }

    boolean setSensorThresholdMin(byte val) throws IOException {
        return write( (byte)0x00, val);
    }

    boolean setSensorThresholdMax(byte val) throws IOException {
        return write( (byte)0x01, val);
    }

    byte getSensorThresholdMin() throws IOException {
        return read( (byte)0x00 );
    }

    byte getSensorThresholdMax() throws IOException {
        return read( (byte)0x01 );
    }

    void sendCommand(byte[] command) throws IOException {
        byte sum = 0;
        for ( int i = 0; i < command.length; i++ ) {
            sum += command[i];
        }

        byte[] request = Arrays.copyOf(command, CHUNK_SIZE);
        request[CHUNK_SIZE-1] = sum;

        driver.write(request, timeout);
    }

    boolean setMode(byte mode) throws IOException {
        if( mode == MODE_SERIAL || mode == MODE_AUTO ) {
            return write( (byte)0x02, mode);
        } else {
            return false;
        }
    }
}
