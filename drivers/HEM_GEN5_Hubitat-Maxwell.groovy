/*
Custom Laundry monitor device for Aeon HEM Gen5 

  originally written by Mike Maxwell for SmartThings (HEM V1)
  
  modified by Dan Ogorchock to work with Hubitat (HEM V1)
  
  updated by Andrew Lewine to work with HEM Gen5 based on Gen5 SmartThings code by Dillon A. Miller

  2018-07-31  Andrew Lewine  Initial release

*/

metadata {
    definition (name: "Aeon HEM Gen5 Laundry DTH", namespace:    "AndrewLewine", author: "Andrew Lewine") 
    {
        capability "Configuration"
        capability "Switch"
        capability "Energy Meter"
        capability "Actuator"
        capability "Pushable Button"
        capability "Sensor"

        attribute "washerWatts", "string"
        attribute "dryerWatts", "string"
        attribute "washerState", "string"
        attribute "dryerState", "string"

        
        fingerprint deviceId: "0x5F", inClusters: "0x5E,0x86,0x72,0x32,0x56,0x60,0x70,0x59,0x85,0x7A,0x73,0x98", outClusters: " 0x5A"
    }

    preferences {
        input name: "washerRW", type: "number", title: "Washer running watts:", description: "", required: true
        input name: "dryerRW", type: "number", title: "Dryer running watts:", description: "", required: true
        input name: "debounceDelay", type: "number", title: "Debounce delay time (seconds):", description: "", required: true
    }
}

def parse(String description){
    //log.trace "Parse received ${description}"
    def result = null
    if (description.startsWith("Err 106")) {
        state.sec = 0
        result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
            descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via Hubitat, you must remove it from your network and add it again.")
    } else if (description != "updated") {
        def cmd = zwave.parse(description, [0x32: 4, 0x56: 1, 0x59: 1, 0x5A: 1, 0x60: 4, 0x70: 1, 0x72: 2, 0x73: 1, 0x82: 1, 0x85: 2, 0x86: 2, 0x8E: 3, 0xEF: 1])
        if (cmd) {
            //log.debug "creating zwave event ${cmd}"
			result = zwaveEvent(cmd)
        }
        if (result) { 
        	//log.debug "Parse returned ${result?.descriptionText}"
        	return result
        }
    }else {
    }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x32: 4, 0x56: 1, 0x59: 1, 0x5A: 1, 0x60: 4, 0x70: 1, 0x72: 2, 0x73: 1, 0x82: 1, 0x85: 2, 0x86: 2, 0x8E: 3, 0xEF: 1])
    state.sec = 1
    //log.debug "encapsulated: ${encapsulatedCommand}"
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract encapsulated cmd from $cmd"
        createEvent(descriptionText: cmd.toString())
    }
}


def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
	response(configure())
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} groupingIdentifier: ${cmd.groupingIdentifier}, maxNodesSupported: ${cmd.maxNodesSupported}, nodeId: ${cmd.nodeId}, reportsToFollow: ${cmd.reportsToFollow}"
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    //log.info "mc3v cmd: ${cmd}"
    if (cmd.commandClass == 50) {
        //log.info "command class 50"
        def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 1, 0x31: 1])
        if (encapsulatedCommand) {
            //log.info "encapuslated command ${encapsulatedCommand}"
            def scale = encapsulatedCommand.scale
            def byteValue = encapsulatedCommand.meterValue
            def source = cmd.sourceEndPoint
            def str = ""
            def name = ""
            def value = ((byteValue[0]*16777216) + (byteValue[1]*65536) + (byteValue[2]*256) + byteValue[3])/1000
            //log.debug "byte array ${byteValue} parsed value ${value}"
            if (scale == 2 ){ //watts
                str = "watts"
                if (source == 1){
                    name = "washerWatts"
                    if (value >= settings.washerRW.toInteger()){
                        if (state.washerIsRunning == false){
                            log.debug "unschedule(sendWasherDone) called"
                            unschedule(sendWasherDone)
                        }
                        state.washerIsRunning = true                       
                        //washer is on
                        sendEvent(name: "washerState", value: "on", displayed: true)
                        
                    } else {
                        //washer is off
                        if (state.washerIsRunning == true){
                            log.debug "runIn(${debounceDelay.toInteger()}, sendWasherDone) called"
                            runIn(debounceDelay.toInteger(), sendWasherDone)
                        }
                        state.washerIsRunning = false
                    }
                } else {
                    name = "dryerWatts"
                    if (value >= settings.dryerRW.toInteger()){
                        if (state.dryerIsRunning == false){
                            log.debug "unschedule(sendDryerDone) called"
                            unschedule(sendDryerDone)
                        }
                        state.dryerIsRunning = true
                        //dryer is on
                        sendEvent(name: "dryerState", value: "on", displayed: true)

                    } else {
                        //dryer is off
                        if (state.dryerIsRunning == true){
                            log.debug "runIn(${debounceDelay.toInteger()}, sendDryerDone) called"
                            runIn(debounceDelay.toInteger(), sendDryerDone)
                        }
                        state.dryerIsRunning = false
                    }
                }
                if (state.washerIsRunning || state.dryerIsRunning){
                    sendEvent(name: "switch", value: "on", descriptionText: "Laundry has started...", displayed: true)
                } else {
                    sendEvent(name: "switch", value: "off", displayed: false)
                }
                //log.debug "mc3v- name: ${name}, value: ${value}, unit: ${str}"
                return [name: name, value: value.toInteger(), unit: str, displayed: false]
            } else {
                log.debug "unhandled config class 50 command: ${encapsulatedCommand}"
            }
        }
    }
}

def sendWasherDone(){
    log.debug "sendWasherDone() called"
    sendEvent(name: "washerState", value: "off", displayed: true)
    //button event
    sendEvent(name: "pushed", value: "1", descriptionText: "Washer has finished.", isStateChange: true)
}

def sendDryerDone(){
    log.debug "sendDryerDone() called"
    sendEvent(name: "dryerState", value: "off", displayed: true)  
    //button event
    sendEvent(name: "pushed", value: "2", descriptionText: "Dryer has finished.", isStateChange: true)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    log.debug "Unhandled event ${cmd}"
    [:]
}

def configure() {
    log.debug "configure()"
    initialize()
    def cmd = delayBetween([
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 0)).format(),            // Disable (=0) selective reporting
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 768)).format(),       //13056
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 30)).format(),         // Every 15 seconds
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 0)).format(),       
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 7200)).format(),         // Every 15 seconds
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0)).format(),       
        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 7200)).format(),         // Every 15 seconds

        zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 3)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 101)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 102)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 103)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 111)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 112)).format(),
		zwave.securityV1.securityMessageEncapsulation().encapsulate(zwave.configurationV1.configurationGet(parameterNumber: 113)).format()
    ],500)
    return cmd
}

def installed() {
    configure()
}

def updated() {
    configure()
}

def initialize() {
    sendEvent(name: "numberOfButtons", value: 2)
    state.sec = 0
    state.washerIsRunning = false
    state.dryerIsRunning = false
}

def push(btnNumber) {
    //log.debug btnNumber
    def desc = bthNumber==1?"Washer has finished":"Dryer has finished"
    sendEvent(name: "pushed", value: btnNumber, descriptionText: desc, isStateChange: true)
}