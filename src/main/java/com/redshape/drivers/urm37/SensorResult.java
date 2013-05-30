package com.redshape.drivers.urm37;

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
public class SensorResult {
    private final SensorResultType type;
    private final int value;

    public SensorResult(SensorResultType type) {
        this(type, -1);
    }

    public SensorResult(SensorResultType type, int value) {
        this.type = type;
        this.value = value;
    }

    public SensorResultType getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public boolean isError() {
        return this.value < 0;
    }
}
