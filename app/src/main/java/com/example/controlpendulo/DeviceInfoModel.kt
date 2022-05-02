package com.example.controlpendulo

class DeviceInfoModel {
    var deviceName: String? = null
        private set
    var deviceHardwareAddress: String? = null
        private set

    constructor() {}
    constructor(deviceName: String?, deviceHardwareAddress: String?) {
        this.deviceName = deviceName
        this.deviceHardwareAddress = deviceHardwareAddress
    }
}