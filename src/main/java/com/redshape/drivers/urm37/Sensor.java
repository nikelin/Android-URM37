package com.redshape.drivers.urm37;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import java.io.IOException;

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
public interface Sensor {

    public SensorResult getMeasurement(SensorResultType type) throws IOException;

    public void setup(Context driver) throws IOException;

    public void start() throws IOException;

    public void stop() throws IOException;

    public void setTimeout( int value );

    public boolean isStopped();

    public UsbDevice getDevice();

    public long getTimeout();
}
