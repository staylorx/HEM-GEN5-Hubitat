/*
 *  Aeon HEM Gen5 Aeotec Model ZW095-A
 *  https://products.z-wavealliance.org/products/1289
 *
 *  Copyright 2020 Ben Rimmasch
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  
 *  Change Log:
 *  2020-02-29: Initial
 *  2020-04-12: Added energy duration to match the OOB drivers
 *  2020-04-22: Added preference and handled CRC16 encapsulation
 *              Changed precision from 2 decimal places to read precision from the command for meter reports
 *              Added preference and added descriptive logging to meter reports
 *
 */


import groovy.transform.Field

// metadata
metadata {
  definition(name: "Aeon HEM Gen5", namespace: "hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/hubitat_codahq/master/devicestypes/aeotec-hem-gen5.groovy") {
    capability "Energy Meter"
    capability "Power Meter"
    capability "Configuration"
    capability "Sensor"
    capability "Refresh"

    command "reset"

    attribute "current", "number"
    attribute "voltage", "number"
    attribute "cost", "number"
    attribute "energyDuration", "text"

    //comment the ones out that you don't want to persist
    attribute "power1", "number"
    attribute "power2", "number"
    attribute "current1", "number"
    attribute "current2", "number"
    attribute "voltage1", "number"
    attribute "voltage2", "number"
    attribute "cost1", "number"
    attribute "cost2", "number"

    fingerprint deviceId: "0x005F", inClusters: "0x5E,0x86,0x72,0x32,0x56,0x60,0x70,0x59,0x85,0x7A,0x73,0x98"
  }

  preferences {
    section("Aeon HEM") {
      input "kWhCost", "string", title: "Cost in \$ / kWh", defaultValue: "0.144508" as String,
        required: false, displayDuringSetup: true
      input name: "enableCrc16Encapsulation", type: "bool", title: "${paramDescriptions[13]}", defaultValue: false
      input name: "enableSelectiveReports", type: "bool", title: "${paramDescriptions[3]}", defaultValue: false
      input name: "wholeHemWattTrigger", type: "number", range: 0..60000, title: "${paramDescriptions[4]}", defaultValue: 10
      input name: "clamp1WattTrigger", type: "number", range: 0..60000, title: "${paramDescriptions[5]}", defaultValue: 50
      input name: "clamp2WattTrigger", type: "number", range: 0..60000, title: "${paramDescriptions[6]}", defaultValue: 50
      input name: "wholeHemPercTrigger", type: "number", range: 0..100, title: "${paramDescriptions[8]}", defaultValue: 5
      input name: "clamp1PercTrigger", type: "number", range: 0..100, title: "${paramDescriptions[9]}", defaultValue: 10
      input name: "clamp2PercTrigger", type: "number", range: 0..100, title: "${paramDescriptions[10]}", defaultValue: 10
      input name: "group1Reports", type: "enum", title: "${paramDescriptions[101]}", multiple: true, required: true, options: reportDescriptions, defaultValue: [1, 2, 4, 8]
      input name: "group2Reports", type: "enum", title: "${paramDescriptions[102]}", multiple: true, required: true, options: reportDescriptions, defaultValue: [256, 512, 2048, 4096]
      input name: "group3Reports", type: "enum", title: "${paramDescriptions[103]}", multiple: true, required: true, options: reportDescriptions, defaultValue: [65536, 131072, 524288, 1048576]
      input "group1Interval", "number", title: "${paramDescriptions[111]}", description: "Interval (secs) for reporting group 1 reports", defaultValue: 60,
        range: "1..4294967295", required: false, displayDuringSetup: true
      input "group2Interval", "number", title: "${paramDescriptions[112]}", description: "Interval (secs) for reporting group 2 reports", defaultValue: 60,
        range: "1..4294967295", required: false, displayDuringSetup: true
      input "group3Interval", "number", title: "${paramDescriptions[113]}", description: "Interval (secs) for reporting group 3 reports", defaultValue: 60,
        range: "1..4294967295", required: false, displayDuringSetup: true

      input name: "refreshReturnsConfig", type: "bool", title: "Refresh command requests configuration parameters", defaultValue: false
      input name: "descriptiveLoggingForMeterReports", type: "bool", title: "Enable descriptionText logging for meter reports", defaultValue: false
      input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
      input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
    }
  }
}

def updated() {
  initialize()
  configure()
}

def initialize() {
  if (!state.energyTime) {
    state.energyTime = new Date().getTime()
  }
  if (!state."Explanation About Selective Reporting") {
    state."Explanation About Selective Reporting" = "Selective reporting is a way to cut down on Z-Wave traffic by " +
      "excluding reports from sending that don't meet a certain threshold.  Selective reporting is only available for " +
      "periodic wattage reports for the HEM or the individual clamps.  What this means is that if you decide to " +
      "set report group 1 to send you HEM watts, HEM kWh, HEM volts and HEM amps every 5 seconds and if you set the " +
      "threshold of the HEM watts to 50 watts and 10% the HEM watts report will only send on the 5 second tick where " +
      "it actually exceeded the threshold.  However, the other three reports (HEM kWh, HEM volts and HEM amps) will " +
      "still send every 5 seconds."
  }
  if (!state."Explanation About Reporting Groups") {
    state."Explanation About Reporting Groups" = "There are three reporting groups.  You can choose as many data points " +
      "for each group as you want. All of the data points chosen will be reported at the interval specified for each " +
      "group. Certain combinations do not work for some reason. There is a chart in the PDF documentation that (I think) " +
      "explains the combinations that do not work but I don't understand it.\n"
  }
  if (!state."Explanation About Preferences") {
    state."Explanation About Preferences" = "Unfortunately, since Hubitat doesn't support multi-select preferences in " +
      "drivers you will have to select your reports EVERY time you save preferences.  Please bring it up with the HE " +
      "platform developers to prioritize this feature.\n" +
      "When you first install the driver each of the reporting group multi-select preferences will be single select.  " +
      "Choose any option and save and they will become multi-select on reload."
  }
}

def refresh() {
  //Get HEM totals
  def request = [
    zwave.meterV3.meterGet(scale: 0),  //kWh
    zwave.meterV3.meterGet(scale: 2),  //Wattage
    zwave.meterV3.meterGet(scale: 4),  //Volts
    zwave.meterV3.meterGet(scale: 5),  //Amps
  ]

  //add parameter query
  if (refreshReturnsConfig) {
    request += configurationGets
  }

  commands(request)
}

def reset() {
  def curr = new Date()
  def stamp = curr.format("YYYY-MM-dd", location.timeZone) + " @ " + curr.format("h:mm a", location.timeZone)
  state."Last Reset Time" = stamp
  state.energyTime = curr.getTime()
  def request = [
    zwave.meterV3.meterReset(),
    zwave.meterV3.meterGet(scale: 0),  //kWh
    zwave.meterV3.meterGet(scale: 2),  //Wattage
    zwave.meterV3.meterGet(scale: 4),  //Volts
    zwave.meterV3.meterGet(scale: 5),  //Amps
  ]
  commands(request)
}

def configure() {
  logInfo "Configuring device ${device.label}"

  def request = [
    //Reset meter parameters to defaults
    //zwave.configurationV1.configurationSet(parameterNumber: 255, size: 1, scaledConfigurationValue: 1),
    //Reset parameters 101-103 to defaults
    //zwave.configurationV1.configurationSet(parameterNumber: 100, size: 1, scaledConfigurationValue: 1),
    //Reset parameters 111-113 to defaults
    //zwave.configurationV1.configurationSet(parameterNumber: 110, size: 1, scaledConfigurationValue: 1),

    // Associate so that reports work
    zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId: []),
    zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId),

    //Selective reports. Set to 0 to reduce network traffic. If disabled reports by from parameters 4-11 are disabled
    zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: enableSelectiveReports ? 1 : 0),

    //Trigger HEM watts with change by this value (default 10)
    zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: wholeHemWattTrigger),
    //Trigger clamp 1 watts with change by this value (default 50)
    zwave.configurationV1.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: clamp1WattTrigger),
    //Trigger clamp 2 watts with change by this value (default 50)
    zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: clamp2WattTrigger),
    //Trigger HEM watts with change by this percent (default 5%)
    zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: wholeHemPercTrigger),
    //Trigger clamp 1 watts with change by this percent (default 10%)
    zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: clamp1PercTrigger),
    //Trigger clamp 2 watts with change by this percent (default 10%)
    zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: clamp2PercTrigger),

    //Set Crc16 Encapsulation
    zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: enableCrc16Encapsulation ? 1 : 0),

    // Which reports need to send in Report group 1.
    zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: group1Reports.collect {
      it.toInteger()
    }.sum()),
    // Which reports need to send in Report group 2.
    zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: group2Reports.collect {
      it.toInteger()
    }.sum()),
    // Which reports need to send in Report group 3.
    zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: group3Reports.collect {
      it.toInteger()
    }.sum()),
    // Interval to send Report group 1.
    zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: group1Interval.toInteger()),
    // Interval to send Report group 2.
    zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: group2Interval.toInteger()),
    // Interval to send Report group 3.
    zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: group3Interval.toInteger())

  ]

  // Report which configuration commands were sent to and received by the HEM Gen5 successfully.
  request += configurationGets
  commands(request)
}

def parse(String description) {
  logDebug "parse(description)"
  logTrace "description: $description"

  def result
  def cmd = zwave.parse(description, commandClasses)
  if (cmd) {
    result = zwaveEvent(cmd)
  }
  else {
    log.warn "Command not parsed! ${description}"
  }
  return result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd)"
  logTrace "cmd: $cmd"

  state.secure = 1

  def encapCmd = cmd.encapsulatedCommand(commandClasses)
  logTrace "encap cmd: ${encapCmd}"
  if (encapCmd) {
    zwaveEvent(encapCmd)
  }
  else {
    log.warn "Unable to extract security message encapsulated cmd from $cmd"
    createEvent(descriptionText: cmd.toString())
  }
}

//def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
//  logDebug "zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd)"
//  logTrace "cmd: $cmd"
//}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd)"
  logTrace "cmd: $cmd"
  def value = cmd.scaledConfigurationValue?.toInteger()
  logTrace "value: $value"

  switch (cmd.parameterNumber) {
    case 2:
      sendConfigEvent(cmd.parameterNumber, detectionModeDescriptions[value])
      break
    case 3:
    case 13:
    case 252:
      sendConfigEvent(cmd.parameterNumber, value ? "enabled" : "disabled")
      break
    case 4:
    case 5:
    case 6:
      sendConfigEvent(cmd.parameterNumber, "Trigger change by ${value} watts")
      break
    case 8:
    case 9:
    case 10:
      sendConfigEvent(cmd.parameterNumber, "Trigger change by ${value}%")
      break
    case 101:
    case 102:
    case 103:
      sendConfigEvent(cmd.parameterNumber, getReportsFromInt(value))
      break
    case 111:
    case 112:
    case 113:
      sendConfigEvent(cmd.parameterNumber, "Trigger every ${value} seconds")
      break
    case 200:
    case 254:
      sendConfigEvent(cmd.parameterNumber, "${value}")
      break
    default:
      log.warn "Unhandled parameter ${cmd.parameterNumber} with value ${value}"
      break
  }
}

private sendConfigEvent(short param, String desc) {
  logInfo "Parameter ${param} \"${paramDescriptions[param as int]}\" has value:: ${desc}"
  sendEvent([name: paramDescriptions[param as int], value: desc])
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd)"
  logTrace "cmd: $cmd"

  logInfo "Assocation Group ${cmd.groupingIdentifier} has nodes :: ${cmd.nodeId}"
  sendEvent([name: "Group 1 Assocations", value: cmd.nodeId])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd)"
  logTrace "cmd: $cmd"

  def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  updateDataValue("firmware", firmware)
  logDebug "${device.displayName} is running firmware version: $firmware, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)"
  logTrace "cmd: $cmd"

  logDebug "manufacturerId:   ${cmd.manufacturerId}"
  logDebug "manufacturerName: ${cmd.manufacturerName}"
  logDebug "productId:        ${cmd.productId}"
  logDebug "productTypeId:    ${cmd.productTypeId}"
  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerId.toString())
  updateDataValue("manufacturerName", cmd.manufacturerName)
  sendEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, int clamp = 0) {
  logDebug "zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd)"
  logTrace "cmd: $cmd"

  def sourceName = clamp == 0 ? "HEM" : "Clamp ${clamp}"
  def source = clamp == 0 ? "" : "${clamp}"

  def value = String.format("%5.${cmd.precision}f", cmd.scaledMeterValue)
  switch (cmd.scale) {
    case 1: //kVAh
    case 3: //pulses
    case 4: //volts
    case 6: //power factor
    case 7: //scale2 values
      break
    case 0: //kWh
      def cost = String.format("%5.2f", cmd.scaledMeterValue * (kWhCost as BigDecimal))
      def msg = "${sourceName} cost is \$${cost}"
      logInfo msg
      sendEvent(name: "cost${source}", value: cost, unit: "", descriptionText: msg)
      def dur_ms = new Date().getTime() - state.energyTime
      def days = (dur_ms / (1000.0 * 60.0 * 60.0 * 24.0))
      sendEvent(name: "energyDuration", value: "${formatDays(days)} Days")
      break
    case 2: //WATTS
      if (cmd.scaledMeterValue > MAX_WATTS) {
        log.warn "Watts ${cmd.scaledMeterValue} too high for ${sourceName}"
        return
      }
      break
    case 5: //Amps
      if (cmd.scaledMeterValue > MAX_AMPS) {
        log.warn "Amps ${cmd.scaledMeterValue} too high for ${sourceName}"
        return
      }
      break
    default:
      log.warn "scale ${cmd.scale} not handled!"
      break
  }

  def msg = "${sourceName} ${unitNames[cmd.scale]} is ${value} ${unitAbbrs[cmd.scale]}"
  if (descriptiveLoggingForMeterReports) {
    logInfo msg
  }
  sendEvent(name: "${unitNames[cmd.scale]}${source}", value: cmd.scaledMeterValue, unit: "", descriptionText: msg)
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd)"
  logTrace "cmd: $cmd"

  if (cmd.commandClass == 50) {
    //extract encapsulated command
    def encapCmd = cmd.encapsulatedCommand([0x30: 1, 0x31: 1])
    logTrace "Command from clamp ${cmd.sourceEndPoint}: ${encapCmd}"
    if (encapCmd) {
      zwaveEvent(encapCmd, cmd.sourceEndPoint)
    }
    else {
      log.warn "Couldn't extract encapsulated command!"
    }
  }
  else {
    log.warn "cmd.commandClass: ${cmd.commandClass}"
  }
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  logDebug "zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd)"
  logTrace "cmd: $cmd"

  def version = commandClasses[cmd.commandClass as int] ?: 1

  def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, version)
  if (!encapsulatedCommand) {
    log.warn "zwaveEvent(): Could not extract command from ${cmd}"
  }
  else {
    logDebug("zwaveEvent(): Extracted command ${encapsulatedCommand}")
    return zwaveEvent(encapsulatedCommand)
  }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "Unhandled: $cmd"
  createEvent(descriptionText: cmd.toString(), isStateChange: false)
}

def getConfigurationGets() {
  return [
    zwave.configurationV1.configurationGet(parameterNumber: 2),   //Energy detection mode configuration for parameters 101~103
    zwave.configurationV1.configurationGet(parameterNumber: 3),   //enable/disable parameter selective reporting parameters 4~10
    zwave.configurationV1.configurationGet(parameterNumber: 4),   //Induce an automatic report of HEM by watts
    zwave.configurationV1.configurationGet(parameterNumber: 5),   //Induce an automatic report of Channel 1 by watts
    zwave.configurationV1.configurationGet(parameterNumber: 6),   //Induce an automatic report of Channel 2 by watts
    zwave.configurationV1.configurationGet(parameterNumber: 8),   //Induce an automatic report of HEM by percent
    zwave.configurationV1.configurationGet(parameterNumber: 9),   //Induce an automatic report of Channel 1 by percent
    zwave.configurationV1.configurationGet(parameterNumber: 10),  //Induce an automatic report of Channel 2 by percent
    zwave.configurationV1.configurationGet(parameterNumber: 13),  //Enable/disable CRC-16 Encapsulation
    zwave.configurationV1.configurationGet(parameterNumber: 101), //Report group 1 reports
    zwave.configurationV1.configurationGet(parameterNumber: 102), //Report group 2 reports
    zwave.configurationV1.configurationGet(parameterNumber: 103), //Report group 3 reports
    zwave.configurationV1.configurationGet(parameterNumber: 111), //Report group 1 frequency
    zwave.configurationV1.configurationGet(parameterNumber: 112), //Report group 2 frequency
    zwave.configurationV1.configurationGet(parameterNumber: 113), //Report group 3 frequency
    zwave.configurationV1.configurationGet(parameterNumber: 200), //Is Aeon or Third-party
    zwave.configurationV1.configurationGet(parameterNumber: 252), //Configuration locked?
    zwave.configurationV1.configurationGet(parameterNumber: 254), //Device Tag
    zwave.associationV2.associationGet(groupingIdentifier: 1),
    zwave.versionV1.versionGet(),
    zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  ]
}

private getCommandClasses() {
  [
    0x32: 3,
    0x56: 1,
    0x59: 1,
    0x5A: 1,
    //0x5E: 2,
    0x60: 3,
    0x70: 4, //even though firmware v1.31 on hubitat reports 1
    0x72: 2,
    0x73: 1,
    //0x7A: 2,
    0x82: 1,
    0x85: 2,
    0x86: 1, //even though firmware v1.31 on hubitat reports v2
    0x8E: 2,
    //0x98: 1,
    0xEF: 1
  ]
}

@Field def detectionModeDescriptions = [
  0: "(Default) Report wattage and the absolute KWH value",
  1: "Report positive/negative wattage and the algebraic sum KWH value",
  2: "Report positive/negative wattage and the positive KWH value (consuming electricity)",
  3: "Report positive/negative wattage and the negative KWH value (generating electricity)"
]

@Field def paramDescriptions = [
  2: "Group Reporting Energy Detection Mode ", //parameters 101~103
  3: "Selective Reporting", //parameters 4~11
  4: "Whole HEM Watts Change",
  5: "Clamp 1 Watts Change",
  6: "Clamp 2 Watts Change",
  7: "Clamp 3 Watts Change",
  8: "Whole HEM Watts Percentage Change",
  9: "Clamp 1 Watts Percentage Change",
  10: "Clamp 2 Watts Percentage Change",
  11: "Clamp 3 Watts Percentage Change",
  13: "CRC-16 Encapsulation",
  101: "Reporting Group 1",
  102: "Reporting Group 2",
  103: "Reporting Group 3",
  111: "Reporting Group 1 Frequency",
  112: "Reporting Group 2 Frequency",
  113: "Reporting Group 3 Frequency",
  200: "Partner ID",
  252: "Configuration Locked?",
  254: "Device Tag"
]

@Field def reportDescriptions = [
  1: "KWH HEM",
  2: "Watts HEM",
  4: "Voltage HEM",
  8: "Current HEM",
  16: "kVarh HEM",
  32: "kVar HEM",
  256: "Watts Clamp 1",
  512: "Watts Clamp 2",
  //1024: "Watts Clamp 3",
  2048: "KWH Clamp 1",
  4096: "KWH Clamp 2",
  //8192: "KWH Clamp 3",
  65536: "Voltage Clamp 1",
  131072: "Voltage Clamp 2",
  //262144: "Voltage Clamp 3",
  524288: "Current (Amperes) Clamp 1",
  1048576: "Current (Amperes) Clamp 2",
  //2097152: "Current (Amperes) Clamp 3"
]

@Field def unitNames = ["energy", "energy", "power", "count", "voltage", "current", "powerFactor", "unknown"]
@Field def unitAbbrs = ["kWh", "kVAh", "W", "pulses", "V", "A", "Power Factor", ""]
@Field def MAX_AMPS = 220
@Field def MAX_WATTS = 24000

private getReportsFromInt(int value) {
  def result = []
  while (value > 0) {
    reportDescriptions.reverseEach {
      if (value >= it.key) {
        value = value - it.key
        result << it.value
      }
    }
  }
  return "${result.reverse()}"
}

private command(hubitat.zwave.Command cmd) {
  if (state.secure) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  }
  else {
    cmd.format()
  }
}

private commands(commands, delay = 500) {
  delayBetween(commands.collect { command(it) }, delay)
}

private String formatDays(BigDecimal duration) {
  java.text.DecimalFormat df = new java.text.DecimalFormat();
  df.setMaximumFractionDigits(2);
  df.setMinimumFractionDigits(0);
  df.setGroupingUsed(false);
  return df.format(duration);
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}
